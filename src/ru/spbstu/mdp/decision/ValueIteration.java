package ru.spbstu.mdp.decision;

import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.model.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * Итерация по значениям: многократное применение уравнения Беллмана
 * U_{i+1}(s) = R(s) + γ·max_a Σ T(s, a, s')·U_i(s'), начиная с U₀ = 0.
 * <p>
 * Остановка: при γ &lt; 1 — δ ≤ ε(1 − γ)/γ (тогда погрешность ≤ ε), при γ = 1 — δ ≤ ε.
 */
public final class ValueIteration implements MdpSolver {

    public static final double DEFAULT_EPSILON = 1e-10;
    public static final int DEFAULT_MAX_ITERATIONS = 100_000;
    /** Сколько промежуточных итераций хранить для показа (последняя сохраняется всегда). */
    static final int HISTORY_LIMIT = 5_000;

    private final double epsilon;
    private final int maxIterations;

    public ValueIteration() {
        this(DEFAULT_EPSILON, DEFAULT_MAX_ITERATIONS);
    }

    public ValueIteration(double epsilon, int maxIterations) {
        if (!(epsilon > 0) || maxIterations < 1) {
            throw new IllegalArgumentException("Некорректные параметры итерации по значениям");
        }
        this.epsilon = epsilon;
        this.maxIterations = maxIterations;
    }

    @Override
    public String name() {
        return Algorithm.VALUE_ITERATION.title();
    }

    @Override
    public SolverResult solve(Mdp mdp) {
        long started = System.nanoTime();
        int width = mdp.world().width();
        int height = mdp.world().height();
        double gamma = mdp.gamma();
        double threshold = gamma < 1 ? epsilon * (1 - gamma) / gamma : epsilon;

        double[][] current = new double[width][height];
        List<IterationSnapshot> history = new ArrayList<>();
        history.add(new IterationSnapshot(0, new Utilities(current), null, Double.NaN, 0, "U₀(s) = 0 для всех s"));

        int iteration = 0;
        boolean converged = false;
        IterationSnapshot last = history.get(0);
        while (iteration < maxIterations) {
            iteration++;
            double[][] next = new double[width][height];
            double delta = 0;
            for (Position s : mdp.states()) {
                double best = 0;
                if (!mdp.isTerminal(s)) {
                    best = Double.NEGATIVE_INFINITY;
                    for (Action a : Action.values()) {
                        best = Math.max(best, mdp.expectedUtility(s, a, current));
                    }
                }
                double value = mdp.reward(s) + gamma * best;
                next[s.x() - 1][s.y() - 1] = value;
                delta = Math.max(delta, Math.abs(value - current[s.x() - 1][s.y() - 1]));
            }
            current = next;
            converged = delta <= threshold;
            if (history.size() < HISTORY_LIMIT || converged || iteration == maxIterations) {
                Utilities u = new Utilities(current);
                var policy = mdp.greedyPolicy(u);
                int changes = last.policy() == null ? 0 : policy.countDifferences(last.policy());
                last = new IterationSnapshot(iteration, u, policy, delta, changes,
                        "обновление Беллмана, max|Uᵢ − Uᵢ₋₁| = " + ru.spbstu.mdp.util.Fmt.sci(delta));
                history.add(last);
            }
            if (converged) {
                break;
            }
        }
        Utilities utilities = new Utilities(current);
        return new SolverResult(name(), utilities, mdp.greedyPolicy(utilities), iteration, converged,
                history, System.nanoTime() - started);
    }
}
