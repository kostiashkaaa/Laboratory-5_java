package ru.spbstu.mdp.model;

import java.util.Arrays;

/**
 * Неизменяемая стратегия π: клетке сопоставлено действие.
 * Для стен и конечных состояний действие равно {@code null}.
 */
public final class Policy {

    private final Action[][] actions;

    public Policy(Action[][] actions) {
        if (actions.length == 0 || actions[0].length == 0) {
            throw new IllegalArgumentException("Стратегия не может быть пустой");
        }
        this.actions = copy(actions);
    }

    public int width() {
        return actions.length;
    }

    public int height() {
        return actions[0].length;
    }

    /** π(s) или {@code null}, если в клетке нет действия. */
    public Action get(Position p) {
        return actions[p.x() - 1][p.y() - 1];
    }

    public Action[][] toArray() {
        return copy(actions);
    }

    /** Число клеток, в которых стратегии предписывают разные действия. */
    public int countDifferences(Policy other) {
        if (other.width() != width() || other.height() != height()) {
            throw new IllegalArgumentException("Размеры стратегий не совпадают");
        }
        int count = 0;
        for (int x = 0; x < actions.length; x++) {
            for (int y = 0; y < actions[x].length; y++) {
                if (actions[x][y] != other.actions[x][y]) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Policy other && Arrays.deepEquals(actions, other.actions);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(actions);
    }

    private static Action[][] copy(Action[][] source) {
        Action[][] result = new Action[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }
}
