import java.io.*;
import java.net.*;
import java.sql.SQLException;

public class Server {
    private static final int PORT = 12343;
    private static final int MAP_WIDTH = 25;
    private static final int MAP_HEIGHT = 18;
    private static final int[][] MAP = GameMap.MAP;

    private static ServerSocket serverSocket;
    private static Socket player1Socket, player2Socket;
    private static PrintWriter player1Out, player2Out;
    private static BufferedReader player1In, player2In;

    private static Player player1 = new Player1("Gracz 1", 1, 1);
    private static Player player2 = new Player2("Gracz 2", 23, 1);
    private static int[][] sharedMap = new int[MAP_HEIGHT][MAP_WIDTH];
    
    private static Ghost ghost1;
    private static Ghost ghost2;
    private static Thread ghostThread1;
    private static Thread ghostThread2;
    

    
    static DatabaseManager dbManager = null;
    
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

            // Połączenie gracza 1
            player1Socket = serverSocket.accept();
            System.out.println("Połączono Gracza 1");
            player1In = new BufferedReader(new InputStreamReader(player1Socket.getInputStream()));
            player1Out = new PrintWriter(player1Socket.getOutputStream(), true);
            player1Out.println("PLAYER:1");

            // Połączenie gracza 2
            player2Socket = serverSocket.accept();
            System.out.println("Połączono Gracza 2");
            player2In = new BufferedReader(new InputStreamReader(player2Socket.getInputStream()));
            player2Out = new PrintWriter(player2Socket.getOutputStream(), true);
            player2Out.println("PLAYER:2");

            // Wątki dla graczy
            Thread player1Thread = new Thread(new PlayerHandler(player1Socket, player1In, player1Out, player1));
            Thread player2Thread = new Thread(new PlayerHandler(player2Socket, player2In, player2Out, player2));

         // Znajdź pozycje duchów na mapie (wartość 3)
            int ghost1X = 1, ghost1Y = 16;
            int ghost2X = 23, ghost2Y = 16;

            // Stwórz duchy
            ghost1 = new Ghost(ghost1X, ghost1Y, sharedMap, player1, player2);
            ghost2 = new Ghost(ghost2X, ghost2Y, sharedMap, player1, player2);

            // Uruchom wątki duchów
            ghostThread1 = new Thread(ghost1);
            ghostThread2 = new Thread(ghost2);

            ghostThread1.start();
            ghostThread2.start();

            player1Thread.start();
            player2Thread.start();

            player1Thread.join();
            player2Thread.join();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (player1Socket != null) player1Socket.close();
                if (player2Socket != null) player2Socket.close();
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
    
    public static void ghostCaughtPlayer() {
        System.out.println("Koniec gry: duch złapał gracza!");
        // Wypchnij wiadomość do obu graczy i zakończ wszystko (analogicznie jak w endGame)
        String endMessage = "END:CAUGHT_BY_GHOST:" + player1.getScore() + ":" + player2.getScore();
        player1Out.println(endMessage);
        player2Out.println(endMessage);

        try {
            if (player1Socket != null) player1Socket.close();
            if (player2Socket != null) player2Socket.close();
            if (serverSocket != null) serverSocket.close();
            if (dbManager != null) dbManager.saveResult(player1.getName(), player1.getScore(), player2.getName(), player2.getScore());
            if (dbManager != null) dbManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.exit(0);  // wyłącz serwer lub zatrzymaj wątki
    }

    private static class PlayerHandler implements Runnable {
        private Socket playerSocket;
        private BufferedReader playerIn;
        private PrintWriter playerOut;
        private Player player;

        private volatile boolean gameOver = false;

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

        private synchronized void endGame(String reason) {
            if (gameOver) return; // już zakończono

            gameOver = true;

            String endMessage = "END:" + reason + ":" + player1.getScore() + ":" + player2.getScore();
            player1Out.println(endMessage);
            player2Out.println(endMessage);

            System.out.println("Gra zakończona powodem: " + reason);

            // Zapisz wynik tylko raz, przy graczu 1
            if (player.getName().equals("Gracz 1")) {
    	        dbManager.saveResult(player1.getName(), player1.getScore(), player2.getName(), player2.getScore());
    	    }
        }

        private synchronized String getGameState() {
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
