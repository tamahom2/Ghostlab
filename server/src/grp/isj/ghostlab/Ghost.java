package grp.isj.ghostlab;

public class Ghost {
	static enum GhostType {
		REGULAR(2), KING(-1), GOLDEN(4);

		// How far a ghost can move in a single displacement
		protected int dMove;

		GhostType(int dMove) {
			this.dMove = dMove;
		}
	}

	protected int x, y; 
	protected GhostType type;

	public Ghost(int x, int y, GhostType type) {
		this.x = x;
		this.y = y;
		this.type = type;
	}
}
