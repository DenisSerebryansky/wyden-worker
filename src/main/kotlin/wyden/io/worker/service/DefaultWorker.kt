package wyden.io.worker.service

import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.Message
import org.springframework.stereotype.Service
import wyden.io.worker.config.AmqpConfig.Companion.EXCHANGE_OUTBOUND
import wyden.io.worker.config.AmqpConfig.Companion.QUEUE_INBOUND
import wyden.io.worker.config.AmqpConfig.Companion.ROUTING_KEY_PREFIX_DISCARDED
import wyden.io.worker.config.AmqpConfig.Companion.ROUTING_KEY_PREFIX_OUTBOUND
import wyden.io.worker.property.WorkerProperties
import wyden.io.worker.util.LoggerDelegate
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

@Service
class DefaultWorker(
    private val workerProperties: WorkerProperties,
    private val rabbitTemplate: RabbitTemplate,
    private val clock: Clock,
) : Worker {

    private var previousTask: String? = null // used only for simulation of every second task failure

    private val uniqueTasksCount = AtomicLong(0)

    @RabbitListener(queues = [QUEUE_INBOUND])
    override fun handle(message: Message<String>) {
        val task = message.payload
        val isRetry = task == previousTask
        val currentTaskNumber = if (isRetry) uniqueTasksCount.get() else uniqueTasksCount.incrementAndGet()

        val headers = message.headers
        val expiration = Instant.ofEpochMilli(headers[HEADER_EXPIRES_AT] as Long)
        val color = extractColor(message)

        if (expiration.isPassed()) {
            log.warn("Received expired task: {} (#{}), discarding", task, currentTaskNumber)
            val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_DISCARDED, color)
            return send(routingKey, "$task-discarded", expiration)
        }

        log.info("Received task: {} (#{}), processing", task, currentTaskNumber)
        process(task, currentTaskNumber, isRetry)

        if (expiration.isPassed()) {
            val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_DISCARDED, color)
            log.warn(
                "Processed task is already expired: {} (#{}), discarding with routing key {}",
                task, currentTaskNumber, routingKey
            )
            send(routingKey, "$task-discarded", expiration)
        } else {
            val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_OUTBOUND, color)
            log.info("Task {} (#{}) has been processed, sending to routing key {}", task, currentTaskNumber, routingKey)
            send(routingKey, "$task-processed", expiration)
        }
    }

    private fun process(task: String, currentTaskNumber: Long, isRetry: Boolean) {
        previousTask = task // mark task before failure so retry is recognized
        if (currentTaskNumber % 2 == 0L && !isRetry && workerProperties.failEverySecondTask) {
            log.error("Got an error while processing task: {}", task)
            throw IllegalArgumentException("Failed task $task!")
        } else {
            // some work simulation
            Thread.sleep(workerProperties.handlingMs)
        }
    }

    private fun Instant.isPassed(): Boolean = clock.instant().isAfter(this)

    private fun getRoutingKey(prefix: String, color: String?) = "$prefix.${color ?: "any"}"

    private fun extractColor(message: Message<String>): String? {
        val routingKey = message.headers[AmqpHeaders.RECEIVED_ROUTING_KEY] as? String
        return routingKey?.substringAfterLast(".")
    }

    private fun send(routingKey: String, message: Any, expiresAt: Instant) {
        rabbitTemplate.convertAndSend(EXCHANGE_OUTBOUND, routingKey, message) { message ->
            message.messageProperties.headers[HEADER_EXPIRES_AT] = expiresAt.toEpochMilli().toString()
            message
        }
    }

    companion object {
        private val log by LoggerDelegate()
        private const val HEADER_EXPIRES_AT = "expiresAt"
    }
}
