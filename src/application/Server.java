package application;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private static int PORT;
    private static final int MAP_WIDTH = 25;
    private static final int MAP_HEIGHT = 18;
    private static int[][] MAP;

    private static ServerSocket serverSocket;

    private static int[][] sharedMap = new int[MAP_HEIGHT][MAP_WIDTH];
    private static List<PlayerHandler> players = new ArrayList<>();

    private static Ghost ghost1;
    private static Ghost ghost2;
    //private static Thread ghostThread1;
    //private static Thread ghostThread2;

    static DatabaseManager dbManager = null;

    private static volatile boolean gameOver = false;

    private static ExecutorService executor;

    public static void main(String[] args) {
        try {
            FileLogger logger=new FileLogger("application.log");
            loadConfig();

            chooseRandomMap();

            for (int y = 0; y < MAP_HEIGHT; y++) {
                System.arraycopy(MAP[y], 0, sharedMap[y], 0, MAP_WIDTH);
            }

            serverSocket = new ServerSocket(PORT);
            System.out.println("Serwer nasłuchuje na porcie " + PORT);
            logger.log("Uruchominono serwer, nasłuchuje na porcie " + PORT + ".");

            dbManager = new DatabaseManager();
            System.out.println("Połączono z bazą danych.");
            logger.log("Połączono z bazą danych.");

            while (players.size() < 2) {
                Socket playerSocket = serverSocket.accept();
                BufferedReader playerIn = new BufferedReader(new InputStreamReader(playerSocket.getInputStream()));
                PrintWriter playerOut = new PrintWriter(playerSocket.getOutputStream(), true);

                Player currentPlayer;
                if (players.size() == 0) {
                    currentPlayer = new Player1("Gracz 1", 1, 1);
                    playerOut.println("PLAYER:1");
                } else {
                    currentPlayer = new Player2("Gracz 2", 23, 1);
                    playerOut.println("PLAYER:2");
                }

                players.add(new PlayerHandler(playerSocket, playerIn, playerOut, currentPlayer));
                System.out.println("Połączono Gracza " + players.size());
                logger.log("Połączono Gracza " + players.size());
            }

            // Tworzymy executor dla 4 wątków: 2 graczy i 2 duchy
            executor = Executors.newFixedThreadPool(4);

            ghost1 = new Ghost(1, 16, sharedMap, players.get(0).player, players.get(1).player);
            ghost2 = new Ghost(23, 16, sharedMap, players.get(0).player, players.get(1).player);

            // Uruchamiamy zadania w executorze zamiast Thread.start()
            executor.submit(ghost1);
            executor.submit(ghost2);

            executor.submit(players.get(0));
            executor.submit(players.get(1));

            // Czekamy aż zadania się zakończą (np. gra się zakończy)
            executor.shutdown();
            logger.log("Zakończenie zadań.");
            // Czekamy maksymalnie 10 minut na zakończenie wszystkich wątków
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                System.out.println("Zakończenie zadań zajęło zbyt długo, wymuszam shutdown.");
                logger.log("Zakończenie zadań zajęło zbyt długo, wymuszam shutdown.");
                executor.shutdownNow();
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            shutdown();
            System.out.println("Zamknięto zasoby serwera.");
        }
    }

    private static void chooseRandomMap() {
        Random random = new Random();
        int choice = random.nextInt(10);
        switch (choice) {
            case 0 -> MAP = GameMap.MAP;
            case 1 -> MAP = GameMap.MAP2;
            case 2 -> MAP = GameMap.MAP3;
            case 3 -> MAP = GameMap.MAP4;
            case 4 -> MAP = GameMap.MAP5;
            case 5 -> MAP = GameMap.MAP6;
            case 6 -> MAP = GameMap.MAP7;
            case 7 -> MAP = GameMap.MAP8;
            case 8 -> MAP = GameMap.MAP9;
            case 9 -> MAP = GameMap.MAP10;

        }
        System.out.println("Wybrano mapę nr " + (choice + 1));
    }

    private static void loadConfig() {
        Properties props = new Properties();
        try (InputStream input = Server.class.getClassLoader().getResourceAsStream("resources/config.txt")) {
            if (input != null) {
                props.load(input);
                PORT = Integer.parseInt(props.getProperty("port", "1234"));
            } else {
                System.err.println("Nie znaleziono pliku config.txt Używam domyślnych wartości.");
                PORT = 1234;
            }
        } catch (IOException e) {
            System.err.println("Błąd ładowania config.txt Używam domyślnych wartości.");
            PORT = 1234;
        }
    }

    public static synchronized void endGame(String reason) {
        if (gameOver) return;
        gameOver = true;

        System.out.println("DEBUG: Wywołano endGame z powodem: " + reason);

        Player player1 = players.get(0).player;
        Player player2 = players.get(1).player;
        String endMessage = "END:" + reason + ":" + player1.getScore() + ":" + player2.getScore();
        System.out.println(endMessage);

        for (PlayerHandler player : players) {
            player.playerOut.println(endMessage);
        }

        System.out.println("Gra zakończona powodem: " + reason);

        try {
            if (dbManager != null) {
                dbManager.saveResult(player1.getName(), player1.getScore(),
                        player2.getName(), player2.getScore());
                dbManager.close();
            }

            for (PlayerHandler player : players) {
                if (player.playerSocket != null) player.playerSocket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        shutdown();
        System.exit(0);
    }

    private static void shutdown() {
        try {
            // Przerwanie działania duchów
            if (ghost1 != null) ghost1.stop();
            if (ghost2 != null) ghost2.stop();

            // Zamknięcie socketów graczy
            for (PlayerHandler player : players) {
                if (player.playerSocket != null && !player.playerSocket.isClosed()) {
                    player.playerSocket.close();
                }
            }

            // Zamknięcie gniazda serwera
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // Zatrzymanie executorów (na wszelki wypadek, jeśli jeszcze działa)
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }

            System.out.println("Zamknięto zasoby serwera.");

        } catch (IOException e) {
            e.printStackTrace();
        }
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

                while (!gameOver) {
                    if (playerIn.ready()) {
                        playerMessage = playerIn.readLine();
                        if (playerMessage != null) {
                            if (playerMessage.equals("DISCONNECT")) {
                                System.out.println(player.getName() + " wysłał DISCONNECT.");
                                endGame("PLAYER_DISCONNECTED");
                                break;
                            }
                            handlePlayerMove(player, playerMessage);
                        } else {
                            System.out.println(player.getName() + " się rozłączył.");
                            endGame("PLAYER_DISCONNECTED");
                            break;
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
                        return false;
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
                }

                p.setX(newX);
                p.setY(newY);

                if ((ghost1.getX() == p.getX() && ghost1.getY() == p.getY()) ||
                        (ghost2.getX() == p.getX() && ghost2.getY() == p.getY())) {

                    System.out.println(p.getName() + " został złapany przez ducha! Wynik zerowany.");

                    p.setScore(0);
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