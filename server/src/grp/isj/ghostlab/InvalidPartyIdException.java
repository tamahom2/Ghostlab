package grp.isj.ghostlab;

public class InvalidPartyIdException extends Exception {
	protected int partyId;

	public InvalidPartyIdException(int partyId) {
		this.partyId = partyId;
	}
}
