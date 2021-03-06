package sc.server.client

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import sc.api.plugins.host.IPlayerListener
import sc.protocol.responses.ProtocolMessage
import sc.server.network.await
import java.util.Queue
import java.util.ArrayDeque
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

class PlayerListener : IPlayerListener {
    private val messages: Queue<ProtocolMessage> = ArrayDeque()

    override fun onPlayerEvent(request: ProtocolMessage) {
        messages.add(request)
    }
    
    /** Clears all messages.
     * @return number of cleared messages */
    fun clearMessages(): Int {
        val size = messages.size
        messages.clear()
        return size
    }
    
    @ExperimentalTime
    fun waitForMessage(messageType: KClass<out ProtocolMessage>) = runBlocking {
        await("Expected to receive ${messageType.simpleName}") {
            messages.shouldNotBeEmpty()
        }
        messages.remove()::class shouldBe messageType
    }
}