package ru.spbstu.mdp.decision;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.StochasticEnvironment;
import ru.spbstu.mdp.world.Transition;
import ru.spbstu.mdp.world.TransitionModel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Марковский процесс принятия решений: состояния S (клетки мира), действия A(s), модель переходов
 * T(s, a, s'), награда R(s) и коэффициент дисконтирования γ.
 * <p>
 * Конструктор проверяет, что полезности ограничены (требование задания «полезность ограничена»):
 * при γ = 1 запрещены R(s) &gt; 0 и клетки, из которых недостижимо ни одно конечное состояние.
 */
public final class Mdp {

    /** Допуск при сравнении полезностей действий; при равенстве выбирается действие, стоящее раньше в {@link Action}. */
    public static final double TIE_EPSILON = 1e-12;

    private final GridWorld world;
    private final TransitionModel model;
    private final double gamma;
    private final Map<Position, EnumMap<Action, List<Transition>>> transitions = new HashMap<>();

    public Mdp(GridWorld world, TransitionModel model, double gamma) {
        this.world = Objects.requireNonNull(world, "world");
        this.model = Objects.requireNonNull(model, "model");
        if (!(gamma > 0.0 && gamma <= 1.0)) {
            throw new IllegalArgumentException("Коэффициент дисконтирования γ должен лежать в (0; 1], получено " + gamma);
        }
        this.gamma = gamma;
        if (world.terminals().isEmpty()) {
            throw new IllegalArgumentException("В мире нет ни одного конечного состояния (+1 или −1)");
        }
        for (Position s : world.states()) {
            EnumMap<Action, List<Transition>> byAction = new EnumMap<>(Action.class);
            if (!world.isTerminal(s)) {
                for (Action a : Action.values()) {
                    byAction.put(a, model.transitions(world, s, a));
                }
            }
            transitions.put(s, byAction);
        }
        if (gamma == 1.0) {
            checkUtilitiesBounded();
        }
    }

    public static Mdp classic() {
        return new Mdp(GridWorld.classic(GridWorld.DEFAULT_STEP_REWARD), TransitionModel.standard(), 1.0);
    }

    private void checkUtilitiesBounded() {
        if (world.stepReward() > 0) {
            throw new IllegalArgumentException("При γ = 1 и R(s) > 0 полезность не ограничена: агенту выгодно никогда "
                    + "не заканчивать эпизод. Уменьшите γ или задайте R(s) ≤ 0.");
        }
        if (world.stepReward() == 0) {
            return;
        }
        // Обратный поиск: клетки, из которых хотя бы одно действие с ненулевой вероятностью ведёт к выходу.
        Set<Position> reach = new HashSet<>(world.terminals());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Position s : world.states()) {
                if (reach.contains(s)) {
                    continue;
                }
                boolean leads = transitions.get(s).values().stream()
                        .anyMatch(list -> list.stream().anyMatch(t -> reach.contains(t.next())));
                if (leads) {
                    reach.add(s);
                    changed = true;
                }
            }
        }
        List<Position> trapped = world.states().stream().filter(s -> !reach.contains(s)).toList();
        if (!trapped.isEmpty()) {
            throw new IllegalArgumentException("Из клеток " + trapped + " нельзя попасть ни в одно конечное состояние; "
                    + "при γ = 1 и R(s) < 0 их полезность равна −∞. Уберите стены или уменьшите γ.");
        }
    }

    /** Новая среда для этого MDP (с собственным генератором случайных чисел). */
    public StochasticEnvironment createEnvironment(long seed) {
        return new StochasticEnvironment(world, model, gamma, seed);
    }

    public GridWorld world() {
        return world;
    }

    public TransitionModel model() {
        return model;
    }

    public double gamma() {
        return gamma;
    }

    public List<Position> states() {
        return world.states();
    }

    public boolean isTerminal(Position s) {
        return world.isTerminal(s);
    }

    public double reward(Position s) {
        return world.reward(s);
    }

    /** A(s): все четыре действия для обычной клетки, пустое множество — для конечной. */
    public Set<Action> actions(Position s) {
        return java.util.Collections.unmodifiableSet(transitionsOf(s).keySet());
    }

    /** T(s, a, ·). */
    public List<Transition> transitions(Position s, Action a) {
        List<Transition> list = transitionsOf(s).get(a);
        if (list == null) {
            throw new IllegalArgumentException("В состоянии " + s + " действие " + a + " недоступно");
        }
        return list;
    }

    /** Ожидаемая полезность действия: Σ_{s'} T(s, a, s')·U(s'). */
    public double expectedUtility(Position s, Action a, Utilities u) {
        double sum = 0;
        for (Transition t : transitions(s, a)) {
            sum += t.probability() * u.get(t.next());
        }
        return sum;
    }

    /** То же для «сырой» таблицы {@code u[x-1][y-1]} (используется во внутренних циклах алгоритмов). */
    double expectedUtility(Position s, Action a, double[][] u) {
        double sum = 0;
        for (Transition t : transitions(s, a)) {
            Position n = t.next();
            sum += t.probability() * u[n.x() - 1][n.y() - 1];
        }
        return sum;
    }

    /** Q(s, a) = R(s) + γ·Σ T(s, a, s')·U(s'). */
    public double qValue(Position s, Action a, Utilities u) {
        return reward(s) + gamma * expectedUtility(s, a, u);
    }

    /** Действие с максимальной ожидаемой полезностью (принцип MEU); {@code null} в конечном состоянии. */
    public Action bestAction(Position s, Utilities u) {
        Action best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Action a : actions(s)) {
            double v = expectedUtility(s, a, u);
            if (v > bestValue + TIE_EPSILON) {
                best = a;
                bestValue = v;
            }
        }
        return best;
    }

    /** Жадная стратегия по таблице полезностей: π(s) = argmax_a Σ T(s, a, s')·U(s'). */
    public Policy greedyPolicy(Utilities u) {
        Action[][] actions = new Action[world.width()][world.height()];
        for (Position s : world.states()) {
            actions[s.x() - 1][s.y() - 1] = bestAction(s, u);
        }
        return new Policy(actions);
    }

    private EnumMap<Action, List<Transition>> transitionsOf(Position s) {
        EnumMap<Action, List<Transition>> map = transitions.get(s);
        if (map == null) {
            throw new IllegalArgumentException("Клетка " + s + " не является состоянием (стена или вне мира)");
        }
        return map;
    }
}
