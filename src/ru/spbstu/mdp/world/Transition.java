package ru.spbstu.mdp.world;

import ru.spbstu.mdp.model.Position;

/** Один исход действия: агент попадает в {@code next} с вероятностью {@code probability} — T(s, a, s'). */
public record Transition(Position next, double probability) {
}
