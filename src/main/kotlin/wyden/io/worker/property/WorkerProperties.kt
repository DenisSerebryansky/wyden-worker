package wyden.io.worker.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("worker")
data class WorkerProperties(
    val handlingMs: Long,
    val failEverySecondTask: Boolean
)
