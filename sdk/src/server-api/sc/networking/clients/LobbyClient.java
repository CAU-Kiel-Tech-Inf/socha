package sc.networking.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.api.plugins.host.IRequestResult;
import sc.framework.plugins.Player;
import sc.networking.INetworkInterface;
import sc.networking.TcpNetwork;
import sc.protocol.helpers.AsyncResultManager;
import sc.protocol.helpers.RequestResult;
import sc.protocol.requests.*;
import sc.protocol.responses.*;
import sc.shared.GameResult;
import sc.shared.SharedConfiguration;
import sc.shared.SlotDescriptor;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

/**
 * This class is used to handle all communication with a server.
 *
 * - It is used in a client (e.g. the java simple client).
 * - It is also used to represent observer-threads started by the server which connect to the server.
 *
 * The server always has a Client object for every LobbyClient representing the client on the server-side.
 */
public final class LobbyClient extends XStreamClient implements IPollsHistory {
  private static final Logger logger = LoggerFactory.getLogger(LobbyClient.class);
  private final AsyncResultManager asyncManager = new AsyncResultManager();
  private final List<ILobbyClientListener> listeners = new ArrayList<>();
  private final List<IHistoryListener> historyListeners = new ArrayList<>();
  private final List<IAdministrativeListener> administrativeListeners = new ArrayList<>();

  public static final String DEFAULT_HOST = "127.0.0.1";

  public LobbyClient() throws IOException {
    this(DEFAULT_HOST, SharedConfiguration.DEFAULT_PORT);
  }

  public LobbyClient(String host, int port) throws IOException {
    super(createTcpNetwork(host, port));
  }

  private static INetworkInterface createTcpNetwork(String host, int port) throws IOException {
    logger.info("Creating TCP Network for {}:{}", host, port);
    return new TcpNetwork(new Socket(host, port));
  }

  @Override
  protected final void onObject(ProtocolMessage o) {
    if (o == null) {
      logger.warn("Received null message.");
      return;
    }

    invokeHandlers(o);

    if (o instanceof RoomPacket) {
      RoomPacket packet = (RoomPacket) o;
      String roomId = packet.getRoomId();
      ProtocolMessage data = packet.getData();
      if (data instanceof MementoEvent) {
        onNewState(roomId, ((MementoEvent) data).getState());
      } else if (data instanceof GameResult) {
        onGameOver(roomId, (GameResult) data);
      } else if (data instanceof GamePausedEvent) {
        onGamePaused(roomId, ((GamePausedEvent) data).getNextPlayer());
      } else if (data instanceof ProtocolErrorMessage) {
        onError(roomId, ((ProtocolErrorMessage) data));
      } else {
        onRoomMessage(roomId, data);
      }
    } else if (o instanceof GamePreparedResponse) {
      onGamePrepared((GamePreparedResponse) o);
    } else if (o instanceof JoinedRoomResponse) {
      onGameJoined(((JoinedRoomResponse) o).getRoomId());
    } else if (o instanceof RoomWasJoinedEvent) {
      onGameJoined(((RoomWasJoinedEvent) o).getRoomId());
    } else if (o instanceof LeftGameEvent) {
      onGameLeft(((LeftGameEvent) o).getRoomId());
    } else if (o instanceof ObservationResponse) {
      onGameObserved(((ObservationResponse) o).getRoomId());
    } else if (o instanceof TestModeResponse) {
      boolean testMode = (((TestModeResponse) o).getTestMode());
      logger.info("TestMode was set to {} ", testMode);
    } else if (o instanceof ProtocolErrorMessage) {
      ProtocolErrorMessage response = (ProtocolErrorMessage) o;
      onError(null, response);
    } else {
      onCustomObject(o);
    }
  }

  private void onGamePaused(String roomId, Player nextPlayer) {
    for (IAdministrativeListener listener : this.administrativeListeners) {
      listener.onGamePaused(roomId, nextPlayer);
    }

    for (ILobbyClientListener listener : this.listeners) {
      listener.onGamePaused(roomId, nextPlayer);
    }
  }

  private void onGameOver(String roomId, GameResult data) {
    logger.info("Received game result: {}", data);
    for (IHistoryListener listener : this.historyListeners) {
      listener.onGameOver(roomId, data);
    }

    for (ILobbyClientListener listener : this.listeners) {
      listener.onGameOver(roomId, data);
    }
  }

  private void onGameLeft(String roomId) {
    for (ILobbyClientListener listener : this.listeners) {
      listener.onGameLeft(roomId);
    }
  }

  private void onGameJoined(String roomId) {
    for (ILobbyClientListener listener : this.listeners) {
      listener.onGameJoined(roomId);
    }
  }

  private void onGameObserved(String roomId) {
    for (ILobbyClientListener listener : this.listeners) {
      listener.onGameObserved(roomId);
    }
  }

  private void invokeHandlers(ProtocolMessage o) {
    if (o == null) {
      throw new IllegalArgumentException("o was null");
    }
    this.asyncManager.invokeHandlers(o);
  }

  protected void onGamePrepared(GamePreparedResponse response) {
    for (ILobbyClientListener listener : this.listeners) {
      listener.onGamePrepared(response);
    }
  }

  public void authenticate(String password) {
    send(new AuthenticateRequest(password));
  }

