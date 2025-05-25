import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Server {
    private static final int PORT = 12343;
    private static final int MAP_WIDTH = 25;
    private static final int MAP_HEIGHT = 18;
    private static final int[][] MAP = GameMap.MAP;

    private static ServerSocket serverSocket;

    private static int[][] sharedMap = new int[MAP_HEIGHT][MAP_WIDTH];
    private static List<PlayerHandler> players = new ArrayList<>();

    private static Ghost ghost1;
    private static Ghost ghost2;
    private static Thread ghostThread1;
    private static Thread ghostThread2;

    static DatabaseManager dbManager = null;

    private static volatile boolean gameOver = false;
    
    static {
        for (int y = 0; y < MAP_HEIGHT; y++) {
            System.arraycopy(MAP[y], 0, sharedMap[y], 0, MAP_WIDTH);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Serwer nasłuchuje na porcie " + PORT);
            
        
            try {
                dbManager = new DatabaseManager();
                System.out.println("Połączono z bazą danych.");
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            // Połączenie obu graczy
            while(players.size() < 2) {
                Socket playerSocket = serverSocket.accept();
                BufferedReader playerIn = new BufferedReader(new InputStreamReader(playerSocket.getInputStream()));
                PrintWriter playerOut = new PrintWriter(playerSocket.getOutputStream(), true);

                Player currentPlayer;
                if(players.size() == 0){
                    currentPlayer = new Player1("Gracz 1", 1, 1);
                    playerOut.println("PLAYER:1");
                } else {
                    currentPlayer = new Player2("Gracz 2", 23, 1);
                    playerOut.println("PLAYER:2");
                }
                players.add(new PlayerHandler(playerSocket, playerIn, playerOut, currentPlayer));
                System.out.println("Połączono Gracza " + players.size());
            }

            // Wątki dla graczy - stworzenie i uruchomienie
            Thread player1Thread = new Thread(players.getFirst());
            Thread player2Thread = new Thread(players.getLast());

            // Znajdź pozycje duchów na mapie (wartość 3)
            int ghost1X = 1, ghost1Y = 16;
            int ghost2X = 23, ghost2Y = 16;

            // Stwórz duchy
            ghost1 = new Ghost(ghost1X, ghost1Y, sharedMap, players.get(0).player, players.get(1).player);
            ghost2 = new Ghost(ghost2X, ghost2Y, sharedMap, players.get(0).player, players.get(1).player);

            // Uruchom wątki duchów
            ghostThread1 = new Thread(ghost1);
            ghostThread2 = new Thread(ghost2);

            ghostThread1.start();
            ghostThread2.start();
            player1Thread.start();
            player2Thread.start();

            // Oczekiwanie, gdzy graczy skoncza
            player1Thread.join();
            player2Thread.join();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                for(PlayerHandler player: players){
                    if(player.playerSocket != null) player.playerSocket.close();
                }
                if (serverSocket != null) serverSocket.close();
                if (dbManager != null) dbManager.close();
                if (ghost1 != null) ghost1.stop();
                if (ghost2 != null) ghost2.stop();
                if (ghostThread1 != null) ghostThread1.join();
                if (ghostThread2 != null) ghostThread2.join();
                
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static synchronized void endGame(String reason) {
        System.out.println("DEBUG: Wywołano endGame z powodem: " + reason);
        if (gameOver) return; // już zakończono

        gameOver = true;

        Player player1 = players.get(0).player;
        Player player2 = players.get(1).player;
        String endMessage = "END:" + reason + ":" + player1.getScore() + ":" + player2.getScore();
        System.out.println(endMessage);
        for(PlayerHandler player: players){
            player.playerOut.println(endMessage);
        }

        System.out.println("Gra zakończona powodem: " + reason);

        try {
            if (dbManager != null) dbManager.saveResult(player1.getName(), player1.getScore(), player2.getName(),
                    player2.getScore());
            for(PlayerHandler player: players){
                if(player.playerSocket != null) player.playerSocket.close();
            }
            if (serverSocket != null) serverSocket.close();
            if (dbManager != null) dbManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //System.exit(0);  // wyłącz serwer lub zatrzymaj wątki
    }

    private static class PlayerHandler implements Runnable {
        private Socket playerSocket;
        private BufferedReader playerIn;
        private PrintWriter playerOut;
        private Player player;


        public PlayerHandler(Socket playerSocket, BufferedReader playerIn, PrintWriter playerOut, Player player) {
            this.playerSocket = playerSocket;
            this.playerIn = playerIn;
            this.playerOut = playerOut;
            this.player = player;
        }

        @Override
        public void run() {
            try {
                String playerMessage;

                while (true) {
                    if (gameOver) break;

                    if (playerIn.ready()) {
                        playerMessage = playerIn.readLine();
                        if (playerMessage != null) {
                            handlePlayerMove(player, playerMessage);
                        }
                    }

                    if (isGameFinished()) {
                        endGame("ALL_POINTS_COLLECTED");
                        break;
                    }

                    String currentState = getGameState();
                    playerOut.println(currentState);
                    Thread.sleep(50);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        private synchronized boolean isGameFinished() {
            for (int y = 0; y < MAP_HEIGHT; y++) {
                for (int x = 0; x < MAP_WIDTH; x++) {
                    if (sharedMap[y][x] == 2) {
                        return false; // punkty jeszcze są na mapie
                    }
                }
            }
            return true;
        }

        private synchronized void handlePlayerMove(Player p, String direction) {
            if (gameOver) return;

            int x = p.getX();
            int y = p.getY();
            int newX = x, newY = y;

            switch (direction) {
                case "UP":    newY--; break;
                case "DOWN":  newY++; break;
                case "LEFT":  newX--; break;
                case "RIGHT": newX++; break;
            }

            if (newX >= 0 && newX < MAP_WIDTH && newY >= 0 && newY < MAP_HEIGHT && MAP[newY][newX] != 1) {
                if (sharedMap[newY][newX] == 2) {
                    sharedMap[newY][newX] = 0;
                    p.setScore(p.getScore() + 1);
                    System.out.println("Wynik " + p.getName() + ": " + p.getScore());
                }

                p.setX(newX);
                p.setY(newY);

                // Sprawdź kolizję z duchami
                if ((ghost1.getX() == newX && ghost1.getY() == newY) ||
                    (ghost2.getX() == newX && ghost2.getY() == newY)) {
                    System.out.println(p.getName() + " został złapany przez ducha! Koniec gry.");
                    endGame("CAUGHT_BY_GHOST");
                }
            }
        }

        private synchronized String getGameState() {
            Player player1 = players.get(0).player;
            Player player2 = players.get(1).player;
            StringBuilder sb = new StringBuilder();
            sb.append("Player1:(").append(player1.getX()).append(",").append(player1.getY()).append(");");
            sb.append("Player2:(").append(player2.getX()).append(",").append(player2.getY()).append(");");
            sb.append("Score1:").append(player1.getScore()).append(";");
            sb.append("Score2:").append(player2.getScore()).append(";");
            sb.append("Ghost1:(").append(ghost1.getX()).append(",").append(ghost1.getY()).append(");");
            sb.append("Ghost2:(").append(ghost2.getX()).append(",").append(ghost2.getY()).append(");");

            for (int y = 0; y < MAP_HEIGHT; y++) {
                for (int x = 0; x < MAP_WIDTH; x++) {
                    if (sharedMap[y][x] == 1) {
                        sb.append("Wall:(").append(x).append(",").append(y).append(");");
                    } else if (sharedMap[y][x] == 2) {
                        sb.append("Point:(").append(x).append(",").append(y).append(");");
                    }
                }
            }
            return sb.toString();
        }
    }

}
