package ru.spbstu.mdp.model;

/**
 * Клетка мира. Координаты как в уравнении Беллмана из задания: {@code x} — номер столбца,
 * {@code y} — номер строки, обе считаются с 1, клетка (1,1) — левый нижний угол.
 * <p>
 * В тексте задания клетки «(3,4) = +1» и «(2,4) = −1» записаны как (строка, столбец);
 * в принятой здесь записи (столбец, строка) это (4,3) и (4,2).
 */
public record Position(int x, int y) {

    /** Соседняя клетка в направлении {@code direction} (без проверки границ и стен). */
    public Position step(Action direction) {
        return new Position(x + direction.dx(), y + direction.dy());
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
