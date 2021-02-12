package sc.server.gaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.exceptions.RescuableClientException;
import sc.networking.InvalidScoreDefinitionException;
import sc.protocol.requests.PrepareGameRequest;
import sc.protocol.responses.GamePreparedResponse;
import sc.protocol.responses.RoomWasJoinedEvent;
import sc.server.Configuration;
import sc.server.network.Client;
import sc.server.plugins.GamePluginInstance;
import sc.server.plugins.GamePluginManager;
import sc.server.plugins.UnknownGameTypeException;
import sc.shared.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/**
 * The GameManager is responsible to keep all games alive and kill them once
 * they are done. Additionally the GameManager has to detect and kill games,
 * which seem to be dead-locked or have caused a timeout.
 */
public class GameRoomManager {
  private Map<String, GameRoom> rooms;

  private final GamePluginManager gamePluginManager = new GamePluginManager();

  private static final Logger logger = LoggerFactory.getLogger(GameRoomManager.class);

  /** Default constructor, initializes rooms, loads available plugins. */
  public GameRoomManager() {
    this.rooms = new HashMap<>();
    this.gamePluginManager.reload();
    this.gamePluginManager.activateAllPlugins();
  }

  /** Adds an active GameRoom to this <code>GameManager</code> */
  private synchronized void add(GameRoom room) {
    logger.debug("Adding room with id {}", room.getId());
    this.rooms.put(room.getId(), room);
  }

  /**
   * Create a not prepared {@link GameRoom GameRoom} of given type
   *
   * @param gameType String of current Game
   *
   * @return Newly created GameRoom
   *
   * @throws RescuableClientException if creation of game failed
   */
  public synchronized GameRoom createGame(String gameType) throws RescuableClientException {
    return createGame(gameType, false);
  }

  /**
   * Create a new GameRoom from the matching plugin.
   * If gameFile is set, load gameState from file.
   *
   * @param gameType id of the game plugin to use
   * @param prepared signals whether the game was prepared by gui or ..., false if player has to send JoinRoomRequest
   *
   * @return newly created GameRoom
   *
   * @throws UnknownGameTypeException if no matching GamePlugin was found
   */
  public GameRoom createGame(String gameType, boolean prepared) throws RescuableClientException {
    GamePluginInstance plugin = this.gamePluginManager.getPlugin(gameType);

    if (plugin == null) {
      logger.warn("Couldn't find a game of type " + gameType);
      throw new UnknownGameTypeException(gameType, this.gamePluginManager.getPluginUUIDs());
    }

    logger.info("Creating new game of type " + gameType);

    String roomId = generateRoomId();
    GameRoom room = new GameRoom(roomId, this, plugin.getPlugin().getScoreDefinition(), plugin.createGame(), prepared);
    // pause room if specified in server.properties on joinRoomRequest
    if (!prepared) {
      boolean paused = Boolean.parseBoolean(Configuration.get(Configuration.PAUSED));
      room.pause(paused);
      logger.info("Pause is set to {}", paused);
    }

    String gameFile = Configuration.get(Configuration.GAMELOADFILE);
    if (gameFile != null && !gameFile.equals("")) {
      logger.info("Request plugin to load game from file: " + gameFile);
      int turn;
      if (Configuration.get(Configuration.TURN_TO_LOAD) != null) {
        turn = Integer.parseInt(Configuration.get(Configuration.TURN_TO_LOAD));
      } else {
        turn = 0;
      }
      logger.debug("Turns is to load is: " + turn);
      if (turn > 0) {
        logger.debug("Loading from non default turn");
        room.game.loadFromFile(gameFile, turn);
      } else {
        logger.debug("Loading first gameState found");
        room.game.loadFromFile(gameFile);
      }
    }

    this.add(room);

    return room;
  }

  private static synchronized String generateRoomId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Open new GameRoom and join the client.
   *
   * @return GameRoomMessage with roomId, null if unsuccessful
   *
   * @throws RescuableClientException if game could not be created
   */
  public synchronized RoomWasJoinedEvent createAndJoinGame(Client client, String gameType)
          throws RescuableClientException {
    GameRoom room = createGame(gameType);
    if (room.join(client)) {
      return new RoomWasJoinedEvent(room.getId(), false);
    }
    return null;
  }

  /**
   * Called on JoinRoomRequest. Client joins an already existing open GameRoom or opens new one and joins.
   *
   * @return GameRoomMessage with roomId, null if unsuccessful
   *
   * @throws RescuableClientException if client could not join room
   */
  public synchronized RoomWasJoinedEvent joinOrCreateGame(Client client, String gameType)
          throws RescuableClientException {
    for (GameRoom gameRoom : getGames()) {
      if (gameRoom.join(client)) {
        return new RoomWasJoinedEvent(gameRoom.getId(), true);
      }
    }

    return createAndJoinGame(client, gameType);
  }

  /** Create an unmodifiable Collection of the {@link GameRoom GameRooms}. */
  public synchronized Collection<GameRoom> getGames() {
    return Collections.unmodifiableCollection(this.rooms.values());
  }

  public GamePluginManager getPluginManager() {
    return this.gamePluginManager;
  }

  /**
   * Creates a new GameRoom through {@link #createGame(String) createGame} with reserved PlayerSlots according to the
   * descriptors and loads a game state from a file if provided.
   *
   * @return new PrepareGameProtocolMessage with roomId and slot reservations
   *
   * @throws RescuableClientException if game could not be created
   */
  public synchronized GamePreparedResponse prepareGame(String gameType, boolean paused, SlotDescriptor[] descriptors, Object loadGameInfo)
          throws RescuableClientException {
    GameRoom room = createGame(gameType, true);
    room.pause(paused);
    room.openSlots(descriptors);

    if (loadGameInfo != null) {
      room.game.loadGameInfo(loadGameInfo);
    }

    return new GamePreparedResponse(room.getId(), room.reserveAllSlots());
  }

  /**
   * Overload for {@link #prepareGame}.
   *
   * @return new PrepareGameProtocolMessage with roomId and slot reservations
   *
   * @throws RescuableClientException if game could not be created
   */
  public GamePreparedResponse prepareGame(PrepareGameRequest prepared) throws RescuableClientException {
    return prepareGame(
            prepared.getGameType(),
            prepared.getPause(),
            prepared.getSlotDescriptors(),
            null
    );
  }

  /**
   * @param roomId String Id of room to be found
   *
   * @return returns GameRoom specified by roomId
   *
   * @throws RescuableClientException if no room could be found
   */
  public synchronized GameRoom findRoom(String roomId) throws RescuableClientException {
    GameRoom room = this.rooms.get(roomId);

    if (room == null) {
      throw new RescuableClientException("Couldn't find a room with id " + roomId);
    }

    return room;
  }

  /** Remove specified room from this manager. */
  public synchronized void remove(GameRoom gameRoom) {
    this.rooms.remove(gameRoom.getId());
  }

}
