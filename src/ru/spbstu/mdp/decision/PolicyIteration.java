package ru.spbstu.mdp.decision;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Итерация по стратегиям — чередование двух фаз из задания:
 * <ol>
 *     <li>Оценка стратегии: Uᵢ = U^{πᵢ}, U_{i+1}(s) = R(s) + γ·Σ T(s, πᵢ(s), s')·Uᵢ(s');</li>
 *     <li>Улучшение стратегии: π_{i+1}(s) = argmax_a Σ T(s, a, s')·Uᵢ(s').</li>
 * </ol>
 * Алгоритм останавливается, когда улучшение не меняет ни одного действия.
 * Начальная стратегия π₀ — случайная (с заданным зерном, чтобы результат воспроизводился).
 */
public final class PolicyIteration implements MdpSolver {

    /** Действие меняется, только если новое лучше текущего больше чем на эту величину (защита от зацикливания на равных). */
    public static final double IMPROVEMENT_EPSILON = 1e-9;
    public static final int DEFAULT_MAX_ITERATIONS = 1_000;

    private final long seed;
    private final int maxIterations;
    private final PolicyEvaluator evaluator;

    public PolicyIteration(long seed) {
        this(seed, DEFAULT_MAX_ITERATIONS, new PolicyEvaluator());
    }

    public PolicyIteration(long seed, int maxIterations, PolicyEvaluator evaluator) {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("Лимит итераций должен быть положительным");
        }
        this.seed = seed;
        this.maxIterations = maxIterations;
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public String name() {
        return Algorithm.POLICY_ITERATION.title();
    }

    /** Случайная стратегия: в каждой неконечной клетке — случайное из четырёх действий. */
    public static Policy randomPolicy(Mdp mdp, Random random) {
        Action[] all = Action.values();
        Action[][] actions = new Action[mdp.world().width()][mdp.world().height()];
        for (Position s : mdp.states()) {
            if (!mdp.isTerminal(s)) {
                actions[s.x() - 1][s.y() - 1] = all[random.nextInt(all.length)];
            }
        }
        return new Policy(actions);
    }

    @Override
    public SolverResult solve(Mdp mdp) {
        long started = System.nanoTime();
        Policy policy = randomPolicy(mdp, new Random(seed));
        Utilities utilities = Utilities.zeros(mdp.world().width(), mdp.world().height());
        List<IterationSnapshot> history = new ArrayList<>();

        int iteration = 0;
        int changes = 0;
        boolean converged = false;
        while (iteration < maxIterations) {
            // 1. Оценка стратегии (начинаем с предыдущих значений — так быстрее сходится).
            PolicyEvaluator.Result evaluation = evaluator.iterative(mdp, policy, utilities);
            double delta = evaluation.utilities().maxDifference(utilities);
            utilities = evaluation.utilities();
            String note = (iteration == 0 ? "π₀ — случайная начальная стратегия" : "после улучшения изменено действий: " + changes)
                    + "; оценка U^π: " + evaluation.sweeps() + " итер." + (evaluation.converged() ? "" : " (лимит, стратегия не ведёт к выходу)");
            history.add(new IterationSnapshot(iteration, utilities, policy, delta, changes, note));
            iteration++;

            // 2. Улучшение стратегии.
            Action[][] improved = policy.toArray();
            changes = 0;
            for (Position s : mdp.states()) {
                if (mdp.isTerminal(s)) {
                    continue;
                }
                Action current = policy.get(s);
                Action bestAction = current;
                double best = mdp.expectedUtility(s, current, utilities);
                for (Action a : Action.values()) {
                    double v = mdp.expectedUtility(s, a, utilities);
                    if (v > best + IMPROVEMENT_EPSILON) {
                        best = v;
                        bestAction = a;
                    }
                }
                if (bestAction != current) {
                    improved[s.x() - 1][s.y() - 1] = bestAction;
                    changes++;
                }
            }
            if (changes == 0 && evaluation.converged()) {
                converged = true;
                break;
            }
            policy = new Policy(improved);
        }
        return new SolverResult(name(), utilities, policy, iteration, converged, history, System.nanoTime() - started);
    }
}
