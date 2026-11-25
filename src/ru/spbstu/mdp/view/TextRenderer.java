package ru.spbstu.mdp.view;

import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.util.Fmt;
import ru.spbstu.mdp.world.CellType;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.Transition;

import java.util.function.Function;
import java.util.stream.Collectors;

/** Текстовое представление мира, полезностей, стратегии и выбора действия (консоль, журнал GUI). */
public final class TextRenderer {

    private static final int CELL = 8;

    private TextRenderer() {
    }

    /** Карта мира: S — старт, ### — стена, +1/−1 — конечные состояния. */
    public static String world(GridWorld world) {
        return grid(world, p -> switch (world.cell(p)) {
            case WALL -> "###";
            case TERMINAL -> Fmt.signed(world.reward(p));
            case EMPTY -> p.equals(world.start()) ? "S" : ".";
        });
    }

    /** Таблица полезностей (как в задании: строки сверху вниз 3, 2, 1). */
    public static String utilities(GridWorld world, Utilities u) {
        return grid(world, p -> switch (world.cell(p)) {
            case WALL -> "###";
            case TERMINAL -> "[" + Fmt.signed(world.reward(p)) + "]";
            case EMPTY -> Fmt.fixed(u.get(p), 3);
        });
    }

    /** Стратегия стрелками. */
    public static String policy(GridWorld world, Policy policy) {
        return grid(world, p -> {
            if (world.cell(p) == CellType.WALL) {
                return "###";
            }
            if (world.cell(p) == CellType.TERMINAL) {
                return "[" + Fmt.signed(world.reward(p)) + "]";
            }
            Action a = policy == null ? null : policy.get(p);
            return a == null ? "?" : a.arrow();
        });
    }

    /**
     * Выбор действия в клетке s в виде уравнения из задания:
     * U(s) = R(s) + γ·max { Up: 0.8·U(1,2) + 0.1·U(2,1) + 0.1·U(1,1), … }.
     */
    public static String decision(Mdp mdp, Utilities u, Policy policy, Position s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Клетка ").append(s).append(": R(s) = ").append(Fmt.signed(mdp.reward(s)))
                .append(", γ = ").append(Fmt.num(mdp.gamma())).append('\n');
        if (mdp.isTerminal(s)) {
            sb.append("Конечное состояние: U").append(s).append(" = R(s) = ").append(Fmt.signed(mdp.reward(s)));
            return sb.toString();
        }
        Action chosen = policy == null ? null : policy.get(s);
        double best = Double.NEGATIVE_INFINITY;
        for (Action a : Action.values()) {
            String formula = mdp.transitions(s, a).stream()
                    .map(TextRenderer::term)
                    .collect(Collectors.joining(" + "));
            double v = mdp.expectedUtility(s, a, u);
            best = Math.max(best, v);
            sb.append(String.format("%-5s %s = %s%s%n", a.englishName(), formula, Fmt.fixed(v, 4),
                    a == chosen ? "  ◀ выбор" : ""));
        }
        sb.append("U").append(s).append(" = ").append(Fmt.signed(mdp.reward(s))).append(" + ")
                .append(Fmt.num(mdp.gamma())).append("·").append(Fmt.fixed(best, 4))
                .append(" = ").append(Fmt.fixed(mdp.reward(s) + mdp.gamma() * best, 4));
        return sb.toString();
    }

    private static String term(Transition t) {
        return Fmt.num(t.probability()) + "·U" + t.next();
    }

    private static String grid(GridWorld world, Function<Position, String> cell) {
        StringBuilder sb = new StringBuilder();
        for (int y = world.height(); y >= 1; y--) {
            sb.append(String.format("%2d │", y));
            for (int x = 1; x <= world.width(); x++) {
                sb.append(center(cell.apply(new Position(x, y))));
            }
            sb.append('\n');
        }
        sb.append("   └").append("─".repeat(CELL * world.width())).append('\n');
        sb.append("    ");
        for (int x = 1; x <= world.width(); x++) {
            sb.append(center(String.valueOf(x)));
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String center(String s) {
        int pad = Math.max(0, CELL - s.length());
        int left = pad / 2;
        return " ".repeat(left) + s + " ".repeat(pad - left);
    }
}
