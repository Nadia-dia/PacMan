package application;


import java.util.*;

public class Ghost implements Runnable {
    private volatile int x, y;
    private final int mapWidth;
    private final int mapHeight;
    private final int[][] map;
    private final Player player1;
    private final Player player2;
    private volatile boolean running = true;

    private int lastDx = 0;
    private int lastDy = 0;

    private int previousCellValue = 0;

    public Ghost(int startX, int startY, int[][] map, Player player1, Player player2) {
        this.x = startX;
        this.y = startY;
        this.map = map;
        this.mapHeight = map.length;
        this.mapWidth = map[0].length;
        this.player1 = player1;
        this.player2 = player2;

        this.previousCellValue = map[startY][startX];
        map[startY][startX] = 3; // Ustaw ducha na mapie
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }

    public void stop() {
        running = false;
        //Thread.currentThread().interrupt();
    }

    @Override
    public void run() {
        try {
            while (running) {
                moveTowardsClosestPlayer();
                checkCollisionWithPlayers();
                Thread.sleep(1200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void checkCollisionWithPlayers() {
        if (player1.getX() == this.x && player1.getY() == this.y) {
            player1.setScore(0);
            player1.setX(1);
            player1.setY(1);
            System.out.println(player1.getName() + " został złapany przez ducha! Wynik zerowany.");
            Server.endGame("CAUGHT_BY_GHOST");
        }

        if (player2.getX() == this.x && player2.getY() == this.y) {
            player2.setScore(0);
            player2.setX(23);
            player2.setY(1);
            System.out.println(player2.getName() + " został złapany przez ducha! Wynik zerowany.");
            Server.endGame("CAUGHT_BY_GHOST");
        }
    }

    private void moveTowardsClosestPlayer() {
        Player target = closestPlayer();
        int targetX = target.getX();
        int targetY = target.getY();

        int[] nextStep = findNextStepTowards(targetX, targetY);

        if (nextStep != null) {
            int newX = nextStep[0];
            int newY = nextStep[1];
            synchronized (map) {
                if (canMoveTo(newX, newY)) {
                    map[y][x] = previousCellValue; // przywróć starą wartość
                    previousCellValue = map[newY][newX]; // zapamiętaj nową
                    map[newY][newX] = 3;

                    lastDx = newX - x;
                    lastDy = newY - y;
                    x = newX;
                    y = newY;
                }
            }
        } else {
            randomMoveAvoidBack();
        }
    }

    private Player closestPlayer() {
        int dist1 = manhattanDistance(x, y, player1.getX(), player1.getY());
        int dist2 = manhattanDistance(x, y, player2.getX(), player2.getY());
        if (dist1 == dist2) return Math.random() < 0.5 ? player1 : player2;
        return Math.random() < 0.8 ? (dist1 < dist2 ? player1 : player2) : (dist1 < dist2 ? player2 : player1);
    }

    private int manhattanDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private boolean canMoveTo(int newX, int newY) {
        if (newX < 0 || newX >= mapWidth || newY < 0 || newY >= mapHeight) return false;
        int cell = map[newY][newX];
        return cell == 0 || cell == 2;
    }

    private int[] findNextStepTowards(int targetX, int targetY) {
        boolean[][] visited = new boolean[mapHeight][mapWidth];
        int[][] prev = new int[mapHeight * mapWidth][];
        Arrays.fill(prev, null);

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{x, y});
        visited[y][x] = true;

        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0], cy = current[1];
            if (cx == targetX && cy == targetY) {
                List<int[]> path = new ArrayList<>();
                while (!(cx == x && cy == y)) {
                    path.add(new int[]{cx, cy});
                    int idx = cy * mapWidth + cx;
                    int[] p = prev[idx];
                    if (p == null) break;
                    cx = p[0];
                    cy = p[1];
                }
                Collections.reverse(path);
                return path.isEmpty() ? null : path.get(0);
            }

            for (int[] d : directions) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight &&
                        !visited[ny][nx] && canMoveTo(nx, ny)) {
                    visited[ny][nx] = true;
                    queue.add(new int[]{nx, ny});
                    prev[ny * mapWidth + nx] = new int[]{cx, cy};
                }
            }
        }
        return null;
    }

    private void randomMoveAvoidBack() {
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
        List<int[]> possibleMoves = new ArrayList<>();

        for (int[] d : directions) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (!(d[0] == -lastDx && d[1] == -lastDy) && canMoveTo(nx, ny)) {
                possibleMoves.add(new int[]{nx, ny});
            }
        }

        if (!possibleMoves.isEmpty()) {
            Collections.shuffle(possibleMoves);
            int[] move = possibleMoves.get(0);
            synchronized (map) {
                map[y][x] = previousCellValue;
                previousCellValue = map[move[1]][move[0]];
                map[move[1]][move[0]] = 3;
                lastDx = move[0] - x;
                lastDy = move[1] - y;
                x = move[0];
                y = move[1];
            }
        }
    }
}

