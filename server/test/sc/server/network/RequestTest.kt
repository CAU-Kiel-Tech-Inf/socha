package sc.server.network

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sc.framework.plugins.AbstractGame
import sc.framework.plugins.protocol.MoveRequest
import sc.networking.clients.LobbyClient
import sc.protocol.requests.*
import sc.server.Configuration
import sc.server.client.PlayerListener
import sc.server.client.TestLobbyClientListener
import sc.server.client.TestPreparedGameResponseListener
import sc.server.gaming.GameRoom
import sc.server.gaming.ObserverRole
import sc.server.helpers.TestHelper
import sc.server.plugins.TestMove
import sc.server.plugins.TestPlugin
import sc.shared.WelcomeMessage
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds
import kotlin.time.seconds

@ExperimentalTime
class RequestTest: RealServerTest() {
    private lateinit var player1: LobbyClient
    private lateinit var player2: LobbyClient
    private lateinit var player3: LobbyClient
    
    @BeforeEach
    fun prepare() {
        player1 = connectClient("localhost", serverPort)
        Thread.sleep(200)
        player2 = connectClient("localhost", serverPort)
        Thread.sleep(200)
        player3 = connectClient("localhost", serverPort)
        Thread.sleep(200)
    }
    
    @Test
    fun authenticationRequest() {
        player1.authenticate(PASSWORD)
        Thread.sleep(200)
        val clients = lobby.clientManager.clients
        assertTrue(clients[0].isAdministrator)
        assertEquals(3, lobby.clientManager.clients.size.toLong())
        
        player2.authenticate("PASSWORD_FAIL_TEST")
        Thread.sleep(200)
        
        // Player2 got kicked
        assertEquals(2, lobby.clientManager.clients.size.toLong())
        assertFalse(clients[1].isAdministrator)
    }
    
    @Test
    fun prepareRoomRequest() {
        player1.authenticate(PASSWORD)
        player1.prepareGame(TestPlugin.TEST_PLUGIN_UUID, true)
        val listener = TestPreparedGameResponseListener()
        player1.addListener(listener)
        
        Thread.sleep(200)
        assertNotNull(listener.response)
        
        assertEquals(1, lobby.games.size.toLong())
        assertEquals(0, lobby.games.iterator().next().clients.size.toLong())
        assertTrue(lobby.games.iterator().next().isPauseRequested)
    }
    
    @Test
    fun joinPreparedRoomRequest() {
        player1.authenticate(PASSWORD)
        val listener = TestPreparedGameResponseListener()
        player1.addListener(listener)
        
        player1.prepareGame(TestPlugin.TEST_PLUGIN_UUID)
        Thread.sleep(200)
        val response = listener.response
        
        val reservation = response.reservations[0]
        player1.joinPreparedGame(reservation)
        Thread.sleep(200)
        assertEquals(1, lobby.games.iterator().next().clients.size.toLong())
        
        player2.joinPreparedGame(response.reservations[1])
        Thread.sleep(200)
        assertEquals(2, lobby.games.iterator().next().clients.size.toLong())
        
        player3.joinPreparedGame(response.reservations[1])
        Thread.sleep(200)
        assertEquals(2, lobby.clientManager.clients.size.toLong())
    }
    
    @Test
    fun observationRequest() {
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        
        Thread.sleep(200)
        
        val gameRoom = lobby.games.iterator().next()
        player3.authenticate(PASSWORD)
        player3.observe(gameRoom.id)
        
        Thread.sleep(200)
        
        val roles = lobby.clientManager.clients[2].roles.iterator()
        var hasRole = false
        while (roles.hasNext()) {
            if (roles.next() is ObserverRole) {
                hasRole = true
            }
        }
        assertTrue(hasRole)
    }
    
    @Test
    fun stepRequestException() {
        val admin = player1
        val player1 = player2
        val player2 = player3
        val p1Listener = PlayerListener()
        val p2Listener = PlayerListener()
        
        // Make player1 Admin and prepare a game in paused mode
        admin.authenticate(PASSWORD)
        val listener = TestLobbyClientListener()
        admin.addListener(listener)
        
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        Thread.sleep(500)
        
        // Room was created
        val room = lobby.games.iterator().next()
        val sp1 = room.slots[0].role.player
        sp1.addPlayerListener(p1Listener)
        admin.send(PauseGameRequest(room.id, true))
        admin.observe(room.id, false)
        
        // Wait for admin
        TestHelper.waitUntilTrue({ listener.observedReceived }, 2000)
        
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        Thread.sleep(500)
        room.slots[1].role.player.addPlayerListener(p2Listener)
        
        // Wait for the server to register that
        TestHelper.waitUntilTrue({ room.isPauseRequested }, 2000)
        
        // Wait for it to register
        // no state will be send if game is paused TestHelper.waitUntilTrue(()->listener.newStateReceived, 2000);
        listener.newStateReceived = false
        
        p1Listener.waitForMessage(WelcomeMessage::class)
        
        player1.sendMessageToRoom(room.id, TestMove(1))
        Thread.sleep(100)
        assertEquals(room.status, GameRoom.GameStatus.OVER)
    }
    
