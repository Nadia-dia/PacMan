package application;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.scene.text.Font;



import java.io.*;
import java.net.Socket;
import java.util.Properties;


public class Client extends Application {
    private static String HOST;
    private static int PORT;
    private Socket socket;
    private volatile boolean running = true;

    private static final int TILE_SIZE = 32;
    private static final int MAP_WIDTH = 25;
    private static final int MAP_HEIGHT = 18;

    private PrintWriter out;
    private BufferedReader in;
    private int playerNumber = 0;

    private Player player;
    private Player opponent;

    private static Ghost ghost1;
    private static Ghost ghost2;

    private GameUI gameUI;
    private Canvas canvas;
    private GraphicsContext gc;

    private GameStateListener gameStateListener = new GameStateListener() {
        @Override
        public void onGameStateReceived(String state) {
            Platform.runLater(() -> {
                parseGameState(state);
            });
        }
    };

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Połączenie z serwerem
        loadConfig();

        try {
            socket = new Socket(HOST, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Nie udało się nawiązać połączenia z " + HOST + " na porcie " + PORT);
            e.printStackTrace();
            Platform.exit();
            return;
        }

        // Odczytanie numeru gracza
        String firstMessage = in.readLine();
        System.out.println("Otrzymano wiadomość: " + firstMessage);
        if (firstMessage != null && firstMessage.startsWith("PLAYER:")) {
            playerNumber = Integer.parseInt(firstMessage.split(":")[1]);
            System.out.println("Jestem graczem nr: " + playerNumber);
        }

        // Inicjalizacja graczy
        if (playerNumber == 1) {
            player = new Player1("Gracz 1", 1, 1);
            opponent = new Player2("Gracz 2", 23, 1);
        } else {
            player = new Player2("Gracz 2", 23, 1);
            opponent = new Player1("Gracz 1", 1, 1);
        }

        int ghost1X = 1, ghost1Y = 16;
        int ghost2X = 23, ghost2Y = 16;
        ghost1 = new Ghost(ghost1X, ghost1Y, GameMap.MAP, player, opponent);
        ghost2 = new Ghost(ghost2X, ghost2Y, GameMap.MAP, player, opponent);

        // Przygotowanie GUI
        canvas = new Canvas(TILE_SIZE * MAP_WIDTH, TILE_SIZE * MAP_HEIGHT);
        gc = canvas.getGraphicsContext2D();
        gameUI = new GameUI(gc);

        Pane root = new Pane();
        root.getChildren().add(canvas);
        Scene scene = new Scene(root, TILE_SIZE * MAP_WIDTH, TILE_SIZE * MAP_HEIGHT);

        // Obsługa klawiatury
        scene.setOnKeyPressed(event -> {
            String direction = player.getDirectionFromKey(event.getCode());
            if (direction != null) {
                out.println(direction);
                //System.out.println("Wysłano ruch: " + direction);
            }
        });

        primaryStage.setTitle("Pacman Multiplayer");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            running = false;
            try {
                out.println("DISCONNECT");
                out.flush();
                in.close();
                out.close();
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                System.exit(0);
            }
        });

        new Thread(() -> {
            try {
                while (running) {
                    String state = in.readLine();
                    if (state == null) {
                        System.out.println("Serwer rozłączył połączenie.");
                        break;
                    }
                    if (state.startsWith("END:")) {
                        handleGameEnd(state);
                        break;
                    } else {
                        // 3. Wywołanie metody z interfejsu zamiast bezpośrednio parseGameState
                        gameStateListener.onGameStateReceived(state);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private static void loadConfig() {
        Properties props = new Properties();
        try (InputStream input = Client.class.getClassLoader().getResourceAsStream("config.txt")) {
            if (input == null) {
                System.err.println("Plik config.txt nie został znaleziony.");
                HOST = "localhost";
                PORT = 1234;
                return;
            }
            props.load(input);
            HOST = props.getProperty("host", "localhost");
            PORT = Integer.parseInt(props.getProperty("port", "1234"));
        } catch (IOException e) {
            System.err.println("Błąd ładowania config.txt Używam domyślnych wartości.");
            HOST = "localhost";
            PORT = 1234;
        }
    }

    private void handleGameEnd(String message) {
        String[] parts = message.split(":");

        String reason = parts[1];  // np. "CAUGHT_BY_GHOST" albo "ALL_POINTS_COLLECTED"
        int score1 = Integer.parseInt(parts[2]);
        int score2 = Integer.parseInt(parts[3]);
        System.out.println(score1+ " / " + score2);

        System.out.println("Koniec gry, powód: " + reason);

        Platform.runLater(() -> {
            // Przekaż też powód zakończenia, jeśli chcesz nim sterować UI
            gameUI.handleGameEnd(playerNumber, score1, score2, reason);
        });
    }


    private void parseGameState(String state) {
        Platform.runLater(() -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            int[][] tempMap = new int[MAP_HEIGHT][MAP_WIDTH];

            String[] parts = state.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("Player1:")) {
                    String coordsPart = part.split("\\(")[1].split("\\)")[0];
                    String[] coords = coordsPart.split(",");
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());

                    if (playerNumber == 1) {
                        player.setX(x);
                        player.setY(y);
                    } else {
                        opponent.setX(x);
                        opponent.setY(y);
                    }

                } else if (part.startsWith("Player2:")) {
                    String coordsPart = part.split("\\(")[1].split("\\)")[0];
                    String[] coords = coordsPart.split(",");
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());

                    if (playerNumber == 2) {
                        player.setX(x);
                        player.setY(y);
                    } else {
                        opponent.setX(x);
                        opponent.setY(y);
                    }

                } else if (part.startsWith("Score1:")) {
                    int score = Integer.parseInt(part.split(":")[1].trim());
                    if (playerNumber == 1) player.setScore(score);
                    else opponent.setScore(score);

                } else if (part.startsWith("Score2:")) {
                    int score = Integer.parseInt(part.split(":")[1].trim());
                    if (playerNumber == 2) player.setScore(score);
                    else opponent.setScore(score);

                } else if (part.startsWith("Ghost1:")) {
                    String coordsPart = part.split("\\(")[1].split("\\)")[0];
                    String[] coords = coordsPart.split(",");
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());
                    ghost1.setX(x);
                    ghost1.setY(y);
                }  else if (part.startsWith("Ghost2:")) {
                    String coordsPart = part.split("\\(")[1].split("\\)")[0];
                    String[] coords = coordsPart.split(",");
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());
                    ghost2.setX(x);
                    ghost2.setY(y);
                } else if (part.startsWith("Wall:")) {
                    String coordsPart = part.split("\\(")[1].split("\\)")[0];
                    String[] coords = coordsPart.split(",");
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());
                    tempMap[y][x] = 1;

                } else if (part.startsWith("Point:")) {
                    String coordsPart = part.split("\\(")[1].split("\\)")[0];
                    String[] coords = coordsPart.split(",");
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());
                    tempMap[y][x] = 2;
                }
            }

            // Rysujemy mapę
            gameUI.drawMap(tempMap);

            // Rysujemy graczy
            gameUI.updatePlayerPosition(player.getX(), player.getY(), (playerNumber == 1) ? Color.BLUE : Color.RED);
            gameUI.updatePlayerPosition(opponent.getX(), opponent.getY(), (playerNumber == 1) ? Color.RED : Color.BLUE);

            gameUI.updateGhostPosition(ghost1.getX(), ghost1.getY());
            gameUI.updateGhostPosition(ghost2.getX(), ghost2.getY());
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}