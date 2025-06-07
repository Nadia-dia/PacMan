package application;

import javafx.scene.input.KeyCode;

public class Player1 extends Player {

    public Player1(String name, int x, int y) {
        super(name, x, y);
    }

    @Override
    public String getDirectionFromKey(KeyCode key) {
        switch (key) {
            case W: return "UP";
            case S: return "DOWN";
            case A: return "LEFT";
            case D: return "RIGHT";
            default: return null;
        }
    }
}
