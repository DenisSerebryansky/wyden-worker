package wyden.io.worker.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import wyden.io.worker.property.WorkerProperties
import java.time.Clock
import java.time.Instant
import kotlin.test.assertFailsWith

class DefaultWorkerTests {

    private val rabbitTemplate = mock(RabbitTemplate::class.java)

    private val clock = mock(Clock::class.java)

    private val properties = WorkerProperties(handlingMs = 0, failEverySecondTask = false)

    private val worker = DefaultWorker(properties, rabbitTemplate, clock)

    private fun message(
        payload: String,
        expiresAt: Long,
        routingKey: String
    ): Message<String> {
        val msg = mock(Message::class.java) as Message<String>
        `when`(msg.payload).thenReturn(payload)
        `when`(msg.headers).thenReturn(
            MessageHeaders(
                mapOf(
                    "expiresAt" to expiresAt,
                    AmqpHeaders.RECEIVED_ROUTING_KEY to routingKey
                )
            )
        )
        return msg
    }

    @BeforeEach
    fun setup() {
        `when`(clock.instant()).thenReturn(Instant.now())
    }

    @Test
    fun `should extract color from routing key`() {
        val msg = message(
            payload = "task1",
            expiresAt = System.currentTimeMillis() + 10000,
            routingKey = "task.produced.red"
        )

        worker.handle(msg)

        verify(rabbitTemplate).convertAndSend(
            eq("work-outbound"),
            eq("task.processed.red"),
            any(),
            any(MessagePostProcessor::class.java),
        )
    }

    @Test
    fun `should discard if expired before processing`() {
        val msg = message(
            payload = "task1",
            expiresAt = System.currentTimeMillis() - 1000,
            routingKey = "task.produced.red"
        )

        worker.handle(msg)

        verify(rabbitTemplate).convertAndSend(
            eq("work-outbound"),
            eq("task.discarded.red"),
            eq("task1-discarded"),
            any(MessagePostProcessor::class.java),
        )
    }

    @Test
    fun `should process and send to processed routing key`() {
        val msg = message(
            payload = "task1",
            expiresAt = System.currentTimeMillis() + 10_000,
            routingKey = "task.produced.blue"
        )

        worker.handle(msg)

        verify(rabbitTemplate).convertAndSend(
            eq("work-outbound"),
            eq("task.processed.blue"),
            eq("task1-processed"),
            any(MessagePostProcessor::class.java),
        )
    }

    @Test
    fun `should discard if expired after processing`() {
        val start = Instant.now()
        val afterProcessing = start.plusMillis(10)

        `when`(clock.instant())
            .thenReturn(start)
            .thenReturn(afterProcessing)

        val msg = message(
            payload = "task1",
            expiresAt = start.plusMillis(5).toEpochMilli(), // before task completion
            routingKey = "task.produced.green"
        )

        worker.handle(msg)

        verify(rabbitTemplate).convertAndSend(
            eq("work-outbound"),
            eq("task.discarded.green"),
            eq("task1-discarded"),
            any(MessagePostProcessor::class.java),
        )
    }

    @Test
    fun `should fail every second task when enabled`() {
        val now = Instant.now()
        `when`(clock.instant()).thenReturn(now)

        val failingWorker = DefaultWorker(
            WorkerProperties(handlingMs = 0, failEverySecondTask = true),
            rabbitTemplate,
            clock
        )

        val msg1 = message(
            "task1",
            System.currentTimeMillis() + 10000,
            "task.produced.red"
        )

        val msg2 = message(
            "task2",
            System.currentTimeMillis() + 10000,
            "task.produced.red"
        )

        failingWorker.handle(msg1)
        assertFailsWith<IllegalArgumentException> { failingWorker.handle(msg2) }
    }
}
