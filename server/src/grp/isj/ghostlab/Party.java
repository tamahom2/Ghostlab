package grp.isj.ghostlab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import grp.isj.ghostlab.Ghost.GhostType;

/**
 * Stores party data.
 */
public class Party extends Thread {
	public static final Set<String> TAKEN_MULTICAST_ADDRESSES = new HashSet<String>();
	
	// Size of labyrinth
	protected int width, height;
	protected int totalGhosts = 10 & 0xff;
	// Set of 2D points
	protected List<Ghost> ghosts;
	protected volatile boolean keepGoing;
	protected Player owner = null;
	protected int partySize = -1;
	protected int partyId;

	// Players' list
	protected List<Player> players;
	private Map<Player, Boolean> confirmations;

	// Labyrinth as tile enum
	private Tile[][] maze;

	// Messages multicasting
	private String multicastAddress;
	private int multicastPort;
	private MulticastSocket multicastSocket;

	// Random instance
	protected Random random;

	public Party(int id) {
		this.partyId = id;
		this.players = new ArrayList<Player>();
		this.confirmations = new HashMap<Player, Boolean>();
		this.random = new Random();
		this.generateMaze();
	}

	private void generateMaze() {
		System.out.printf("GENERATING MAZE...\n");
		Maze maze = new Maze(this.random);
		this.height = maze.height;
		this.width = maze.width;
		int[][] grid = maze.grid;
		this.maze = new Tile[height][width];
		for (int y = 0; y < this.height; y++) {
			for (int x = 0; x < this.width; x++) {
				this.maze[y][x] = (grid[y][x] == 1) ? Tile.EMPTY : Tile.WALL;
			}
		}
		System.out.printf("H: %d, W: %d\n", this.height, this.width);
		// this.printLabyrinth(true);
	}

	protected void generateMaze(int height,int width) {
		System.out.printf("GENERATING MAZE...\n");
		Maze maze = new Maze(Math.min(Math.max(16, height), 999), Math.min(Math.max(16, width), 999));
		this.height = maze.height;
		this.width = maze.width;
		int[][] grid = maze.grid;
		System.out.printf("HEIGTH %d WIDTH %d\n",this.height,this.width);
		this.maze = new Tile[this.height][this.width];
		for (int y = 0; y < this.height; y++) {
			for (int x = 0; x < this.width; x++) {
				this.maze[y][x] = (grid[y][x] == 1) ? Tile.EMPTY : Tile.WALL;
			}
		}
		System.out.printf("H: %d, W: %d\n", this.height, this.width);
		// this.printLabyrinth(true);
	}

	protected void generateGhosts(){
		this.ghosts = new ArrayList<Ghost>();
		for (int i = 0; i < this.totalGhosts; i++) {
			int ghostX = this.random.nextInt(this.width);
			int ghostY = this.random.nextInt(this.height);
			boolean spotOccupied = false;
			while (this.maze[ghostY][ghostX] == Tile.WALL || spotOccupied) {
				for (Ghost distinctGhost : this.ghosts) {
					if (distinctGhost.x == ghostX && distinctGhost.y == ghostY) {
						if (GhostLab.VERBOSE) System.out.printf("GHOST ALREADY ON y=%d x=%d\n", ghostY, ghostX);
						spotOccupied = true;
					}
					for (Player player : this.players) {
						if (player.x == ghostX && player.y == ghostY) {
							if (GhostLab.VERBOSE) System.out.printf("PLAYER ALREADY ON y=%d x=%d\n", player.y, player.x);
							spotOccupied = true;
						}
					}
				}
				ghostY = this.random.nextInt(this.height);
				ghostX = this.random.nextInt(this.width);
			}
			if (GhostLab.VERBOSE) System.out.printf("GHOST INITIALLY PLACED ON y=%d x=%d\n", ghostY, ghostX);
			if (i == this.emptySpaces() / 4) {
				this.ghosts.add(new Ghost(ghostX, ghostY, GhostType.KING));
			} else if (this.random.nextFloat() > 0.8) {
				this.ghosts.add(new Ghost(ghostX, ghostY, GhostType.GOLDEN));
			} else {
				this.ghosts.add(new Ghost(ghostX, ghostY, GhostType.REGULAR));
			}
		}
		System.out.printf("SPAWNED %d GHOSTS\n", this.totalGhosts);
	}

