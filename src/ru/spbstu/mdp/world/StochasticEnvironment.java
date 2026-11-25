package ru.spbstu.mdp.world;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Position;

import java.util.Objects;
import java.util.Random;

/**
 * Стохастическая среда: хранит положение агента и разыгрывает исход каждого действия.
 * Среда полностью наблюдаема — агент всегда знает свою клетку ({@link #position()}).
 * <p>
 * Сумма наград эпизода считается как в определении полезности: R(s₀) + R(s₁) + … + R(s_T),
 * дисконтированная сумма — Σ γᵗ·R(sₜ).
 */
public final class StochasticEnvironment {

    private final GridWorld world;
    private final TransitionModel model;
    private final double gamma;
    private final Random random;

    private Position position;
    private int steps;
    private double totalReward;
    private double discountedReturn;
    private double discount;

    public StochasticEnvironment(GridWorld world, TransitionModel model, double gamma, long seed) {
        this.world = Objects.requireNonNull(world, "world");
        this.model = Objects.requireNonNull(model, "model");
        if (!(gamma > 0 && gamma <= 1)) {
            throw new IllegalArgumentException("γ должен лежать в (0; 1], получено " + gamma);
        }
        this.gamma = gamma;
        this.random = new Random(seed);
        reset();
    }

    /** Новый эпизод: агент в стартовой клетке, получена награда R(старт). */
    public void reset() {
        position = world.start();
        steps = 0;
        double r = world.reward(position);
        totalReward = r;
        discountedReturn = r;
        discount = 1.0;
    }

    /** Выполнить выбранное агентом действие; фактическое направление разыгрывается случайно. */
    public StepRecord execute(Action intended) {
        Objects.requireNonNull(intended, "intended");
        if (isTerminal()) {
            throw new IllegalStateException("Эпизод завершён: агент в конечном состоянии " + position);
        }
        Action actual = model.sampleDirection(intended, random);
        Position from = position;
        Position to = TransitionModel.destination(world, from, actual);
        steps++;
        discount *= gamma;
        double r = world.reward(to);
        totalReward += r;
        discountedReturn += discount * r;
        position = to;
        return new StepRecord(steps, from, intended, actual, to, r, totalReward, world.isTerminal(to));
    }

    public GridWorld world() {
        return world;
    }

    public Position position() {
        return position;
    }

    public boolean isTerminal() {
        return world.isTerminal(position);
    }

    public int steps() {
        return steps;
    }

    public double totalReward() {
        return totalReward;
    }

    public double discountedReturn() {
        return discountedReturn;
    }
}
