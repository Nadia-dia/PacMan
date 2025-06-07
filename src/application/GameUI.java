package application;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.image.Image;


public class GameUI extends Pane {

    private GraphicsContext gc;
    private Image ghostImage;
    private Image player1Image;
    private Image player2Image;


    public GameUI(GraphicsContext gc, int[][] map) {
        this.gc = gc;
        this.ghostImage = new Image(getClass().getResourceAsStream("/images/ghost.png")); // wczytaj obrazek
        player1Image = new Image(getClass().getResourceAsStream("/images/player1.png"));
        player2Image = new Image(getClass().getResourceAsStream("/images/player2.png"));

        initializeMap(map);
    }

    // Metoda inicjalizująca mapę i początkowe pozycje graczy
    public void initializeMap(int[][] map) {
        drawMap(map);

        // Domyślne pozycje początkowe graczy
        updatePlayerPosition(1, 1, Color.BLUE);   // Pierwszy gracz na pozycji (1,1)
        updatePlayerPosition(23, 1, Color.RED);  // Drugi gracz na pozycji (23,16)
    }

    void drawMap(GraphicsContext gc) {
        // Całkowite wyczyszczenie płótna
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());

        drawBackground(gc);
        drawItems(gc);
    }

    private void drawBackground(GraphicsContext gc) {
        // Rysowanie tła (ścian i pustych pól)
        for (int y = 0; y < GameMap.MAP.length; y++) {
            for (int x = 0; x < GameMap.MAP[0].length; x++) {
                int tile = GameMap.MAP[y][x];

                if (tile == 1) { // Ściana
                    gc.setFill(Color.DARKBLUE);
                } else { // Puste pole
                    gc.setFill(Color.WHITE);
                }
                gc.fillRect(x * GameMap.TILE_SIZE, y * GameMap.TILE_SIZE, GameMap.TILE_SIZE, GameMap.TILE_SIZE);
            }
        }
    }

    private void drawItems(GraphicsContext gc) {
        // Rysowanie punktów do zebrania (kropki)
        for (int y = 0; y < GameMap.MAP.length; y++) {
            for (int x = 0; x < GameMap.MAP[0].length; x++) {
                int tile = GameMap.MAP[y][x];

                if (tile == 2) { // Punkt do zebrania (kropka)
                    gc.setFill(Color.DARKSALMON);
                    gc.fillOval(
                            x * GameMap.TILE_SIZE + GameMap.TILE_SIZE / 2 - 3, // X-koordynata kropki (w centrum)
                            y * GameMap.TILE_SIZE + GameMap.TILE_SIZE / 2 - 3, // Y-koordynata kropki (w centrum)
                            6, 6); // Rozmiar kropki (średnica 6)
                }
            }
        }
    }

    public void drawMap(int[][] map) {
        // Wyczyszczenie płótna
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());

        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[0].length; x++) {
                if (map[y][x] == 1) {
                    gc.setFill(Color.DARKBLUE);
                } else {
                    gc.setFill(Color.WHITE);
                }
                gc.fillRect(x * GameMap.TILE_SIZE, y * GameMap.TILE_SIZE, GameMap.TILE_SIZE, GameMap.TILE_SIZE);

                if (map[y][x] == 2) {
                    gc.setFill(Color.DARKSALMON);
                    gc.fillOval(
                            x * GameMap.TILE_SIZE + GameMap.TILE_SIZE / 2 - 3,
                            y * GameMap.TILE_SIZE + GameMap.TILE_SIZE / 2 - 3,
                            6, 6
                    );
                }
            }
        }
    }


    // Metoda do aktualizacji pozycji gracza
    public void updatePlayerPosition(int playerX, int playerY, Color color) {
        Image playerImage = (color == Color.BLUE) ? player1Image : player2Image;

        gc.drawImage(
                playerImage,
                playerX * GameMap.TILE_SIZE,
                playerY * GameMap.TILE_SIZE,
                GameMap.TILE_SIZE,
                GameMap.TILE_SIZE
        );
    }


    public void updateGhostPosition(int x, int y) {
        gc.drawImage(
                ghostImage,
                x * GameMap.TILE_SIZE,
                y * GameMap.TILE_SIZE,
                GameMap.TILE_SIZE,
                GameMap.TILE_SIZE
        );
    }



    public void handleGameEnd(int playerNumber, int score1, int score2, String reason) {
        double width = gc.getCanvas().getWidth();
        double height = gc.getCanvas().getHeight();

        // Tło
        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, width, height);

        // Ramka
        gc.setStroke(Color.GOLD);
        gc.setLineWidth(5);
        gc.strokeRoundRect(150, 150, width - 300, height - 300, 30, 30);

        // Fonty
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        gc.setFill(Color.WHITE);

        // Tytuł
        gc.fillText("Koniec gry!", width / 2 - 100, 220);

        // Wyniki
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 24));
        gc.fillText("Twoj wynik (Gracz " + playerNumber + "): " + (playerNumber == 1 ? score1:score2),
                width / 2 - 130, 270);
        gc.fillText("Wynik oponenta (Gracz " + (3-playerNumber) + "): " + (playerNumber == 1 ? score2:score1),
                width / 2 - 160, 310);

        // Rezultat
        String result;
        Color resultColor;

        if ((playerNumber == 1 && score1 > score2) || (playerNumber == 2 && score2 > score1)) {
            result = "Wygrałeś!";
            resultColor = Color.LIMEGREEN;
        } else if (score1 == score2) {
            result = "Remis!";
            resultColor = Color.GOLD;
        } else {
            result = "Przegrałeś!";
            resultColor = Color.RED;
        }

        gc.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        gc.setFill(resultColor);
        gc.fillText(result, width / 2 - 70, 370);

        // Dodatkowy tekst z powodem zakończenia gry
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 15));
        gc.setFill(Color.LIGHTGRAY);
        String reasonText = switch (reason) {
            case "CAUGHT_BY_GHOST" -> "Gracz złapany przez ducha!";
            case "ALL_POINTS_COLLECTED"-> "Zebrano wszystkie punkty!";
            default -> "Powód zakończenia: " + reason;
        };
        gc.fillText(reasonText, width / 2 - 150, 410);
    }



}