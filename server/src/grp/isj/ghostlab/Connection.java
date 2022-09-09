package grp.isj.ghostlab;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Represents a connection to the server.
 * 
 * Input & output are handled here.
 */
public class Connection extends Thread {
	// Keep a reference to the lab for access to parties
	protected final GhostLab lab;
	// The connection's socket
	private final Socket socket;

	// Is this connection/"player" waiting for a game or ingame?
	protected volatile boolean waitingForGame, inGame;

	// IO
	protected OutputStream output;
	protected InputStream input;

	// To each connection there's an associated player & party
	protected Player currentPlayer;
	protected Party currentParty;

	public Connection(GhostLab lab, Socket socket) throws IOException {
		this.lab = lab;
		this.socket = socket;
		this.waitingForGame = false;
		this.inGame = false;
		this.output = socket.getOutputStream();
		this.input = socket.getInputStream();
		this.currentPlayer = null;
		this.currentParty = null;
	}

	@Override
	public void run() {
		try {
			this.sendGamesInfo();
			boolean keepGoing = true;
			while (keepGoing) {
				while (!this.inGame && !this.waitingForGame) {
					byte buffer[] = new byte[1024];
					int read = this.input.read(buffer);
					if (read != -1) {
						String in = new String(buffer, 0, read).strip();
						System.out.printf("NEW FROM %s:%d\n\t%s\n", this.socket.getInetAddress(), this.socket.getPort(), in);
						this.handleCommand(in);
					} else {
						keepGoing = false;
						break;
					}
				}
				while (this.waitingForGame) { // Do nothing while we're waiting for the game to start, sleep to not waste CPU cycles
					try {
						Thread.sleep(1);
					} catch (InterruptedException InterruptedException) {
						continue;
					}
				}
				while (this.inGame) {
					byte buffer[] = new byte[1024];
					int read = this.input.read(buffer);
					if (read != -1) {
						String in = new String(buffer, 0, read).strip();
						System.out.printf("NEW FROM %s:%d\n\t%s\n\n", this.socket.getInetAddress(), this.socket.getPort(), in);
						this.handleCommand(in);
					} else {
						keepGoing = false;
						break;
					}
				} 
			} 
		} catch (IOException ioException) {
			ioException.printStackTrace();
			System.out.printf("CONNECTION CLOSED (%s:%d)\n", this.socket.getInetAddress(), this.socket.getPort());
			try {
				this.cleanUp(true);
			} catch (IOException anotherIoException) {
				return;
			}
		}
		try {
			this.cleanUp(true);
		} catch (IOException anotherIoException) {
			return;
		}
	}

	// Called when connection should close
	private void cleanUp(boolean closeSocket) throws IOException {
		if (this.currentPlayer != null) {
			this.currentParty.unregisterPlayer(this.currentPlayer);
		}
		this.lab.removeConnection(this);
		if (closeSocket) {
			this.close();
			System.out.printf("CLIENT DISCONNECTED: %s:%d\n\n", this.socket.getInetAddress(), this.socket.getPort());
		}
	}

