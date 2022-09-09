package grp.isj.ghostlab;

public class InvalidPermission extends Exception {
	protected String playerId;

	public InvalidPermission(String playerId) {
		this.playerId = playerId;
	}
}