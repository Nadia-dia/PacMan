package application;

import javafx.scene.input.KeyCode;

public class Player2 extends Player {

    public Player2(String name, int x, int y) {
        super(name, x, y);
    }

    @Override
    public String getDirectionFromKey(KeyCode key) {
        switch (key) {
            case UP: return "UP";
            case DOWN: return "DOWN";
            case LEFT: return "LEFT";
            case RIGHT: return "RIGHT";
            default: return null;
        }
    }
}
