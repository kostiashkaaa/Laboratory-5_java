package ru.spbstu.mdp.decision;

/** Доступные алгоритмы (для выбора в GUI и консоли). */
public enum Algorithm {
    POLICY_ITERATION("Итерация по стратегиям"),
    VALUE_ITERATION("Итерация по значениям");

    private final String title;

    Algorithm(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /** @param seed зерно для случайной начальной стратегии (используется итерацией по стратегиям) */
    public MdpSolver createSolver(long seed) {
        return this == POLICY_ITERATION ? new PolicyIteration(seed) : new ValueIteration();
    }

    @Override
    public String toString() {
        return title;
    }
}
