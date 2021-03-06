package sc.server.plugins;

import sc.api.plugins.exceptions.RescuableClientException;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class UnknownGameTypeException extends RescuableClientException {
  private static final long serialVersionUID = -6520842646313711672L;

  private final Iterable<String> availableUUIDs;

  public UnknownGameTypeException(String string, Iterable<String> iterable) {
    super(string);
    this.availableUUIDs = iterable;
  }

  @Override
  public String getMessage() {
    return "Unknown GameType UUID: " + super.getMessage() + " (available: "
            + StreamSupport.stream(availableUUIDs.spliterator(), false).collect(Collectors.joining(",")) + ")";
  }

}
