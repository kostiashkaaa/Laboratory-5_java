package ru.spbstu.mdp.decision;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.world.Transition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Оценка стратегии — расчёт полезности U^π(s) фиксированной стратегии π.
 * <ul>
 *     <li>{@link #iterative} — формула из задания: U_{i+1}(s) = R(s) + γ·Σ T(s, π(s), s')·U_i(s');</li>
 *     <li>{@link #exact} — точное решение системы линейных уравнений U = R + γ·P_π·U методом Гаусса.</li>
 * </ul>
 */
public final class PolicyEvaluator {

    public static final double DEFAULT_TOLERANCE = 1e-11;
    public static final int DEFAULT_MAX_SWEEPS = 10_000;
    private static final double PIVOT_EPSILON = 1e-10;

    /** @param sweeps число выполненных итераций; {@code converged} — изменение стало меньше допуска */
    public record Result(Utilities utilities, int sweeps, boolean converged, double lastDelta) {
    }

    private final double tolerance;
    private final int maxSweeps;

    public PolicyEvaluator() {
        this(DEFAULT_TOLERANCE, DEFAULT_MAX_SWEEPS);
    }

    public PolicyEvaluator(double tolerance, int maxSweeps) {
        if (!(tolerance > 0) || maxSweeps < 1) {
            throw new IllegalArgumentException("Некорректные параметры оценки стратегии");
        }
        this.tolerance = tolerance;
        this.maxSweeps = maxSweeps;
    }

    /**
     * Итеративная оценка, начиная с {@code initial}. Если стратегия при γ = 1 не приводит к выходу
     * («зацикливается»), значения уходят в −∞ и цикл прерывается по лимиту итераций — {@code converged = false}.
     */
    public Result iterative(Mdp mdp, Policy policy, Utilities initial) {
        double[][] current = initial.toArray();
        int width = current.length;
        int height = current[0].length;
        int sweeps = 0;
        double delta = Double.POSITIVE_INFINITY;
        boolean converged = false;
        while (sweeps < maxSweeps) {
            sweeps++;
            double[][] next = new double[width][height];
            delta = 0;
            for (Position s : mdp.states()) {
                double value = mdp.reward(s);
                if (!mdp.isTerminal(s)) {
                    value += mdp.gamma() * mdp.expectedUtility(s, requireAction(policy, s), current);
                }
                next[s.x() - 1][s.y() - 1] = value;
                delta = Math.max(delta, Math.abs(value - current[s.x() - 1][s.y() - 1]));
            }
            current = next;
            if (delta <= tolerance) {
                converged = true;
                break;
            }
        }
        return new Result(new Utilities(current), sweeps, converged, delta);
    }

    /**
     * Точная оценка. Возвращает пустой результат, если система вырождена — так бывает при γ = 1, когда
     * стратегия из некоторых клеток никогда не приводит в конечное состояние (полезность не определена).
     */
    public Optional<Utilities> exact(Mdp mdp, Policy policy) {
        List<Position> states = mdp.states();
        int n = states.size();
        Map<Position, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(states.get(i), i);
        }
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            Position s = states.get(i);
            a[i][i] = 1.0;
            a[i][n] = mdp.reward(s);
            if (!mdp.isTerminal(s)) {
                for (Transition t : mdp.transitions(s, requireAction(policy, s))) {
                    a[i][index.get(t.next())] -= mdp.gamma() * t.probability();
                }
            }
        }
        // Метод Гаусса — Жордана с выбором главного элемента по столбцу.
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            if (Math.abs(a[pivot][col]) < PIVOT_EPSILON) {
                return Optional.empty();
            }
            double[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            double div = a[col][col];
            for (int c = col; c <= n; c++) {
                a[col][c] /= div;
            }
            for (int r = 0; r < n; r++) {
                if (r != col && a[r][col] != 0) {
                    double factor = a[r][col];
                    for (int c = col; c <= n; c++) {
                        a[r][c] -= factor * a[col][c];
                    }
                }
            }
        }
        double[][] values = new double[mdp.world().width()][mdp.world().height()];
        for (int i = 0; i < n; i++) {
            Position s = states.get(i);
            values[s.x() - 1][s.y() - 1] = a[i][n];
        }
        return Optional.of(new Utilities(values));
    }

    private static Action requireAction(Policy policy, Position s) {
        Action a = policy.get(s);
        if (a == null) {
            throw new IllegalArgumentException("Стратегия не задаёт действие для состояния " + s);
        }
        return a;
    }
}
