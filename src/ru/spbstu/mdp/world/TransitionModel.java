package ru.spbstu.mdp.world;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Position;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Стохастическая модель движения (рис. 1 задания): выбранное направление выполняется с вероятностью
 * {@code forward} (по умолчанию 0.8), с вероятностью (1 − forward)/2 агента сносит ветром влево и
 * столько же — вправо (перпендикулярно движению). Если в результирующем направлении стена или граница,
 * агент остаётся на месте.
 */
public final class TransitionModel {

    public static final double DEFAULT_FORWARD = 0.8;

    private final double forward;
    private final double side;

    public TransitionModel(double forward) {
        if (!(forward >= 0.0 && forward <= 1.0)) {
            throw new IllegalArgumentException("Вероятность движения в выбранном направлении должна лежать в [0; 1], получено " + forward);
        }
        this.forward = forward;
        this.side = (1.0 - forward) / 2.0;
    }

    public static TransitionModel standard() {
        return new TransitionModel(DEFAULT_FORWARD);
    }

    public double forward() {
        return forward;
    }

    /** Вероятность сноса в каждую из двух перпендикулярных сторон. */
    public double side() {
        return side;
    }

    /**
     * T(s, a, ·): список различных клеток-исходов с суммарными вероятностями (нулевые исходы опущены).
     * Для конечного состояния список пуст — действий там нет.
     */
    public List<Transition> transitions(GridWorld world, Position s, Action a) {
        if (world.isTerminal(s)) {
            return List.of();
        }
        Map<Position, Double> outcomes = new LinkedHashMap<>();
        add(outcomes, destination(world, s, a), forward);
        add(outcomes, destination(world, s, a.turnLeft()), side);
        add(outcomes, destination(world, s, a.turnRight()), side);
        List<Transition> result = new ArrayList<>(outcomes.size());
        outcomes.forEach((next, p) -> result.add(new Transition(next, p)));
        return List.copyOf(result);
    }

    /** Куда приводит фактическое движение в направлении {@code direction}: соседняя клетка или та же (удар о стену). */
    public static Position destination(GridWorld world, Position s, Action direction) {
        Position next = s.step(direction);
        return world.isBlocked(next) ? s : next;
    }

    /** Розыгрыш фактического направления движения при выбранном {@code intended}. */
    public Action sampleDirection(Action intended, Random random) {
        double u = random.nextDouble();
        if (u < forward) {
            return intended;
        }
        if (u < forward + side) {
            return intended.turnLeft();
        }
        return intended.turnRight();
    }

    private static void add(Map<Position, Double> outcomes, Position next, double probability) {
        if (probability > 0) {
            outcomes.merge(next, probability, Double::sum);
        }
    }
}
