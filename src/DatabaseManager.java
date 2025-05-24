import java.sql.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/";
    private static final String DB_NAME = "PacMan";
    private static final String USER = "root"; 
    private static final String PASSWORD = ""; 

    private Connection connection;

    public DatabaseManager() throws SQLException {
        // Najpierw połącz się bezpośrednio do serwera MySQL (bez bazy)
        connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);

        // Sprawdź i utwórz bazę, jeśli nie istnieje
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
        }

        // Teraz połącz się z już istniejącą bazą PacMan
        connection.close();
        connection = DriverManager.getConnection(DB_URL + DB_NAME, USER, PASSWORD);

        // Sprawdź i utwórz tabelę wyniki, jeśli nie istnieje
        try (Statement stmt = connection.createStatement()) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS wyniki (" +
                                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                                    "player1_name VARCHAR(50)," +
                                    "player1_score INT," +
                                    "player2_name VARCHAR(50)," +
                                    "player2_score INT," +
                                    "date_played TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                                    ")";
            stmt.executeUpdate(createTableSQL);
        }
    }

    public void saveResult(String player1Name, int player1Score, String player2Name, int player2Score) {
        String insertSQL = "INSERT INTO wyniki (player1_name, player1_score, player2_name, player2_score) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            pstmt.setString(1, player1Name);
            pstmt.setInt(2, player1Score);
            pstmt.setString(3, player2Name);
            pstmt.setInt(4, player2Score);
            pstmt.executeUpdate();
            System.out.println("Wyniki zapisane w bazie danych.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
