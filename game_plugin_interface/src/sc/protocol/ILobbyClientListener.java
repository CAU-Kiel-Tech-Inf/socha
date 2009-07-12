package sc.protocol;

import sc.api.plugins.GameResult;
import sc.protocol.responses.PrepareGameResponse;

public interface ILobbyClientListener
{

	void onNewState(String roomId, Object state);

	void onError(ErrorResponse error);

	void onRoomMessage(String roomId, Object data);

	void onGamePrepared(PrepareGameResponse response);

	void onGameLeft(String roomId);

	void onGameJoined(String roomId);

	void onGameOver(String roomId, GameResult data);

	void onGamePaused(String roomId);

}
