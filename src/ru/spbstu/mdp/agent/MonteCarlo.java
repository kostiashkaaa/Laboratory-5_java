package ru.spbstu.mdp.agent;

import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.world.StochasticEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

/** Моделирование серии эпизодов для проверки: среднее Σ γᵗ·R(sₜ) должно совпасть с U^π(старт). */
public final class MonteCarlo {

    private MonteCarlo() {
    }

    public static EpisodeStatistics simulate(Mdp mdp, Policy policy, int episodes, long seed, int maxSteps) {
        if (episodes < 1 || maxSteps < 1) {
            throw new IllegalArgumentException("Число эпизодов и лимит шагов должны быть положительными");
        }
        StochasticEnvironment env = mdp.createEnvironment(seed);
        Map<Position, Integer> exits = new LinkedHashMap<>();
        for (Position t : mdp.world().terminals()) {
            exits.put(t, 0);
        }
        double mean = 0;
        double m2 = 0;
        long totalSteps = 0;
        int timeouts = 0;
        for (int i = 1; i <= episodes; i++) {
            env.reset();
            while (!env.isTerminal() && env.steps() < maxSteps) {
                Action a = policy.get(env.position());
                env.execute(a);
            }
            if (env.isTerminal()) {
                exits.merge(env.position(), 1, Integer::sum);
            } else {
                timeouts++;
            }
            totalSteps += env.steps();
            // Алгоритм Уэлфорда — устойчивое вычисление среднего и дисперсии.
            double x = env.discountedReturn();
            double d = x - mean;
            mean += d / i;
            m2 += d * (x - mean);
        }
        double sd = episodes > 1 ? Math.sqrt(m2 / (episodes - 1)) : 0;
        return new EpisodeStatistics(episodes, mean, sd, (double) totalSteps / episodes, exits, timeouts);
    }
}
