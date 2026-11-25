package ru.spbstu.mdp.world;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.util.Fmt;

/**
 * Запись об одном шаге агента в среде.
 *
 * @param intended    направление, выбранное агентом
 * @param actual      фактическое направление движения (с учётом сноса ветром)
 * @param reward      R(s') полученная в новой клетке
 * @param totalReward сумма наград с начала эпизода (включая R(старт))
 */
public record StepRecord(int step, Position from, Action intended, Action actual, Position to,
                         double reward, double totalReward, boolean terminal) {

    public boolean drifted() {
        return actual != intended;
    }

    public boolean bumped() {
        return from.equals(to);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Шаг ").append(step).append(": ").append(from)
                .append(" решение ").append(intended.arrow()).append(' ').append(intended.title());
        if (drifted()) {
            sb.append(", снесло ветром ").append(actual.arrow()).append(' ').append(actual.title());
        }
        if (bumped()) {
            sb.append(" (удар о стену, остался на месте)");
        }
        sb.append(" → ").append(to)
                .append(", R = ").append(Fmt.signed(reward))
                .append(", сумма = ").append(Fmt.fixed(totalReward, 2));
        if (terminal) {
            sb.append(" — конечное состояние");
        }
        return sb.toString();
    }
}
