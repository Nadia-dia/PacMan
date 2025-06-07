package application;
import javafx.scene.input.KeyCode;

public abstract class Player {
    protected String name;
    protected int x, y;
    protected int score;

    public Player(String name, int x, int y) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.score = 0;
    }

    public abstract String getDirectionFromKey(KeyCode key);

    public String getName() {
        return name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}