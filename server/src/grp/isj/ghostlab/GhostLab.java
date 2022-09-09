package grp.isj.ghostlab;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class.
 */
public class GhostLab {
	public static boolean VERBOSE = true;
	public static final int DEFAULT_PORT = 43244;

	private int port;
	private List<Connection> connections;

	private byte currentPartyId = 0;
	private List<Party> parties;

	public GhostLab(int port) {
		this.port = port;
		this.connections = new ArrayList<Connection>();
		this.parties = new ArrayList<Party>();
	}

	public void start() {
		try {
			System.out.printf("WAITING FOR CONNECTIONS ON PORT %d\n\n", this.port);
			ServerSocket serverSocket = new ServerSocket(this.port);
			boolean isRunning = true;
			while (isRunning) {
				Socket socket = serverSocket.accept();
				System.out.printf("NEW CONNECTION FROM %s:%d\n\n", socket.getInetAddress(), socket.getPort());
				Connection connection = new Connection(this, socket);
				this.connections.add(connection);
				connection.start();
			}
			serverSocket.close();
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	public synchronized void removeConnection(Connection connection) throws IOException {
		this.connections.remove(connection);
	}

	public synchronized byte getOnStandbyParties() {
		byte partiesCount = 0;
		for (Party party : this.parties) {
			if (party.players.size() > 0) {
				partiesCount += 1;
			}
		}
		return partiesCount;
	}

	public synchronized boolean isPlayerIdTaken(String playerId) {
		boolean isTaken = false;
		for (Party party : this.parties) {
			for (Player player : party.players) {
				if (player.playerId.equals(playerId)) {
					isTaken = true;
				}
			}
		}
		return isTaken;
	}

	public synchronized Party createParty() throws NumberFormatException, IOException {
		Party party = new Party(this.currentPartyId);
		this.parties.add(party);
		this.currentPartyId++;
		return party;
	}

	public synchronized Party isValidParty(short partyId) {
		for (Party party : this.parties) {
			if (party.partyId == partyId) {
				return party;
			}
		}
		return null;
	}

	public synchronized Party getPartyForPlayer(Player player) {
		for (Party party : this.parties) {
			if (party.isPlayerRegistered(player)) {
				return party;
			}
		}
		return null;
	}

	public synchronized List<Party> getParties() {
		return parties;
	}
	
	public static byte[] formatToBytes(String format, Object... args) {
		return String.format(format, args).getBytes();
	}

	public static int[] toLittleEndianBytes(int x) {
		int data[] = new int[2];
		data[0] = (x & 0xff);
		data[1] = ((x >>> 8) & 0xff);
		return data;
	}
 
	public static void main(String[] args) {
		System.out.printf("\nCWD: %s\n", System.getProperty("user.dir"));
		System.out.printf("ENDIANNESS: %s\n", ByteOrder.nativeOrder());
		int port;
		try {
			port = Integer.parseInt(args[0]);
		} catch (Exception exception) {
			port = GhostLab.DEFAULT_PORT;
		}
		new GhostLab(port).start();
	}
}
