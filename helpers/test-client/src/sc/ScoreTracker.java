package sc;

import sc.networking.InvalidScoreDefinitionException;
import sc.server.Configuration;
import sc.shared.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

public class ScoreTracker {

  /**
   * Called by gameRoom after game ended and test mode enabled to save results in playerScores.
   *
   * @param name1        displayName of player1
   * @param name2        displayName of player2
   *
   * @throws InvalidScoreDefinitionException if scoreDefinitions do not match
   */
  public void addResultToScore(GameResult result, String name1, String name2) throws InvalidScoreDefinitionException {
  }

}
