package wyden.io.worker.config

import org.springframework.amqp.core.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AmqpConfig {

    @Bean
    fun workInboundExchange(): TopicExchange = TopicExchange(EXCHANGE_INBOUND)

    @Bean
    fun workInboundQueue(): Queue =
        QueueBuilder
            .durable(QUEUE_INBOUND)
            .deadLetterExchange(EXCHANGE_OUTBOUND)
            .build()

    @Bean
    fun bindings(): Declarables =
        Declarables(
            BindingBuilder
                .bind(workInboundQueue())
                .to(workInboundExchange())
                .with(ROUTING_KEY_INBOUND)
        )

    companion object {
        const val EXCHANGE_INBOUND = "work-inbound"
        const val EXCHANGE_OUTBOUND = "work-outbound"

        const val ROUTING_KEY_INBOUND = "task.produced.*"

        const val ROUTING_KEY_PREFIX_OUTBOUND = "task.processed"
        const val ROUTING_KEY_PREFIX_DISCARDED = "task.discarded"

        const val QUEUE_INBOUND = "work-inbound-worker"
    }
}
