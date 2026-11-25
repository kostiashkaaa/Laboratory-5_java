package ru.spbstu.mdp.model;

import java.util.Locale;

/**
 * Действия агента. Порядок совпадает с порядком строк в уравнении состояния (1,1) из задания:
 * Up, Left, Down, Right. При равных полезностях предпочтение отдаётся действию, стоящему раньше.
 */
public enum Action {
    UP(0, 1, "↑", "Вверх"),
    LEFT(-1, 0, "←", "Влево"),
    DOWN(0, -1, "↓", "Вниз"),
    RIGHT(1, 0, "→", "Вправо");

    private final int dx;
    private final int dy;
    private final String arrow;
    private final String title;

    Action(int dx, int dy, String arrow, String title) {
        this.dx = dx;
        this.dy = dy;
        this.arrow = arrow;
        this.title = title;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public String arrow() {
        return arrow;
    }

    public String title() {
        return title;
    }

    /** Имя как в формулах задания: Up, Left, Down, Right. */
    public String englishName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    /** Направление на 90° против часовой стрелки (снос ветром «влево» относительно движения). */
    public Action turnLeft() {
        return switch (this) {
            case UP -> LEFT;
            case LEFT -> DOWN;
            case DOWN -> RIGHT;
            case RIGHT -> UP;
        };
    }

    /** Направление на 90° по часовой стрелке (снос ветром «вправо» относительно движения). */
    public Action turnRight() {
        return switch (this) {
            case UP -> RIGHT;
            case RIGHT -> DOWN;
            case DOWN -> LEFT;
            case LEFT -> UP;
        };
    }

    public Action opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
