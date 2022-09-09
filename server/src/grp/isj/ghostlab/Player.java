package grp.isj.ghostlab;

import java.util.HashSet;
import java.util.Set;

/**
 * Stores data for a player "entity".
 * 
 * Used by {@link Connection} and {@link Party}
 */
public class Player {
	public static final Set<Integer> TAKEN_UDP_PORTS = new HashSet<Integer>();

	protected Connection connection;
	protected String playerId;
	protected int udpPort;

	protected int x, y, points;

	public Player(Connection connection, String id, int udpPort) {
		this.connection = connection;
		this.playerId = id;
		this.udpPort = udpPort;
		this.x = 0;
		this.y = 0;
		this.points = 0;
		Player.TAKEN_UDP_PORTS.add(udpPort);
	}
}