	private void handleCommand(String command) throws IOException {
		if (command.length() >= 5) {
			String requestStr = command.substring(0, 5);
			if (requestStr.endsWith("?")) requestStr = requestStr.substring(0, 4) + "Q";
			if (requestStr.endsWith("!")) requestStr = requestStr.substring(0, 4) + "A";
			Request request = Request.fromString(requestStr);
			if (GhostLab.VERBOSE) System.out.printf("REQUEST AS STR %s, REQUEST %s\n", requestStr, request);
			String terminator = command.substring(command.length() - 3);
			// Check if command ends wtih "***" and is such well formed
			if (terminator.equals("***") && command.charAt(command.length() - 4) != '*') {
				if (!this.waitingForGame && !this.inGame) {
					switch (request) {
						case NEWPL:
							this.handleNEWPL(command);
							break;
						case REGIS:
							this.handleREGIS(command);
							break;
						case UNREG:
							this.handleUNREG(command);
							break;
						case SIZEQ:
							this.handleSIZEQ(command);
							break;
						case LISTQ:
							this.handleLISTQ(command);
							break;
						case GAMEQ:
							this.handleGAMEQ(command);
							break;
						case HELPQ:
							this.handleHELPQ(command);
							break;
						case START:
							this.handleSTART(command);
							break;
						case PSIZE:
							this.handlePSIZE(command);
							break;
						case POWNT:
							this.handlePOWNT(command);
							break;
						case PGHST:
							this.handlePGHST(command);
							break;
						case PWDTH:
							this.handlePWDTH(command);
							break;
						case PHGHT:
							this.handlePHGHT(command);
							break;
						default:
							break;
					}
				} else if (this.waitingForGame) {
					switch (request) {
						case HELPQ:
							this.handleHELPQ(command);
							break;
						default:
							break;
					}
				} else if (this.inGame) {
					switch (request) {
						case UPMOV:
							Direction direction = Direction.UP;
							this.handleMoves(command, direction);
							break;
						case DOMOV:
							direction = Direction.DOWN;
							this.handleMoves(command, direction);
							break;
						case LEMOV:
							direction = Direction.LEFT;
							this.handleMoves(command, direction);
							break;
						case RIMOV:
							direction = Direction.RIGHT;
							this.handleMoves(command, direction);
							break;
						case GLISQ:
							this.handleGLISQ(command);
							break;
						case MALLQ:
							this.handleMALLQ(command);
							break;
						case IQUIT:
							this.handleIQUIT(command);
							break;
						case SENDQ:
							this.handleSENDQ(command);
							break;
						case HELPQ:
							this.handleHELPQ(command);
							break;
						default:
							break;
					} 
				}
			} else {
				System.out.printf("INVALID REQUEST (WRONG TERMINATOR)\n\n");
			}
		} else {
			System.out.println("INVALID REQUEST\n\n");
		}
	}