	@Deprecated
	public void loadFromFile(String path) {
		System.out.printf("LOADING LABYRINTH FROM %s\n", path);
		File file = new File(path);
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line = reader.readLine();
			String[] dimensions = line.split(" ");
			this.height = Integer.parseInt(dimensions[0]);
			this.width = Integer.parseInt(dimensions[1]);
			System.out.printf("DIMENSIONS:\n\tHEIGHT: %d\n\tWIDTH: %d\n", this.height, this.width);
			this.maze = new Tile[height][width];
			for (int y = 0; y < this.height; y++) {
				line = reader.readLine();
				String[] splits = line.split(" ");
				for (int x = 0; x < this.width; x++) {
					this.maze[y][x] = Integer.valueOf(splits[x]) == 0 ? Tile.EMPTY : Tile.WALL;
				}
			}
			reader.close();
		} catch (IOException exception) {
			exception.printStackTrace();
			System.exit(-1);
		} catch (NumberFormatException | IndexOutOfBoundsException exception) {
			exception.printStackTrace();
		}
	}

	public void printLabyrinth(boolean firstTime) {
		for (int y = 0; y < this.height; y++) {
			for (int x = 0; x < this.width; x++) {
				boolean shouldBreak = false;
				if (!firstTime) {
					synchronized (this.ghosts) {
						for (Ghost ghost : this.ghosts) {
							if (ghost.x == x && ghost.y == y) {
								switch (ghost.type) {
									case REGULAR:
										System.out.print("F\t");
										break;
									case GOLDEN:
										System.out.print("G\t");
										break;
									case KING:
										System.out.print("K\t");
										break;
								}
								shouldBreak = true;
							}
						}
					}
					synchronized (this.players) {
						for (Player player : this.players) {
							if (player.x == x && player.y == y) {
								System.out.print("P\t");
								shouldBreak = true;
							}
						}
					}
				}
				if (!shouldBreak) System.out.print((this.maze[y][x] == Tile.EMPTY ? " " : "|") + "\t");				
			}
			System.out.println();
		}
		System.out.println();
	}

	protected short emptySpaces() {
		short empty = 0;
		for (int y = 0; y < this.height; y++) {
			for (int x = 0; x < this.width; x++) {
				if (this.maze[y][x] == Tile.EMPTY) empty++;
			}
		}
		return empty;
	}

	@Override
	public void run() {
		String multicastAddress = this.generateRandomMulticastAddress();
		int port = 9999;
		for (Player player : this.players) {
			int playerX = this.random.nextInt(this.width);
			int playerY = this.random.nextInt(this.height);
			boolean spotOccupied = false;
			while (this.maze[playerY][playerX] == Tile.WALL || spotOccupied)  {
				for (Player distinctPlayer : this.players) {
					if (distinctPlayer == player) {
						continue;
					}
					if (distinctPlayer.x == playerX && distinctPlayer.y == playerY) {
						if (GhostLab.VERBOSE) System.out.printf("PLAYER ALREADY ON y=%d x=%d\n", distinctPlayer.y, distinctPlayer.x);
						spotOccupied = true;
					}
				}
				playerY = this.random.nextInt(this.height);
				playerX = this.random.nextInt(this.width);
			}
			player.x = playerX;
			player.y = playerY;
			if (GhostLab.VERBOSE) System.out.printf("PLAYER INITIALLY PLACED ON y=%d x=%d\n", playerY, playerX);
			try {
				String multicastFormatted = String.format("%-15s", multicastAddress).replace(' ', '#');
				int hLE[] = GhostLab.toLittleEndianBytes(this.height);
				int wLE[] = GhostLab.toLittleEndianBytes(this.width);
				player.connection.output.write("WELCO ".getBytes());
				player.connection.output.write(this.partyId & 0xff);
				player.connection.output.write(" ".getBytes());
				player.connection.output.write(hLE[0] & 0xff);
				player.connection.output.write(hLE[1] & 0xff);
				player.connection.output.write(32 & 0xff);
				player.connection.output.write(wLE[0] & 0xff);
				player.connection.output.write(wLE[1] & 0xff);
				player.connection.output.write(" ".getBytes());
				player.connection.output.write(totalGhosts & 0xff);
				player.connection.output.write(" ".getBytes());
				player.connection.output.write(multicastFormatted.getBytes());
				player.connection.output.write(" ".getBytes());
				player.connection.output.write(String.valueOf(port).getBytes());
				player.connection.output.write("***".getBytes());
				// player.connection.writeSpecial("WELCO ", this.partyId, " ", (hLE[0] & 0xff), (hLE[1] & 0xff), " ", (wLE[0] & 0xff), (wLE[1] & 0xff), " ", (totalGhosts & 0xff), " ", multicastFormatted, " ", String.valueOf(port), "***");
				player.connection.write("POSIT %s %03d %03d***", player.playerId, player.x, player.y);
				player.connection.joinGame();
			} catch (IOException exception) {
				try {
					this.unregisterPlayer(player);
				} catch (IOException ioException) {
					ioException.printStackTrace();
				}
			}
		}
		this.multicastAddress = multicastAddress;
		this.multicastPort = port;
		try {
			this.multicastSocket = new MulticastSocket();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
		this.ghosts = new ArrayList<Ghost>();
		for (int i = 0; i < totalGhosts; i++) {
			int ghostX = this.random.nextInt(this.width);
			int ghostY = this.random.nextInt(this.height);
			boolean spotOccupied = false;
			while (this.maze[ghostY][ghostX] == Tile.WALL || spotOccupied) {
				for (Ghost distinctGhost : this.ghosts) {
					if (distinctGhost.x == ghostX && distinctGhost.y == ghostY) {
						if (GhostLab.VERBOSE) System.out.printf("GHOST ALREADY ON y=%d x=%d\n", ghostY, ghostX);
						spotOccupied = true;
					}
					for (Player player : this.players) {
						if (player.x == ghostX && player.y == ghostY) {
							if (GhostLab.VERBOSE) System.out.printf("PLAYER ALREADY ON y=%d x=%d\n", player.y, player.x);
							spotOccupied = true;
						}
					}
				}
				ghostY = this.random.nextInt(this.height);
				ghostX = this.random.nextInt(this.width);
			}
			if (GhostLab.VERBOSE) System.out.printf("GHOST INITIALLY PLACED ON y=%d x=%d\n", ghostY, ghostX);
			if (i == this.emptySpaces() / 4) {
				this.ghosts.add(new Ghost(ghostX, ghostY, GhostType.KING));
			} else if (this.random.nextFloat() > 0.8) {
				this.ghosts.add(new Ghost(ghostX, ghostY, GhostType.GOLDEN));
			} else {
				this.ghosts.add(new Ghost(ghostX, ghostY, GhostType.REGULAR));
			}
		}
		System.out.printf("SPAWNED %d GHOSTS\n", totalGhosts);
		this.keepGoing = true;
		while (this.keepGoing) {
			try {
				if (this.players.isEmpty()) {
					if (GhostLab.VERBOSE) System.out.printf("ALL PLAYERS DISCONNECTED\n");
					this.keepGoing = false;
					break;
				}
				this.moveGhosts();
				// this.printLabyrinth(false);
			} catch (IOException ioException) {
				System.err.printf("AN EXCEPTION OCCURRED WHILE MOVING GHOSTS\n");
				ioException.printStackTrace();
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException interruptedException) {
				continue;
			}
		}
		this.multicastSocket.close();
		Party.TAKEN_MULTICAST_ADDRESSES.remove(this.multicastAddress);
	}

	private synchronized void moveGhosts() throws IOException {
		for (Ghost ghost : this.ghosts) {
			switch (ghost.type) {
				case REGULAR:
				case GOLDEN:
					int dy = 0, dx = 0;
					boolean validCoords = false;
					while (!validCoords) {
						dx = -ghost.type.dMove + this.random.nextInt(2 * ghost.type.dMove + 1);
						dy = -ghost.type.dMove + this.random.nextInt(2 * ghost.type.dMove + 1);
						validCoords = !this.coordsInvalid(ghost, ghost.y + dy, ghost.x + dx);
					}
					ghost.y += dy;
					ghost.x += dx;
					if (GhostLab.VERBOSE) System.out.printf("GHOST MOVED TO y=%d x=%d\n", ghost.y, ghost.x);
					break;
				case KING:
					int newY = 0, newX = 0;
					validCoords = false;
					while (!validCoords) {
						newY = this.random.nextInt(this.height);
						newX = this.random.nextInt(this.width);
						validCoords = !this.coordsInvalid(ghost, newY, newX);
					}
					ghost.y = newY;
					ghost.x = newX;
					if (GhostLab.VERBOSE) System.out.printf("KING GHOST MOVED ON y=%d x=%d\n\n", ghost.y, ghost.x);
					break;
			}
			this.multicast("GHOST %03d %03d+++", ghost.x, ghost.y);
		}
	}

	public boolean coordsInvalid(Ghost ghost, int y, int x) {
		if (x < 0 || x >= this.width || y < 0 || y >= this.height) return true;
		if (this.maze[y][x] == Tile.WALL) {
			return true;
		}
		for (Ghost distinctGhost : this.ghosts) {
			if (distinctGhost == ghost) {
				continue;
			}
			if (distinctGhost.y == y && distinctGhost.x == x) {
				if (GhostLab.VERBOSE) System.out.printf("GHOST ALREADY ON y=%d x=%d\n", distinctGhost.y, distinctGhost.x);
				return true;
			}
			for (Player player : this.players) {
				if (player.x == x && player.y == y) {
					if (GhostLab.VERBOSE) System.out.printf("PLAYER ALREADY ON y=%d x=%d\n", player.y, player.x);
					return true;
				}
			}
		}
		return false;
	}

	public void multicast(String format, Object... args) throws IOException {
		byte data[] = String.format(format, args).getBytes();
		InetSocketAddress socketAddress = new InetSocketAddress(this.multicastAddress, this.multicastPort);
		DatagramPacket packet = new DatagramPacket(data, data.length, socketAddress);
		this.multicastSocket.send(packet);
	}

	public String generateRandomMulticastAddress() {
		int b1 = 224 + this.random.nextInt(16), b2 = this.random.nextInt(256), b3 = this.random.nextInt(256), b4 = this.random.nextInt(256);
		String address = String.format("%d.%d.%d.%d", b1, b2, b3, b4);
		while (Party.TAKEN_MULTICAST_ADDRESSES.contains(address)) {
			address = this.generateRandomMulticastAddress();
		}
		Party.TAKEN_MULTICAST_ADDRESSES.add(address);
		return address;
	}

	public boolean isPlayerRegistered(Player player) {
		return this.confirmations.keySet().contains(player);
	}

	public void registerPlayer(Player player) {
		this.players.add(player);
		this.confirmations.put(player, false);
	}

	public void unregisterPlayer(Player player) throws IOException {
		this.players.remove(player);
		this.confirmations.remove(player);
		Player.TAKEN_UDP_PORTS.remove(player.udpPort);
		if (this.keepGoing && this.players.size() == 1) {
			System.out.printf("\nONLY 1 PLAYER LEFT, ENDING GAME\n");
			this.closeParty();
		}
	}

	public void togglePlayer(Player player, boolean b) {
		this.confirmations.put(player, b);
		boolean allReady = true;
		for (Player key : this.confirmations.keySet()) {
			allReady &= this.confirmations.get(key);
		}
		if (allReady && this.confirmations.keySet().size() > 1) {
			this.start();
		}
	}

	public void movePlayer(Player player, Direction dir, short distance) throws IOException {
		List<Ghost> ghostsMet = new ArrayList<Ghost>();
		int x = player.x;
		int y = player.y;
		int ddir = 0;
		switch (dir) {
			case UP:
				if (distance == 1 && this.maze[y - 1][x] != Tile.WALL) {
					player.y = y - 1;
					ghostsMet.addAll(this.checkGhosts(x, y - 1));
				} else {
					while (ddir <= distance && y >= 0 && this.maze[y][x] != Tile.WALL) {
						ghostsMet.addAll(this.checkGhosts(x, y));
						y--;
						ddir++;
					}
					player.y = y + 1;
				}
				break;
			case DOWN:
				if (distance == 1 && this.maze[y + 1][x] != Tile.WALL) {
					player.y = y + 1;
					ghostsMet.addAll(this.checkGhosts(x, y + 1));
				} else {
					while (ddir <= distance && y < this.height && this.maze[y][x] != Tile.WALL) {
						ghostsMet.addAll(this.checkGhosts(x, y));
						y++;
						ddir++;
					}
					player.y = y - 1;
				}
				break;
			case LEFT:
				if (distance == 1 && this.maze[y][x - 1] != Tile.WALL) {
					player.x = x - 1;
					ghostsMet.addAll(this.checkGhosts(x - 1, y));
				} else {
					while (ddir <= distance && x >= 0 && this.maze[y][x] != Tile.WALL) {
						ghostsMet.addAll(this.checkGhosts(x, y));
						x--;
						ddir++;
					}
					player.x = x + 1;
				}
				break;
			case RIGHT:
				if (distance == 1 && this.maze[y][x + 1] != Tile.WALL) {
					player.x = x + 1;
					ghostsMet.addAll(this.checkGhosts(x + 1, y));
				} else {
					while (ddir <= distance && x < this.width && this.maze[y][x] != Tile.WALL) {
						ghostsMet.addAll(this.checkGhosts(x, y));
						x++;
						ddir++;
					}
					player.x = x - 1;
				}
				break;
		}
		this.printLabyrinth(false);
		synchronized (this.ghosts) {
			if (ghostsMet.size() > 0) {
				for (Ghost metGhost : ghostsMet) {
					switch (metGhost.type) {
						case REGULAR:
							player.points += 1;
							break;
						case GOLDEN:
							player.points += 5;
							break;
						case KING:
							player.points += 15;
							break;
					}
				}
				player.connection.write("MOVEF %03d %03d %04d***", player.x, player.y, player.points);
				for (Ghost ghostMet : ghostsMet) this.multicast("SCORE %s %04d %03d %03d+++", player.playerId, player.points, ghostMet.x, ghostMet.y);
			} else {
				player.connection.write("MOVE! %03d %03d***", player.x, player.y);
			}
			synchronized (this.ghosts) {
				if (GhostLab.VERBOSE) System.out.printf("REMAINING GHOSTS %d\n", this.ghosts.size());
				if (this.ghosts.size() == 0) {
					this.closeParty();
				}
			}
		}
	}

	private synchronized void closeParty() throws IOException {
		int maxScore = -1;
		List<Player> topPlayers = new ArrayList<Player>();
		for (Player currentPlayer : this.players) {
			if (currentPlayer.points > maxScore) {
				topPlayers.clear();
				maxScore = currentPlayer.points;
				topPlayers.add(currentPlayer);
			} else if (currentPlayer.points == maxScore) {
				topPlayers.add(currentPlayer);
			}
		}
		if (topPlayers.size() == 1) {
			this.multicast("ENDGA %s %04d+++", topPlayers.get(0).playerId, topPlayers.get(0).points);
		} else {
			int randomPlayer = new Random().nextInt(topPlayers.size());
			this.multicast("ENDGA %s %04d+++", topPlayers.get(randomPlayer).playerId, topPlayers.get(randomPlayer).points);
		}
		for (Player player : this.players) {
			player.connection.inGame = false;
			player.connection.waitingForGame = false;
		}
		this.keepGoing = false;
		this.players.get(0).connection.lab.getParties().remove(this);
		this.players.clear();
	}

	private synchronized List<Ghost> checkGhosts(int x, int y) {
		List<Ghost> ghostsMet = new ArrayList<Ghost>();
		for (Ghost ghost : ((Ghost[]) this.ghosts.toArray(new Ghost[this.ghosts.size()]))) {
			if (ghost.x == x && ghost.y == y) {
				ghostsMet.add(ghost);
				this.ghosts.remove(ghost);
			}
		}
		return ghostsMet;
	}
}
