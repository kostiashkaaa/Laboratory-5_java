package ru.spbstu.mdp.agent;

import ru.spbstu.mdp.model.Position;

import java.util.Map;

/**
 * Статистика серии эпизодов.
 *
 * @param meanReturn        среднее Σ γᵗ·R(sₜ) — оценка U^π(старт) методом Монте-Карло
 * @param standardDeviation выборочное стандартное отклонение суммы наград
 * @param exits             сколько раз эпизод закончился в каждом конечном состоянии
 * @param timeouts          число эпизодов, прерванных по лимиту шагов
 */
public record EpisodeStatistics(int episodes, double meanReturn, double standardDeviation, double meanSteps,
                                Map<Position, Integer> exits, int timeouts) {

    public EpisodeStatistics {
        exits = Map.copyOf(exits);
    }

    public double standardError() {
        return episodes > 1 ? standardDeviation / Math.sqrt(episodes) : 0;
    }

    /** Полуширина 95-процентного доверительного интервала для среднего. */
    public double confidence95() {
        return 1.96 * standardError();
    }
}
