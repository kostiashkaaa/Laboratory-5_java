package ru.spbstu.mdp.world;

import ru.spbstu.mdp.model.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * Клеточный мир (среда): размеры, стены, конечные состояния, награда R(s) обычной клетки и старт.
 * Объект неизменяемый; для построения и редактирования используется {@link Builder}.
 */
public final class GridWorld {

    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 10;
    public static final double DEFAULT_STEP_REWARD = -0.04;

    private final int width;
    private final int height;
    private final CellType[][] cells;          // [x-1][y-1]
    private final double[][] terminalRewards;  // [x-1][y-1], значимы только для TERMINAL
    private final double stepReward;
    private final Position start;
    private final List<Position> states;
    private final List<Position> terminals;

    private GridWorld(Builder b) {
        if (!Double.isFinite(b.stepReward)) {
            throw new IllegalArgumentException("R(s) должно быть конечным числом");
        }
        this.width = b.width;
        this.height = b.height;
        this.cells = copy(b.cells);
        this.terminalRewards = copy(b.terminalRewards);
        this.stepReward = b.stepReward;
        this.start = b.start;
        if (!contains(start)) {
            throw new IllegalArgumentException("Старт " + start + " вне мира " + width + "×" + height);
        }
        if (cell(start) != CellType.EMPTY) {
            throw new IllegalArgumentException("Стартовая клетка " + start + " должна быть обычной клеткой");
        }
        List<Position> all = new ArrayList<>();
        List<Position> exits = new ArrayList<>();
        for (int y = 1; y <= height; y++) {
            for (int x = 1; x <= width; x++) {
                Position p = new Position(x, y);
                CellType type = cell(p);
                if (type != CellType.WALL) {
                    all.add(p);
                }
                if (type == CellType.TERMINAL) {
                    if (!Double.isFinite(terminalRewards[x - 1][y - 1])) {
                        throw new IllegalArgumentException("Награда конечного состояния " + p + " должна быть конечной");
                    }
                    exits.add(p);
                }
            }
        }
        this.states = List.copyOf(all);
        this.terminals = List.copyOf(exits);
    }

    /** Мир из задания: 4 × 3, стена (2,2), +1 в (4,3), −1 в (4,2), старт (1,1). */
    public static Builder classicBuilder() {
        return builder(4, 3)
                .stepReward(DEFAULT_STEP_REWARD)
                .wall(2, 2)
                .terminal(4, 3, 1.0)
                .terminal(4, 2, -1.0)
                .start(1, 1);
    }

    public static GridWorld classic(double stepReward) {
        return classicBuilder().stepReward(stepReward).build();
    }

    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    public Builder toBuilder() {
        Builder b = new Builder(width, height);
        b.cells = copy(cells);
        b.terminalRewards = copy(terminalRewards);
        b.stepReward = stepReward;
        b.start = start;
        return b;
    }

