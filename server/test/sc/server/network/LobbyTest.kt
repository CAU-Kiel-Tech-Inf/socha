package sc.server.network

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import sc.server.client.TestLobbyClientListener
import sc.server.gaming.GameRoom
import sc.server.plugins.TestPlugin
import sc.shared.ScoreCause
import kotlin.time.ExperimentalTime

@ExperimentalTime
class LobbyTest: RealServerTest() {
    
    @Test
    fun shouldEndGameOnDisconnect() {
        val player1 = connectClient("localhost", serverPort)
        val player2 = connectClient("localhost", serverPort)
        
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        
        await("Game created") { lobby.games.size == 1 }
        await("Game started") { lobby.games.single().status == GameRoom.GameStatus.ACTIVE }
        
        player1.stop()
        await("GameRoom closes after one player died") { lobby.games.isEmpty() }
    }
    
    @Test
    fun shouldEndGameOnIllegalMessage() {
        val player1 = connectClient("localhost", serverPort)
        val player2 = connectClient("localhost", serverPort)
    
        val listener = TestLobbyClientListener()
        player1.addListener(listener)
        
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        await { listener.gameJoinedReceived }
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
    
        await("Game started") { lobby.games.single().status == GameRoom.GameStatus.ACTIVE }
        
        val room = gameMgr.games.single()
        room.isOver shouldBe false
        
        player1.sendCustomData("<yarr>")
        
        await("Game is over") { room.isOver }
        await("GameResult") { room.result != null }
        room.result.scores.first().cause shouldBe ScoreCause.LEFT
        
        await("GameRoom closes") { gameMgr.games.isEmpty() }
    }
    
}
