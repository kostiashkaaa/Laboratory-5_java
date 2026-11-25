package ru.spbstu.mdp.model;

/**
 * Неизменяемая таблица полезностей U(s) размером width × height.
 * Значение клетки (x, y) хранится в {@code values[x-1][y-1]}; у стен значение 0 и не используется.
 */
public final class Utilities {

    private final double[][] values;

    public Utilities(double[][] values) {
        if (values.length == 0 || values[0].length == 0) {
            throw new IllegalArgumentException("Таблица полезностей не может быть пустой");
        }
        int height = values[0].length;
        for (double[] column : values) {
            if (column.length != height) {
                throw new IllegalArgumentException("Таблица полезностей должна быть прямоугольной");
            }
        }
        this.values = copy(values);
    }

    public static Utilities zeros(int width, int height) {
        return new Utilities(new double[width][height]);
    }

    public int width() {
        return values.length;
    }

    public int height() {
        return values[0].length;
    }

    public double get(Position p) {
        return values[p.x() - 1][p.y() - 1];
    }

    /** Копия значений (изменения копии не влияют на таблицу). */
    public double[][] toArray() {
        return copy(values);
    }

    /** max |U(s) − U'(s)| по всем клеткам — норма, по которой проверяется сходимость. */
    public double maxDifference(Utilities other) {
        if (other.width() != width() || other.height() != height()) {
            throw new IllegalArgumentException("Размеры таблиц полезностей не совпадают");
        }
        double max = 0;
        for (int x = 0; x < values.length; x++) {
            for (int y = 0; y < values[x].length; y++) {
                max = Math.max(max, Math.abs(values[x][y] - other.values[x][y]));
            }
        }
        return max;
    }

    private static double[][] copy(double[][] source) {
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }
}
