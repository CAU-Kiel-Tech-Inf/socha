package client.sc.plugin2015;

/**
 * @author ffi
 * 
 */
public interface IGUIObservation {

	/**
	 * @param activePlayerId
	 * @param actionFromOther
	 */
	void newTurn(int activePlayerId, String actionFromOther);

	/**
	 * 
	 */
	void ready();

}
