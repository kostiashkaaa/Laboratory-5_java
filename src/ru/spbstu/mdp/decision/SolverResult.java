package ru.spbstu.mdp.decision;

import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Utilities;

import java.util.List;

/**
 * Результат работы алгоритма.
 *
 * @param iterations число выполненных итераций (оценок стратегии / обновлений Беллмана)
 * @param converged  достигнут ли критерий остановки до исчерпания лимита итераций
 * @param history    промежуточные состояния (первое — начальное, последнее — итоговое)
 */
public record SolverResult(String algorithm, Utilities utilities, Policy policy, int iterations,
                           boolean converged, List<IterationSnapshot> history, long elapsedNanos) {

    public SolverResult {
        history = List.copyOf(history);
    }

    public double elapsedMillis() {
        return elapsedNanos / 1e6;
    }
}
