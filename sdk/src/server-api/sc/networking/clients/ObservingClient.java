package sc.networking.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.protocol.responses.ProtocolErrorMessage;
import sc.protocol.responses.ProtocolMessage;
import sc.shared.GameResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ObservingClient implements IControllableGame, IHistoryListener {
  private static final Logger logger = LoggerFactory.getLogger(ObservingClient.class);

  public ObservingClient(String roomId, boolean isPaused) {
    this.roomId = roomId;
    this.paused = isPaused;
  }

  public final String roomId;

  private final boolean replay = false;

  protected int position = 0;
  protected boolean paused;
  private boolean gameOver = false;

  private final List<ProtocolMessage> history = new ArrayList<>();
  private final List<IUpdateListener> listeners = new ArrayList<>();

  private GameResult result = null;

  protected void addObservation(ProtocolMessage observation) {
    boolean firstObservation = this.history.isEmpty();

    this.history.add(observation);
    if(logger.isDebugEnabled())
      logger.debug("{} saved observation {}", this, observation);

    if (canAutoStep() || firstObservation) {
      setPosition(this.history.size() - 1);
    }
  }

  private boolean canAutoStep() {
    return !this.replay || !this.paused;
  }

  @Override
  public void onNewState(String roomId, IGameState state) {
    if(logger.isDebugEnabled())
      logger.debug("{} got new state {}", this, state);
    if (isAffected(roomId)) {
      addObservation(state);
      notifyOnUpdate();
    }
  }

  protected boolean isAffected(String roomId) {
    return this.replay || this.roomId.equals(roomId);
  }

  protected void notifyOnUpdate() {
    for (IUpdateListener listener : this.listeners) {
      listener.onUpdate(this);
    }
  }

  protected void notifyOnError(String errorMessage) {
    for (IUpdateListener listener : this.listeners) {
      listener.onError(errorMessage);
    }
  }

  @Override
  public void removeListener(IUpdateListener u) {
    this.listeners.remove(u);
  }

  @Override
  public void addListener(IUpdateListener u) {
    this.listeners.add(u);
    u.onUpdate(this);
  }

  @Override
  public void next() {
    this.changePosition(+1);
  }

  @Override
  public void pause() {
    paused = true;
    notifyOnUpdate();
  }

  @Override
  public void previous() {
    if (!isPaused()) {
      pause();
    }

    this.changePosition(-1);
  }

  protected void changePosition(int i) {
    this.setPosition(this.getPosition() + i);
  }

  private int getPosition() {
    return this.position;
  }

  @Override
  public void unpause() {
    paused = false;

    if (this.replay) {
      next();
    } else {
      this.setPosition(this.history.size() - 1);
    }
  }

  protected void setPosition(int i) {
    int newPosition = Math.max(0, Math.min(this.history.size() - 1, i));
    logger.debug("Setting Position to {} (requested {})", newPosition, i);
    if (newPosition != this.position) {
      this.position = newPosition;
      notifyOnUpdate();
    }
  }

  @Override
  public Object getCurrentState() {
    if (this.history.size() == 0) {
      return null;
    }

    int pos = this.position;
    while (this.history.get(pos) instanceof ProtocolErrorMessage) {
      pos--;
    }
    return this.history.get(pos);
  }

  @Override
  public Object getCurrentError() {
    if (this.history.size() == 0) {
      return null;
    }

    Object state = this.history.get(this.position);
    return (state instanceof ProtocolErrorMessage ? state : null);
  }

  @Override
  public boolean isAtStart() {
    return this.getPosition() == 0;
  }

  @Override
  public boolean isAtEnd() {
    return this.getPosition() >= this.history.size() - 1;
  }

  public List<ProtocolMessage> getHistory() {
    return Collections.unmodifiableList(this.history);
  }

  @Override
  public boolean hasNext() {
    return (this.getPosition() + 1) < this.history.size();
  }

  @Override
  public boolean hasPrevious() {
    return this.getPosition() > 0;
  }

  @Override
  public boolean isPaused() {
    return paused;
  }

  @Override
  public boolean isGameOver() {
    return this.replay || this.gameOver;
  }

  public void close() {
    this.history.clear();
  }

  @Override
  public void onGameOver(String roomId, GameResult result) {
    logger.info("Saving GameResult");

    if (this.result != null) {
      logger.warn("Received extra GameResult");
    }

    this.gameOver = true;
    this.result = result;

    notifyOnUpdate();
  }

  @Override
  public void cancel() {
    paused = true;
  }

  @Override
  public void goToFirst() {
    this.setPosition(0);
  }

  @Override
  public void goToLast() {
    this.setPosition(getHistory().size() - 1);
  }

  @Override
  public boolean canTogglePause() {
    return false;
  }

  @Override
  public GameResult getResult() {
    return this.result;
  }

  @Override
  public boolean isReplay() {
    return this.replay;
  }

  @Override
  public void onGameError(String roomId, ProtocolErrorMessage error) {
    if(logger.isDebugEnabled())
      logger.debug("{} (room: {})", error.getLogMessage(), roomId);
    if (isAffected(roomId)) {
      addObservation(error);
      notifyOnError(error.getMessage());
    }
  }

}
