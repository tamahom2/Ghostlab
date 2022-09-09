package grp.isj.ghostlab;

import java.util.ArrayList;
import java.util.Stack;
import java.util.Random;

public class Maze {
    private final int min = 16;
    private final int max = 128;

    private Stack<Point> stack;

    protected int[][] grid;
    protected int height, width;

    public Maze(Random random) {
        this.stack = new Stack<Point>();
        this.height = random.nextInt(max - min) + min;
        this.width = random.nextInt(max - min) + min;
        this.grid = new int[this.height][this.width];
        fillMaze(random);
    }

    public Maze(int height, int width) {
        Random random = new Random();
        this.stack = new Stack<Point>();
        this.height = height;
        this.width = width;
        this.grid = new int[this.height][this.width];
        fillMaze(random);
    }

    // A BFS to generate our maze 
    public void fillMaze(Random random) {
        stack.push(new Point(0, 0));
        while (!stack.empty()){
            Point next = stack.pop();
            if (this.isValidPoint(next)) {
                grid[next.y][next.x] = 1;
                ArrayList<Point> neighbors = this.findNeighbors(next);
                this.addPointsRandomly(neighbors, random);
            }
        }
    }

    public boolean isValidPoint(Point point) {
        int neighbors = 0;
        for (int y = point.y - 1; y < point.y + 2; y++) {
            for (int x = point.x - 1; x < point.x + 2; x++) {
                if (x == point.x && y == point.y) {
                    continue;
                }
                if (inGrid(x, y) && grid[y][x] == 1) {
                    neighbors++;
                }
            }
        }
        return (neighbors < 3) && grid[point.y][point.x] != 1;
    }

    public void addPointsRandomly(ArrayList<Point> points, Random rand) {
        int target;
        while (!points.isEmpty()) {
            target = rand.nextInt(points.size());
            stack.push(points.remove(target));
        }
    }

    public ArrayList<Point> findNeighbors(Point point) {
        ArrayList<Point> neighbors = new ArrayList<Point>();
        for (int y = point.y - 1; y < point.y + 2; y++) {
            for (int x = point.x - 1; x < point.x + 2; x++) {
                if (x == point.x && y == point.y) {
                    continue;
                }
                if (!(x == point.x || y == point.y)){
                    continue;
                }
                if (inGrid(x, y)) {
                    neighbors.add(new Point(x,y));
                }
            }
        }
        return neighbors;
    }

    public boolean inGrid(int x, int y) {
        return (x < this.width) && (x >= 0) && (y < this.height) && (y >= 0);
    }
}