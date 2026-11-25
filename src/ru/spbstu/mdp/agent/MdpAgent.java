package ru.spbstu.mdp.agent;

import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.decision.MdpSolver;
import ru.spbstu.mdp.decision.PolicyEvaluator;
import ru.spbstu.mdp.decision.SolverResult;
import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.world.StepRecord;
import ru.spbstu.mdp.world.StochasticEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Агент в стохастической среде. Знает модель мира (MDP) и свою клетку, но не знает, куда его снесёт ветер.
 * <ul>
 *     <li>{@link #computePolicy} — расчёт оптимальной стратегии (итерация по стратегиям / по значениям);</li>
 *     <li>{@link #decideDirection} — принятие решения о направлении движения: π*(s);</li>
 *     <li>{@link #move} — перемещение агента (исход разыгрывает среда);</li>
 *     <li>{@link #evaluatePolicyUtility} — расчёт полезности стратегии U^π.</li>
 * </ul>
 */
public final class MdpAgent {

    private final Mdp mdp;
    private final StochasticEnvironment environment;
    private final PolicyEvaluator evaluator = new PolicyEvaluator();
    private SolverResult plan;

    public MdpAgent(Mdp mdp, StochasticEnvironment environment) {
        this.mdp = Objects.requireNonNull(mdp, "mdp");
        this.environment = Objects.requireNonNull(environment, "environment");
        if (environment.world() != mdp.world()) {
            throw new IllegalArgumentException("Среда и модель агента должны описывать один и тот же мир");
        }
    }

    /** Планирование: поиск оптимальной стратегии выбранным алгоритмом. */
    public SolverResult computePolicy(MdpSolver solver) {
        plan = solver.solve(mdp);
        return plan;
    }

    public boolean hasPolicy() {
        return plan != null;
    }

    public SolverResult plan() {
        requirePlan();
        return plan;
    }

    public Policy policy() {
        requirePlan();
        return plan.policy();
    }

    public Utilities utilities() {
        requirePlan();
        return plan.utilities();
    }

    /**
     * Принятие решения: действие с максимальной ожидаемой полезностью в текущей клетке,
     * π*(s) = argmax_a Σ T(s, a, s')·U(s'). В конечном состоянии — {@code null}.
     */
    public Action decideDirection() {
        requirePlan();
        Position s = environment.position();
        return mdp.isTerminal(s) ? null : plan.policy().get(s);
    }

    /** Перемещение: выполнить принятое решение в среде. */
    public StepRecord move() {
        Action a = decideDirection();
        if (a == null) {
            throw new IllegalStateException("Агент уже в конечном состоянии " + position());
        }
        return environment.execute(a);
    }

    /** Новый эпизод с начальной клетки. */
    public void startNewEpisode() {
        environment.reset();
    }

    /** Полный эпизод от старта до выхода (или до лимита шагов, если стратегия не ведёт к выходу). */
    public Episode runEpisode(int maxSteps) {
        environment.reset();
        List<StepRecord> steps = new ArrayList<>();
        while (!environment.isTerminal() && environment.steps() < maxSteps) {
            steps.add(move());
        }
        return new Episode(steps, environment.position(), environment.totalReward(),
                environment.discountedReturn(), environment.isTerminal());
    }

    /**
     * Расчёт полезности стратегии U^π: точное решение СЛАУ, а если она вырождена
     * (стратегия при γ = 1 не ведёт к выходу) — итеративная оценка с лимитом.
     */
    public Utilities evaluatePolicyUtility(Policy policy) {
        return evaluator.exact(mdp, policy).orElseGet(() -> evaluator.iterative(mdp, policy,
                Utilities.zeros(mdp.world().width(), mdp.world().height())).utilities());
    }

    /** U^π(s) для найденной стратегии. */
    public double policyUtility(Position s) {
        return evaluatePolicyUtility(policy()).get(s);
    }

    /** Ожидаемая полезность каждого действия в клетке s: Σ T(s, a, s')·U(s') (как в примере выбора действия). */
    public Map<Action, Double> actionUtilities(Position s) {
        requirePlan();
        if (mdp.isTerminal(s)) {
            return Collections.emptyMap();
        }
        Map<Action, Double> result = new EnumMap<>(Action.class);
        for (Action a : Action.values()) {
            result.put(a, mdp.expectedUtility(s, a, plan.utilities()));
        }
        return result;
    }

    /** Серия эпизодов в отдельной среде (текущий эпизод агента не затрагивается). */
    public EpisodeStatistics simulate(int episodes, long seed, int maxSteps) {
        return MonteCarlo.simulate(mdp, policy(), episodes, seed, maxSteps);
    }

    public Position position() {
        return environment.position();
    }

    public Mdp mdp() {
        return mdp;
    }

    public StochasticEnvironment environment() {
        return environment;
    }

    private void requirePlan() {
        if (plan == null) {
            throw new IllegalStateException("Стратегия ещё не рассчитана: вызовите computePolicy()");
        }
    }
}
