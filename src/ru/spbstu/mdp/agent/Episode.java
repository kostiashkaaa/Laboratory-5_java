package ru.spbstu.mdp.agent;

import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.world.StepRecord;

import java.util.List;

/**
 * Один эпизод: путь агента от старта до конечного состояния (или до лимита шагов).
 *
 * @param totalReward      R(s₀) + R(s₁) + … + R(s_T)
 * @param discountedReturn Σ γᵗ·R(sₜ) — именно её математическое ожидание равно U^π(старт)
 */
public record Episode(List<StepRecord> steps, Position finalPosition, double totalReward,
                      double discountedReturn, boolean reachedTerminal) {

    public Episode {
        steps = List.copyOf(steps);
    }

    public int length() {
        return steps.size();
    }
}