    public GridWorld withStepReward(double reward) {
        return toBuilder().stepReward(reward).build();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Position start() {
        return start;
    }

    /** R(s) для всех неконечных клеток. */
    public double stepReward() {
        return stepReward;
    }

    public boolean contains(Position p) {
        return p.x() >= 1 && p.x() <= width && p.y() >= 1 && p.y() <= height;
    }

    public CellType cell(Position p) {
        if (!contains(p)) {
            throw new IllegalArgumentException("Клетка " + p + " вне мира " + width + "×" + height);
        }
        return cells[p.x() - 1][p.y() - 1];
    }

    public boolean isWall(Position p) {
        return cell(p) == CellType.WALL;
    }

    public boolean isTerminal(Position p) {
        return contains(p) && cell(p) == CellType.TERMINAL;
    }

    /** Клетка непроходима: за границей мира или стена. */
    public boolean isBlocked(Position p) {
        return !contains(p) || cells[p.x() - 1][p.y() - 1] == CellType.WALL;
    }

    /** Награда R(s): для конечной клетки — её значение (+1 / −1), для обычной — {@link #stepReward()}. */
    public double reward(Position p) {
        return switch (cell(p)) {
            case TERMINAL -> terminalRewards[p.x() - 1][p.y() - 1];
            case EMPTY -> stepReward;
            case WALL -> throw new IllegalArgumentException("У стены " + p + " нет награды");
        };
    }

    /** Все состояния (все клетки, кроме стен) в порядке строк снизу вверх. */
    public List<Position> states() {
        return states;
    }

    public List<Position> terminals() {
        return terminals;
    }

    private static CellType[][] copy(CellType[][] source) {
        CellType[][] result = new CellType[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }

    private static double[][] copy(double[][] source) {
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }

    private static void checkSize(int width, int height) {
        if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException("Размер мира должен быть от " + MIN_SIZE + " до " + MAX_SIZE
                    + " по каждой стороне, получено " + width + "×" + height);
        }
    }

    /** Изменяемый построитель мира; используется также редактором в GUI. */
    public static final class Builder {
        private int width;
        private int height;
        private CellType[][] cells;
        private double[][] terminalRewards;
        private double stepReward = DEFAULT_STEP_REWARD;
        private Position start = new Position(1, 1);

        private Builder(int width, int height) {
            checkSize(width, height);
            this.width = width;
            this.height = height;
            this.cells = new CellType[width][height];
            this.terminalRewards = new double[width][height];
            for (CellType[] column : cells) {
                java.util.Arrays.fill(column, CellType.EMPTY);
            }
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public Position start() {
            return start;
        }

        public CellType cell(Position p) {
            checkInside(p);
            return cells[p.x() - 1][p.y() - 1];
        }

        public double terminalReward(Position p) {
            checkInside(p);
            return terminalRewards[p.x() - 1][p.y() - 1];
        }

        public Builder stepReward(double reward) {
            this.stepReward = reward;
            return this;
        }

        public Builder wall(int x, int y) {
            return set(new Position(x, y), CellType.WALL, 0);
        }

        public Builder terminal(int x, int y, double reward) {
            return set(new Position(x, y), CellType.TERMINAL, reward);
        }

        public Builder empty(int x, int y) {
            return set(new Position(x, y), CellType.EMPTY, 0);
        }

        public Builder start(int x, int y) {
            Position p = new Position(x, y);
            if (cell(p) != CellType.EMPTY) {
                throw new IllegalArgumentException("Старт можно поставить только в обычную клетку, " + p + " — " + cell(p));
            }
            this.start = p;
            return this;
        }

        /** Перенос старта из редактора: {@code false}, если клетка не обычная. */
        public boolean moveStart(Position p) {
            if (cell(p) != CellType.EMPTY) {
                return false;
            }
            this.start = p;
            return true;
        }

        /**
         * Смена типа клетки по кругу (для редактора): пусто → стена → +1 → −1 → пусто.
         * Клетку старта менять нельзя — возвращается {@code false}.
         */
        public boolean cycle(Position p) {
            checkInside(p);
            if (p.equals(start)) {
                return false;
            }
            int x = p.x() - 1;
            int y = p.y() - 1;
            switch (cells[x][y]) {
                case EMPTY -> set(p, CellType.WALL, 0);
                case WALL -> set(p, CellType.TERMINAL, 1.0);
                case TERMINAL -> {
                    if (terminalRewards[x][y] > 0) {
                        set(p, CellType.TERMINAL, -1.0);
                    } else {
                        set(p, CellType.EMPTY, 0);
                    }
                }
            }
            return true;
        }

        /** Изменение размеров с сохранением пересекающейся части мира. */
        public Builder resize(int newWidth, int newHeight) {
            checkSize(newWidth, newHeight);
            CellType[][] newCells = new CellType[newWidth][newHeight];
            double[][] newRewards = new double[newWidth][newHeight];
            for (int x = 0; x < newWidth; x++) {
                for (int y = 0; y < newHeight; y++) {
                    boolean old = x < width && y < height;
                    newCells[x][y] = old ? cells[x][y] : CellType.EMPTY;
                    newRewards[x][y] = old ? terminalRewards[x][y] : 0;
                }
            }
            this.width = newWidth;
            this.height = newHeight;
            this.cells = newCells;
            this.terminalRewards = newRewards;
            if (start.x() > newWidth || start.y() > newHeight) {
                cells[0][0] = CellType.EMPTY;
                terminalRewards[0][0] = 0;
                start = new Position(1, 1);
            }
            return this;
        }

        public GridWorld build() {
            return new GridWorld(this);
        }

        private Builder set(Position p, CellType type, double reward) {
            checkInside(p);
            if (p.equals(start) && type != CellType.EMPTY) {
                throw new IllegalArgumentException("Клетка старта " + p + " должна оставаться обычной");
            }
            cells[p.x() - 1][p.y() - 1] = type;
            terminalRewards[p.x() - 1][p.y() - 1] = type == CellType.TERMINAL ? reward : 0;
            return this;
        }

        private void checkInside(Position p) {
            if (p.x() < 1 || p.x() > width || p.y() < 1 || p.y() > height) {
                throw new IllegalArgumentException("Клетка " + p + " вне мира " + width + "×" + height);
            }
        }
    }
}
