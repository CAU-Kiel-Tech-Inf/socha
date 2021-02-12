package sc.server

import org.slf4j.LoggerFactory
import sc.api.plugins.exceptions.RescuableClientException
import sc.protocol.requests.*
import sc.protocol.responses.ProtocolErrorMessage
import sc.protocol.responses.RoomPacket
import sc.protocol.responses.TestModeResponse
import sc.server.gaming.GameRoomManager
import sc.server.gaming.PlayerRole
import sc.server.gaming.ReservationManager
import sc.server.network.Client
import sc.server.network.ClientManager
import sc.server.network.IClientListener
import sc.server.network.PacketCallback
import sc.shared.InvalidGameStateException
import java.io.Closeable
import java.io.IOException

/** The lobby joins clients into a game by finding open rooms or creating new ones. */
open class Lobby: GameRoomManager(), IClientListener, Closeable {
    private val logger = LoggerFactory.getLogger(Lobby::class.java)
    
    val clientManager = ClientManager().also {
        it.setOnClientConnected(this::onClientConnected)
    }
    
    /** @see ClientManager.start */
    @Throws(IOException::class)
    fun start() {
        clientManager.start()
    }
    
    /**
     * Add lobby as listener to client.
     * Prepare client for send and receive.
     *
     * @param client connected XStreamClient
     */
    fun onClientConnected(client: Client) {
        client.addClientListener(this)
        client.start()
    }
    
    override fun onClientDisconnected(source: Client) {
        logger.info("{} disconnected.", source)
        source.removeClientListener(this)
    }
    
    /** Handle requests or moves of clients. */
    @Throws(RescuableClientException::class, InvalidGameStateException::class)
    override fun onRequest(source: Client, callback: PacketCallback) {
        val packet = callback.packet
        if (packet is ILobbyRequest) {
            when (packet) {
                is JoinPreparedRoomRequest -> ReservationManager.redeemReservationCode(source, packet.reservationCode)
                is JoinRoomRequest -> {
                    val gameRoomMessage = this.joinOrCreateGame(source, packet.gameType)
                    // null is returned if join was unsuccessful
                    if(gameRoomMessage != null) {
                        clientManager.clients
                                .filter { it.isAdministrator }
                                .forEach { it.send(gameRoomMessage) }
                    }
                }
                is RoomPacket -> {
                    // i.e. new move
                    val room = this.findRoom(packet.roomId)
                    room.onEvent(source, packet.data)
                }
                is AuthenticateRequest -> source.authenticate(packet.password)
                
                is AdminLobbyRequest -> if (source.isAdministrator) when (packet) {
                    is PrepareGameRequest -> {
                        source.send(this.prepareGame(packet))
                    }
                    is FreeReservationRequest -> {
                        ReservationManager.freeReservation(packet.reservation)
                    }
                    is ControlTimeoutRequest -> {
                        val room = this.findRoom(packet.roomId)
                        room.ensureOpenSlots(packet.slot + 1)
                        val slot = room.slots[packet.slot]
                        slot.descriptor = slot.descriptor.copy(canTimeout = packet.activate)
                        slot.role?.player?.canTimeout = packet.activate
                    }
                    is ObservationRequest -> {
                        val room = this.findRoom(packet.roomId)
                        room.addObserver(source)
                    }
                    is PauseGameRequest -> {
                        try {
                            val room = this.findRoom(packet.roomId)
                            room.pause(packet.pause)
                        } catch (e: RescuableClientException) {
                            this.logger.error("Got exception on pause: {}", e)
                        }
                    }
                    is StepRequest -> {
                        // TODO check for a prior pending StepRequest
                        val room = this.findRoom(packet.roomId)
                        room.step(packet.forced)
                    }
                    is CancelRequest -> {
                        val room = this.findRoom(packet.roomId)
                        room.cancel()
                        // TODO check whether all clients receive game over message
                    }
                    is TestModeRequest -> {
                        val testMode = packet.testMode
                        logger.info("Setting Test mode to {}", testMode)
                        Configuration.set(Configuration.TEST_MODE, testMode.toString())
                        source.send(TestModeResponse(testMode))
                    }
                }
                else -> throw RescuableClientException("Unhandled Packet of type: " + packet.javaClass)
            }
            callback.setProcessed()
        }
    }
    
    override fun close() {
        clientManager.close()
    }
    
    override fun onError(source: Client, errorPacket: ProtocolErrorMessage) {
        for (role in source.roles) {
            if (role.javaClass == PlayerRole::class.java) {
                (role as PlayerRole).playerSlot.room.onClientError(errorPacket)
            }
        }
    }
}
