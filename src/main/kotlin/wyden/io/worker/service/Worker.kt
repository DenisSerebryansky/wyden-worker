package wyden.io.worker.service

import org.springframework.messaging.Message

interface Worker {

    fun handle(message: Message<String>)
}