	// FORMAT: NEWPL ID_8_CHARS UDP_PORT***
	private void handleNEWPL(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			String playerId = args[1];
			String playerUdpPortStr = args[2].substring(0, args[2].indexOf("***"));
			int playerUdpPort = Integer.parseInt(playerUdpPortStr);
			boolean isAlphanumeric = true;
			for (char ch : playerId.toCharArray()) {
				if (!Character.isLetterOrDigit(ch)) {
					isAlphanumeric = false;
				}
			}
			if (Player.TAKEN_UDP_PORTS.contains(playerUdpPort) || playerUdpPort <= 0 || playerUdpPortStr.length() > 4) {
				throw new InvalidPlayerUDPException(playerUdpPort);
			}
			if (playerId.length() != 8 || !isAlphanumeric) {
				throw new InvalidPlayerIdException(playerId);
			}
			System.out.printf("VALID REQUEST FROM %s:%d\n\tTYPE: NEWPL\n\tPLAYER ID: %s\n\tPORT: %d\n\n", this.socket.getInetAddress(), this.socket.getPort(), playerId, playerUdpPort);
			// Lock the lab field so we can get an unique party id
			synchronized (this.lab) {
				Party party = this.lab.createParty();
				Player newPlayer = new Player(this, playerId, playerUdpPort);
				party.registerPlayer(newPlayer);
				this.currentPlayer = newPlayer;
				this.currentParty = party;
				this.currentParty.owner = newPlayer;
				this.output.write("REGOK ".getBytes());
				this.output.write(party.partyId & 0xff);
				this.output.write("***".getBytes());
			}
			return;
		} catch (NumberFormatException | IndexOutOfBoundsException exception) {
			System.out.printf("INVALID REQUEST\n\n");
		} catch (InvalidPlayerIdException exception) {
			System.out.printf("INVALID PLAYER ID: %s\n\n", exception.playerId);
		} catch (InvalidPlayerUDPException exception) {
			System.out.printf("INVALID UDP PORT: %d\n\n", exception.udpPort);
		}
		this.write("REGNO***");
	}

	// FORMAT: REGIS ID_8_CHARS UDP_PORT PARTY_ID***
	private void handleREGIS(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			// If player is already registered to another party, unregister him
			if (this.currentParty != null) {
				this.currentParty.unregisterPlayer(this.currentPlayer);
				this.currentPlayer = null;
			}
			String playerId = args[1];
			String playerUdpPortStr = args[2];
			int playerUdpPort = Integer.parseInt(playerUdpPortStr);
			short partyId = Short.parseShort(args[3].substring(0, args[3].indexOf("***")));
			Party party = this.lab.isValidParty(partyId);
			if (party == null) {
				throw new InvalidPartyIdException(partyId);
			}
			if (party.partySize == party.players.size()) {
				throw new InvalidPartySizeException(partyId);
			}
			for (Player p : party.players) {
				if (p.playerId.equals(playerId)) {
					throw new InvalidPlayerIdException(playerId);
				}
			}
			if (Player.TAKEN_UDP_PORTS.contains(playerUdpPort) || playerUdpPort <= 0 || playerUdpPortStr.length() > 4) {
				throw new InvalidPlayerUDPException(playerUdpPort);
			}
			System.out.printf("VALID REQUEST FROM %s:%d\n\tTYPE: REGIS\n\tPLAYER ID: %s\n\tPORT: %d\n\tPARTY ID: %d\n\n", this.socket.getInetAddress(), this.socket.getPort(), playerId, playerUdpPort, partyId);
			this.output.write("REGOK ".getBytes());
			this.output.write(party.partyId & 0xff);
			this.output.write("***".getBytes());
			Player player = new Player(this, playerId, playerUdpPort);
			party.registerPlayer(player);
			this.currentPlayer = player;
			this.currentParty = party;
			return;
		} catch (NumberFormatException | IndexOutOfBoundsException exception) {
			System.out.printf("INVALID REQUEST\n\n");
		} catch (InvalidPlayerIdException exception) {
			System.out.printf("PLAYER ID %s TAKEN\n\n", exception.playerId);
		} catch (InvalidPartyIdException exception) {
			System.out.printf("PARTY %d DOESN'T EXIST\n\n", exception.partyId);
		} catch (InvalidPlayerUDPException exception) {
			System.out.printf("INVALID UDP PORT: %d\n\n", exception.udpPort);
		}catch (InvalidPartySizeException exception) {
			System.out.printf("PARTY %d IS FULL\n\n", exception.partyId);
		}
		this.write("REGNO***");
	}

	// FORMAT: PSIZE PARTYSIZE***
	private void handlePSIZE(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			if (this.currentParty != null) {
				short partySize = Short.parseShort(args[1].substring(0, args[1].indexOf("***")));
				if (this.currentPlayer != this.currentParty.owner){
					throw new InvalidPermission(this.currentPlayer.playerId);
				}
				System.out.printf("PLAYER ID: %s CHANGING PARTY SIZE TO %d\n", this.currentPlayer.playerId, partySize);
				this.currentParty.partySize = partySize;
				this.write("PALLW***");
				return;
			} else {
				this.write("DUNNO***");
			}
		} 
		catch (InvalidPermission exception) {
			System.out.printf("PLAYER ID %s IS NOT THE OWNER\n\n", exception.playerId);
		}
		this.write("PDENY***");
	}

	// FORMAT: POWNT PLAYERID***
	private void handlePOWNT(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			if (this.currentParty != null) {
				String playerId = args[1].substring(0, args[1].indexOf("***"));
				Player targetPlayer = null;
				for (Player currentPlayer : this.currentParty.players) {
					if (currentPlayer.playerId.equals(playerId))  {
						targetPlayer = currentPlayer;
					}
				}
				if (this.currentPlayer != this.currentParty.owner) {
					throw new InvalidPermission(this.currentPlayer.playerId);
				}
				this.currentParty.owner = targetPlayer;
				this.write("PALLW***");
				return;
			} else {
				this.write("DUNNO***");
			}
		} catch (InvalidPermission exception) {
			System.out.printf("PLAYER ID %s IS NOT THE OWNER\n\n", exception.playerId);
		}
		this.write("PDENY***");
	}

	// FORMAT: PWDTH WIDTH***
	private void handlePWDTH(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			if (this.currentParty != null) {
				short width = Short.parseShort(args[1].substring(0, args[1].indexOf("***")));
				if (this.currentPlayer != this.currentParty.owner){
					throw new InvalidPermission(this.currentPlayer.playerId);
				}
				this.currentParty.generateMaze(this.currentParty.height, width);
				this.currentParty.generateGhosts();
				this.write("PALLW***");
				return;
			} else {
				this.write("DUNNO***");
			}
		} 
		catch (InvalidPermission exception) {
			System.out.printf("PLAYER ID %s IS NOT THE OWNER\n\n", exception.playerId);
		}
		this.write("PDENY***");
	}

	// FORMAT: PHGHT HEIGHT***
	private void handlePHGHT(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			if (this.currentParty != null) {
				short height = Short.parseShort(args[1].substring(0, args[1].indexOf("***")));
				if (this.currentPlayer != this.currentParty.owner){
					throw new InvalidPermission(this.currentPlayer.playerId);
				}
				this.currentParty.generateMaze(height, this.currentParty.width);
				this.currentParty.generateGhosts();
				this.write("PALLW***");
				return;
			} else {
				this.write("DUNNO***");
			}
		} 
		catch (InvalidPermission exception) {
			System.out.printf("PLAYER ID %s IS NOT THE OWNER\n\n", exception.playerId);
		}
		this.write("PDENY***");
	}
	
	// FORMAT: PGHST TOTALGHOSTS***
	private void handlePGHST(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			if (this.currentParty != null) {
				short totalGhosts = Short.parseShort(args[1].substring(0, args[1].indexOf("***")));
				if (this.currentPlayer != this.currentParty.owner){
					throw new InvalidPermission(this.currentPlayer.playerId);
				}
				this.currentParty.totalGhosts = Math.min(this.currentParty.emptySpaces()/4, totalGhosts);
				this.currentParty.generateGhosts();
				this.write("PALLW***");
				return;
			} else {
				this.write("DUNNO***");
			}
		} 
		catch (InvalidPermission exception) {
			System.out.printf("PLAYER ID %s IS NOT THE OWNER\n\n", exception.playerId);
		}
		this.write("PDENY***");
	}

	// FORMAT: UNREG***
	private void handleUNREG(String command) throws IOException {
		if (command.substring(5).equals("***")) {
			if (this.currentPlayer != null) {
				this.currentParty.unregisterPlayer(this.currentPlayer);
				System.out.printf("VALID REQUEST FROM %s:%d\n\tTYPE: UNREG\n\tPLAYER ID: %s\n\n", this.socket.getInetAddress(), this.socket.getPort(), this.currentPlayer.playerId);
				this.output.write("UNROK ".getBytes());
				this.output.write(this.currentParty.partyId & 0xff);
				this.output.write("***".getBytes());
				// this.output.write(GhostLab.generateMessage("UNROK ", this.currentParty.partyId, "***"));
				// this.write("UNROK %d***", this.currentParty.partyId);
				this.currentPlayer = null;
				this.currentParty = null;
				return;
			}
			this.write("DUNNO***");
		} else {
			System.out.printf("INVALID REQUEST\n\n");
		}
	}

	// FORMAT: SIZE? PARTY_ID***
	private void handleSIZEQ(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			short partyId = Short.parseShort(args[1].substring(0, args[1].indexOf("***")));
			Party party = this.lab.isValidParty(partyId);
			if (party == null) {
				throw new InvalidPartyIdException(partyId);
			}
			System.out.printf("VALID REQUEST FROM %s:%d\n\tTYPE: SIZE?\n\tPARTY ID: %d\n\n", this.socket.getInetAddress(), this.socket.getPort(), partyId);
			int hLE[] = GhostLab.toLittleEndianBytes(party.height);
			int wLE[] = GhostLab.toLittleEndianBytes(party.width);
			this.output.write("SIZE! ".getBytes());
			this.output.write(party.partyId & 0xff);
			this.output.write(" ".getBytes());
			this.output.write(hLE[0] & 0xff);
			this.output.write(hLE[1] & 0xff);
			this.output.write(32 & 0xff);
			this.output.write(wLE[0] & 0xff);
			this.output.write(wLE[1] & 0xff);
			this.output.write("***".getBytes());
			// int height_bytes[] = GhostLab.toLEBytes(party.height);
			// int width_bytes[] = GhostLab.toLEBytes(party.width);
			// this.write("SIZE! %d %d%d %d%d***", party.partyId, height_bytes[1], height_bytes[0], width_bytes[1], width_bytes[0]);
		} catch (NumberFormatException | IndexOutOfBoundsException exception) {
			System.out.printf("INVALID REQUEST\n\n");
		} catch (InvalidPartyIdException exception) {
			System.out.printf("PARTY %d DOESN'T EXIST\n\n", exception.partyId);
			this.write("DUNNO***");
		}
	}

	// FORMAT: LIST? PARTY_ID***
	private void handleLISTQ(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			short partyId = Short.parseShort(args[1].substring(0, args[1].indexOf("***")));
			Party party = this.lab.isValidParty(partyId);
			if (party == null) {
				throw new InvalidPartyIdException(partyId);
			}
			System.out.printf("VALID REQUEST FROM %s:%d\n\tTYPE: LIST?\n\tPARTY ID: %d\n\n", this.socket.getInetAddress(), this.socket.getPort(), partyId);
			this.output.write("LIST! ".getBytes());
			this.output.write(party.partyId & 0xff);
			this.output.write(32 & 0xff);
			this.output.write(party.players.size() & 0xff);
			this.output.write("***".getBytes());
			// this.write("LIST! %d %d***", party.partyId, party.players.size());
			for (Player player : party.players) {
				this.write("PLAYR %s***", player.playerId);
			}
		} catch (NumberFormatException | IndexOutOfBoundsException exception) {
			System.out.printf("INVALID REQUEST\n\n");
		} catch (InvalidPartyIdException exception) {
			System.out.printf("PARTY %d DOESN'T EXIST\n\n", exception.partyId);
			this.write("DUNNO***");
		}
	}

	// FORMAT: GAME?***
	private void handleGAMEQ(String command) throws IOException {
		if (command.substring(5).equals("***")) {
			this.sendGamesInfo();
		} else {
			System.out.printf("INVALID REQUEST\n\n");
		}
	}

	// FORMAT: START***
	private void handleSTART(String command) throws IOException {
		if (command.substring(5).equals("***")) {
			if (this.currentPlayer == null) {
				this.write("NOPE!***");
			} else {
				System.out.printf("PLAYER %s IS REGISTERED IN PARTY %d\n\n", this.currentPlayer.playerId, this.currentParty.partyId);
				this.currentParty.togglePlayer(this.currentPlayer, true);				
				this.waitingForGame = true;
			}
		} else {
			System.out.printf("INVALID REQUEST\n\n");
		}
	}

	// Handle player movement - delegates to Party
	private void handleMoves(String command, Direction direction) throws IOException {
		String[] args = command.split(" ");
		try {
			short distance = Short.parseShort(args[1].substring(0, args[1].indexOf("***")));
			this.currentParty.movePlayer(this.currentPlayer, direction, distance);
		} catch (NumberFormatException | IndexOutOfBoundsException exception) {
			System.out.printf("INVALID REQUEST\n\n");
		}
	}

	// FORMAT: GLIS?***
	private void handleGLISQ(String command) throws IOException {
		if (command.substring(5).equals("***")) {
			this.write("GLIS! %d***", this.currentParty.players.size());
			for (Player player : this.currentParty.players) {
				this.write("GPLYR %s %03d %03d %04d***", player.playerId, player.x, player.y, player.points);
			}
		} else {
			System.out.printf("INVALID REQUEST\n\n");
		}
	}

	// FORMAT: MALL? MESSAGE***
	private void handleMALLQ(String command) throws IOException {
		String message = command.substring(6, command.indexOf("***"));
		if (!message.contains("***") && !message.contains("+++") && message.length() < 200) {
			this.currentParty.multicast("MESSA " + this.currentPlayer.playerId + " " + message + "+++");
			this.write("MALL!***");	
		}
	}

	// FORMAT: IQUIT***
	private void handleIQUIT(String command) throws IOException {
		if (command.substring(5).equals("***")) {
			this.write("GOBYE***");
			this.cleanUp(false);
		} else {
			System.out.printf("INVALID REQUEST\n\n");
		}
	}

	// FORMAT: SEND? PLAYER_ID MESSAGE***
	private void handleSENDQ(String command) throws IOException {
		String[] args = command.split(" ");
		try {
			String playerId = args[1];
			Player targetPlayer = null;
			for (Player currentPlayer : this.currentParty.players) {
				if (currentPlayer.playerId.equals(playerId))  {
					targetPlayer = currentPlayer;
				}
			}
			if (targetPlayer == null) {
				throw new InvalidPlayerIdException(playerId);
			}
			String message = command.substring(15, command.indexOf("***", 15));
			if (GhostLab.VERBOSE) System.out.printf("MESSAGE: %s\n", message);
			if (!message.contains("***") && !message.contains("+++") && message.length() < 200) {
				DatagramSocket datagramSocket = new DatagramSocket();
				byte data[] = GhostLab.formatToBytes("MESSP %s %s+++", this.currentPlayer.playerId, message);
				InetSocketAddress address = new InetSocketAddress("255.255.255.255", targetPlayer.udpPort);
				DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address);
				datagramSocket.send(datagramPacket);
				datagramSocket.close();
				this.write("SEND!***");
				return;
			}
		} catch (NumberFormatException | IndexOutOfBoundsException exception) {
			System.out.printf("INVALID REQUEST\n\n");
		} catch (InvalidPlayerIdException exception) {
			System.out.printf("INVALID PLAYER ID: %s\n\n", exception.playerId);
		}
		this.write("NSEND***");
	}

	private void handleHELPQ(String command) throws IOException {
		if (command.substring(5).equals("***")) {
			this.sendHelp();
		} else {
			System.out.printf("INVALID REQUEST\n\n");
		}
	}

	private void sendGamesInfo() throws IOException {
		this.output.write("GAMES ".getBytes());
		this.output.write(this.lab.getOnStandbyParties() & 0xff);
		this.output.write("***".getBytes());
		if (this.lab.getOnStandbyParties() > 0) {
			for (Party party : lab.getParties()) {
				this.output.write("OGAME ".getBytes());
				this.output.write(party.partyId & 0xff);
				this.output.write(" ".getBytes());
				this.output.write(party.players.size() & 0xff);
				this.output.write("***".getBytes());
			}
		}
	}

	private void sendHelp() throws IOException {
		System.out.printf("SENDING HELP\n");
		if (!this.waitingForGame && !this.inGame) {
			this.write("CURRENTLY AVAILABLE COMMANDS:\n\t%s\n\t%s\n\t%s\n\t%s\n\t%s\n\t%s\n\t%s\n\t%sALL COMMANDS MUST END WITH ***",
			"NEWPL [PLAYER_ID] [UDP_PORT]\n\t\tCREATE A NEW PARTY\n",
			"REGIS [PLAYER_ID] [UDP_PORT] [PARTY_ID]\n\t\tJOIN AN EXISTING PARTY\n",
			"UNREG\n\t\tUNREGISTER FROM A PARTY\n",
			"SIZE? [PARTY_ID]\n\t\tGET PARTY'S MAZE DIMENSIONS\n",
			"LIST? [PARTY_ID]\n\t\tGET PARTY'S PLAYERS LIST\n",
			"GAME?\n\t\tSEE THE NUMBER OF ON STANDBY PARTIES\n",
			"START\n\t\tVOTE TO START THE GAME\n",
			"HELP?\n\t\tDISPLAY THIS HELP MESSAGE\n");
		} 
		if (this.waitingForGame) {
			this.write("NO COMMANDS AVAILABLE WHILE WAITING FOR GAME TO START\nALL COMMANDS MUST END WITH ***");
		} 
		if (this.inGame) {
			this.write("CURRENTLY AVAILABLE COMMANDS:\n\t%s\n\t%s\n\t%s\n\t%s\n\t%s\n\t%s\n\t%sALL COMMANDS MUST END WITH ***",
			"UPMOV/DOMOV/RIMOV/LEMOV [AMOUNT]\n\t\tMOVE YOUR CHARACTER IN THE ACCORDING DIRECTION\n",
			"REGIS [PLAYER_ID] [UDP_PORT] [PARTY_ID]\n\t\tJOIN AN EXISTING PARTY\n",
			"GLIS?\n\t\tGET CURRENT PARTY'S PLAYERS INFO\n",
			"MALL? [MESSAGE]\n\t\tSEND A MESSAGE TO ALL PLAYERS IN THE CURRENT PARTY\n",
			"IQUIT\n\t\tLEAVE THE GAME\n",
			"SEND? [PLAYER_ID] [MESSAGE]\n\t\tSEND A PRIVATE MESSAGE TO A SPECIFIC PLAYER\n",
			"HELP?\n\t\tDISPLAY THIS HELP MESSAGE\n");
		}
	}

	public void joinGame() {
		System.out.printf("ENTERING GAME FOR %s\n", this.currentPlayer.playerId);
		this.waitingForGame = false;
		this.inGame = true;
	}

	public void write(String format, Object... args) throws IOException {
		this.output.write(GhostLab.formatToBytes(format, args));
	}

	public void close() throws IOException {
		this.socket.close();
	}
}