  @SuppressWarnings("unchecked")
  public RequestResult<GamePreparedResponse> prepareGameAndWait(String gameType) throws InterruptedException {
    return blockingRequest(new PrepareGameRequest(gameType), GamePreparedResponse.class);
  }

  @SuppressWarnings("unchecked")
  public RequestResult<GamePreparedResponse> prepareGameAndWait(
          PrepareGameRequest request) throws InterruptedException {
    return blockingRequest(request, GamePreparedResponse.class);
  }

  public void prepareGame(String gameType) {
    send(new PrepareGameRequest(gameType));
  }

  public void prepareGame(String gameType, boolean startPaused) {
    send(new PrepareGameRequest(
        gameType,
        new SlotDescriptor("player1", false),
        new SlotDescriptor("player2", false),
        startPaused)
    );
  }

  protected void onCustomObject(Object o) {
    logger.warn("Couldn't process message {}.", o);
  }

  protected void onNewState(String roomId, IGameState state) {
    for (ILobbyClientListener listener : this.listeners) {
      listener.onNewState(roomId, state);
    }
    for (IHistoryListener listener : this.historyListeners) {
      listener.onNewState(roomId, state);
    }
  }

  protected void onRoomMessage(String roomId, ProtocolMessage data) {
    for (ILobbyClientListener listener : this.listeners) {
      listener.onRoomMessage(roomId, data);
    }
  }

  protected void onError(String roomId, ProtocolErrorMessage error) {
    logger.warn("{} (room: {})", error.getLogMessage(), roomId);
    for (ILobbyClientListener listener : this.listeners) {
      listener.onError(roomId, error);
    }
    for (IHistoryListener listener : this.historyListeners) {
      listener.onGameError(roomId, error);
    }
  }

  public void sendMessageToRoom(String roomId, ProtocolMessage o) {
    send(new RoomPacket(roomId, o));
  }

  /**
   * used in server
   *
   * @param reservation reservation ID
   */
  public void joinPreparedGame(String reservation) {
    send(new JoinPreparedRoomRequest(reservation));
  }

  /**
   * currently not used in server
   *
   * @param gameType GameID
   */
  public void joinRoomRequest(String gameType) {
    send(new JoinRoomRequest(gameType));
  }

  /**
   * used in server
   *
   * @param request  ProtocolMessage which contains the request
   * @param response Response class to be created
   * @param handler  Handler for the requests
   */
  protected void request(ProtocolMessage request, Class<? extends ProtocolMessage> response,
                         IRequestResult handler) {
    this.asyncManager.addHandler(response, handler);
    send(request);
  }

  protected RequestResult blockingRequest(ProtocolMessage request,
                                          Class<? extends ProtocolMessage> response) throws InterruptedException {
    // TODO return a proper future here
    // This is really old async code, so the variable needs to be final but still mutable
    // IDEA suggested to use an array and we'll stay with that until we reimplement it properly.
    final RequestResult[] requestResult = {null};
    final Object beacon = new Object();
    synchronized(beacon) {
      IRequestResult blockingHandler = new IRequestResult() {
        @Override
        public void handleError(ProtocolErrorMessage e) {
          requestResult[0] = new RequestResult.Error(e);
          notifySemaphore();
        }

        @Override
        public void accept(ProtocolMessage result) {
          requestResult[0] = new RequestResult.Success<>(result);
          notifySemaphore();
        }

        private void notifySemaphore() {
          synchronized(beacon) {
            beacon.notify();
          }
        }
      };
      request(request, response, blockingHandler);
      beacon.wait();
    }

    return requestResult[0];
  }

  public void addListener(ILobbyClientListener listener) {
    this.listeners.add(listener);
  }

  public void removeListener(ILobbyClientListener listener) {
    this.listeners.remove(listener);
  }

  /** Takes control of the game in the given room and pauses it. */
  public IControllableGame observeAndControl(String roomId) {
    final IControllableGame controller = observeAndControl(roomId, true);
    controller.pause();
    return controller;
  }

  /** Takes control of the game in the given room.
   * @param isPaused whether the game to observe is already paused. */
  public IControllableGame observeAndControl(String roomId, boolean isPaused) {
    ControllingClient controller = new ControllingClient(this, roomId, isPaused);
    addListener((IAdministrativeListener) controller);
    addListener((IHistoryListener) controller);
    requestObservation(roomId);
    return controller;
  }

  public ObservingClient observe(String roomId) {
    return observe(roomId, false);
  }

  public ObservingClient observe(String roomId, boolean isPaused) {
    ObservingClient observer = new ObservingClient(roomId, isPaused);
    addListener(observer);
    requestObservation(roomId);
    return observer;
  }

  private void requestObservation(String roomId) {
    start();
    logger.debug("Sending observation request for roomId: {}", roomId);
    send(new ObservationRequest(roomId));
  }

  @Override
  public void addListener(IHistoryListener listener) {
    this.historyListeners.add(listener);
  }

  @Override
  public void removeListener(IHistoryListener listener) {
    this.historyListeners.remove(listener);
  }

  public void addListener(IAdministrativeListener listener) {
    this.administrativeListeners.add(listener);
  }

  public void removeListener(IAdministrativeListener listener) {
    this.administrativeListeners.remove(listener);
  }

  public void freeReservation(String reservation) {
    send(new FreeReservationRequest(reservation));
  }

}
