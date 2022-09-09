package grp.isj.ghostlab;

public class InvalidPlayerUDPException extends Exception {
	protected int udpPort;

	public InvalidPlayerUDPException(int udpPort) {
		this.udpPort = udpPort;
	}
}
