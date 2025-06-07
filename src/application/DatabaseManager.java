package application;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class DatabaseManager {
    private static String DB_URL;
    private static String DB_NAME;
    private static String USER;
    private static String PASSWORD;

    private Connection connection;

    public DatabaseManager() {
        loadDbConfigFromTxt();

        try {
            // Połączenie do serwera (bez bazy)
            try (Connection initConnection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                 Statement stmt = initConnection.createStatement()) {

                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
            }

            // Połącz się z bazą danych
            connection = DriverManager.getConnection(DB_URL + DB_NAME, USER, PASSWORD);

            // Utwórz tabelę, jeśli nie istnieje
            try (Statement stmt = connection.createStatement()) {
                String createTableSQL = """
                    CREATE TABLE IF NOT EXISTS wyniki (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player1_name VARCHAR(50),
                        player1_score INT,
                        player2_name VARCHAR(50),
                        player2_score INT,
                        date_played TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """;
                stmt.executeUpdate(createTableSQL);
            }

        } catch (SQLException e) {
            System.err.println("Błąd podczas inicjalizacji bazy danych: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadDbConfigFromTxt() {
        Properties props = new Properties();
        try (FileInputStream input = new FileInputStream("src/resources/db_config.txt")) {
            props.load(input);
            DB_URL = props.getProperty("url", "jdbc:mysql://localhost:3306/");
            DB_NAME = props.getProperty("name", "PacMan");
            USER = props.getProperty("user", "root");
            PASSWORD = props.getProperty("password", "");
        } catch (IOException e) {
            System.err.println("Nie można wczytać pliku db_config.txt. Używam wartości domyślnych.");
            DB_URL = "jdbc:mysql://localhost:3306/";
            DB_NAME = "PacMan";
            USER = "root";
            PASSWORD = "";
        }
    }

    public void saveResult(String player1Name, int player1Score, String player2Name, int player2Score) {
        String insertSQL = """
            INSERT INTO wyniki (player1_name, player1_score, player2_name, player2_score)
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            pstmt.setString(1, player1Name);
            pstmt.setInt(2, player1Score);
            pstmt.setString(3, player2Name);
            pstmt.setInt(4, player2Score);
            pstmt.executeUpdate();
            System.out.println("Wyniki zostały zapisane w bazie danych.");
        } catch (SQLException e) {
            System.err.println("Błąd zapisu wyniku do bazy danych: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Błąd przy zamykaniu połączenia: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
