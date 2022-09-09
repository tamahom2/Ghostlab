package grp.isj.ghostlab;

public class InvalidPartySizeException extends Exception  {
	protected int partyId;

	public InvalidPartySizeException(int partyId) {
		this.partyId = partyId;
	}
}

