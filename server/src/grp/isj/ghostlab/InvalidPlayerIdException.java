package grp.isj.ghostlab;

public class InvalidPlayerIdException extends Exception {
	protected String playerId;

	public InvalidPlayerIdException(String playerId) {
		this.playerId = playerId;
	}
}