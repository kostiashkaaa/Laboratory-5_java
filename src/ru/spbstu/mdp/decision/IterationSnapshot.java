package ru.spbstu.mdp.decision;

import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Utilities;

/**
 * Состояние алгоритма после одной итерации (для пошагового показа в GUI и трассировки в консоли).
 *
 * @param policy        стратегия на этой итерации; {@code null}, если ещё не определена (U₀ у итерации по значениям)
 * @param delta         max |Uᵢ − Uᵢ₋₁|; {@code NaN} для нулевой итерации
 * @param policyChanges сколько действий изменилось по сравнению с предыдущей стратегией
 */
public record IterationSnapshot(int iteration, Utilities utilities, Policy policy, double delta,
                                int policyChanges, String note) {
}
