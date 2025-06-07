package application;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileLogger {
    private String fileName;
    private DateTimeFormatter dateTimeFromat;

    public FileLogger(String filePath) {
        this.fileName = filePath;
        this.dateTimeFromat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    }

    public void log(String message) {
        String timestamp= LocalDateTime.now().format(dateTimeFromat);
        String logEntry= String.format("[%s]: %s\n", timestamp, message);

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(logEntry);
        }catch (IOException e){
            System.err.println("Nie udało się zapisać do pliku logu: " + e.getMessage());
        }
    }

}