    @Test
    fun stepRequest() {
        val admin = player1
        val player1 = player2
        val player2 = player3
        val p1Listener = PlayerListener()
        val p2Listener = PlayerListener()
        
        // Make player1 Admin and prepare a game in paused mode
        admin.authenticate(PASSWORD)
        val listener = TestLobbyClientListener()
        admin.addListener(listener)
        
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        Thread.sleep(500)
        
        // Room was created
        val room = lobby.games.iterator().next()
        room.slots[0].role.player.addPlayerListener(p1Listener)
        admin.send(PauseGameRequest(room.id, true))
        admin.observe(room.id)
        await("Game paused") { room.isPauseRequested }
        await("Admin observing") { listener.observedReceived }
        
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        await("Second player joins and game starts", 1.seconds) { room.status == GameRoom.GameStatus.ACTIVE }
        room.slots[1].role.player.addPlayerListener(p2Listener)
        
        // TODO the section above duplicates the one of the previous test, clean that up
        
        // Wait for it to register
        // no state will be send if game is paused TestHelper.waitUntilTrue(()->listener.newStateReceived, 2000);
        listener.newStateReceived = false
        
        p1Listener.waitForMessage(WelcomeMessage::class)
        
        // TODO enabling this should result in a GameLogicException
        // player1.sendMessageToRoom(room.getId(), new TestMove(1));
        // Thread.sleep(100);
        
        // Request a move from the first player
        admin.send(StepRequest(room.id))
        TestHelper.waitUntilTrue({ listener.newStateReceived }, 2000)
        p1Listener.waitForMessage(MoveRequest::class)
        // send move
        listener.newStateReceived = false;
        player1.sendMessageToRoom(room.id, TestMove(1));
        TestHelper.waitUntilTrue({ listener.newStateReceived }, 2000)
        
        admin.send(StepRequest(room.id))
        // Wait for second players turn
        p2Listener.waitForMessage(MoveRequest::class, 4.seconds)
        
        // Second player sends Move with value 42
        player2.sendMessageToRoom(room.id, TestMove(42));
        Thread.sleep(100);
        
        // Request a move
        admin.send(StepRequest(room.id))
        
        // should register as a new state
        TestHelper.waitUntilTrue({ listener.newStateReceived }, 2000)
        listener.newStateReceived = false
        // Wait for it to register
        p1Listener.waitForMessage(MoveRequest::class)
        
        p1Listener.clearMessages() shouldBe 0
        // Second player sends Move not being his turn
        player2.sendMessageToRoom(room.id, TestMove(73))
        TestHelper.waitUntilTrue({ listener.newStateReceived }, 1000)
        listener.newStateReceived = false
        
        // There should not come another request
        Thread.sleep(500)
        p1Listener.clearMessages() shouldBe 0
        // should not result in a new game state
        assertFalse(listener.newStateReceived)
        
        // Game should be deleted, because player3 sent invalid move
        assertEquals(0L, lobby.games.size.toLong())
    }
    
    @Test
    fun cancelRequest() {
        player1.authenticate(PASSWORD)
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        val listener = TestLobbyClientListener()
        player1.addListener(listener)
        
        await("Lobby creates a room") {
            lobby.games.isNotEmpty() && listener.gameJoinedReceived && listener.roomId != null
        }
        
        player1.send(CancelRequest(listener.roomId))
        await("Lobby closes the room") {
            lobby.games.isEmpty()
        }
    }
    
    @Test
    fun testModeRequest() {
        player1.authenticate(PASSWORD)
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        val listener = TestLobbyClientListener()
        player1.addListener(listener)
        
        player1.send(TestModeRequest(true))
        TestHelper.assertEqualsWithTimeout("true", { Configuration.get(Configuration.TEST_MODE) }, 1000)
        
        player1.send(TestModeRequest(false))
        TestHelper.assertEqualsWithTimeout("false", { Configuration.get(Configuration.TEST_MODE) }, 1000)
    }
    
    @Test
    fun timeoutRequest() {
        player1.authenticate(PASSWORD)
        val listener = TestLobbyClientListener()
        
        player1.addListener(listener)
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        
        TestHelper.waitUntilEqual(1, { lobby.games.size }, 2000)
        var room = gameMgr.games.iterator().next()
        assertTrue(room.slots[0].role.player.canTimeout)
        val req = ControlTimeoutRequest(room.id, false, 0)
        player1.send(req)
        Thread.sleep(2000)
        room = gameMgr.games.iterator().next()
        assertFalse(room.slots[0].role.player.canTimeout)
    }
    
    @Test
    fun pauseRequest() {
        player1.authenticate(PASSWORD)
        val listener = TestLobbyClientListener()
        val p1Listener = PlayerListener()
        val p2Listener = PlayerListener()
        
        player1.addListener(listener)
        
        player1.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        TestHelper.waitUntilEqual(1, { lobby.games.size }, 2000)
        val room = gameMgr.games.iterator().next()
        room.slots[0].role.player.addPlayerListener(p1Listener)
        player2.joinRoomRequest(TestPlugin.TEST_PLUGIN_UUID)
        TestHelper.waitUntilEqual(2, { room.slots.size }, 2000)
        Thread.sleep(500)
        val splayer2 = room.slots[1].role.player
        splayer2.addPlayerListener(p2Listener)
        splayer2.displayName = "player2..."
        
        assertFalse(room.isPauseRequested)
        p1Listener.waitForMessage(WelcomeMessage::class)
        p1Listener.waitForMessage(MoveRequest::class, 500.milliseconds)
        listener.newStateReceived = false
        
        player1.send(PauseGameRequest(room.id, true))
        TestHelper.waitUntilEqual(true, { room.isPauseRequested }, 2000)
        
        player1.sendMessageToRoom(room.id, TestMove(42))
        Thread.sleep(1000)
        // assert that (if the game is paused) no new gameState is send to the observers after a pending Request was received
        assertFalse(listener.newStateReceived)
        
        player1.send(PauseGameRequest(room.id, false))
        TestHelper.waitUntilEqual(false, { (room.game as AbstractGame<*>).isPaused }, 2000)
        p2Listener.waitForMessage(MoveRequest::class, 500.milliseconds)
    }
    
}
