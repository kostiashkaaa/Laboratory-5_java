package ru.spbstu.mdp.world;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Position;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Генератор мира произвольного размера по образцу мира из задания: старт в левом нижнем углу (1,1),
 * выход +1 в правом верхнем углу, выход −1 под ним. Остальное выбирается случайно:
 * стены занимают около 12 % клеток, в больших мирах добавляются дополнительные ловушки −1
 * (одна на каждые 20 клеток).
 * <p>
 * Мир принимается, только если из старта можно дойти до +1, не заходя в выходы, и из каждой клетки
 * достижим какой-нибудь выход — тогда полезность ограничена и при γ = 1.
 */
public final class WorldGenerator {

    public static final double WALL_SHARE = 0.12;
    public static final int CELLS_PER_TRAP = 20;
    private static final int ATTEMPTS = 500;

    private WorldGenerator() {
    }

    public static GridWorld generate(int width, int height, double stepReward, long seed) {
        Random random = new Random(seed);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            GridWorld world = layout(width, height, stepReward, random, true).build();
            if (isValid(world)) {
                return world;
            }
        }
        return layout(width, height, stepReward, random, false).build(); // без стен мир всегда корректен
    }

    /** Число стен в мире заданного размера. */
    public static int wallCount(int width, int height) {
        return (int) Math.round(WALL_SHARE * width * height);
    }

    /** Число дополнительных ловушек −1 (кроме ловушки под выходом +1). */
    public static int extraTrapCount(int width, int height) {
        return width * height / CELLS_PER_TRAP;
    }

    private static GridWorld.Builder layout(int width, int height, double stepReward, Random random, boolean walls) {
        GridWorld.Builder b = GridWorld.builder(width, height).stepReward(stepReward).start(1, 1);
        Position start = new Position(1, 1);
        Position goal = new Position(width, height);
        Position trap = new Position(width, height - 1);
        b.terminal(goal.x(), goal.y(), 1.0);
        if (!trap.equals(start)) {
            b.terminal(trap.x(), trap.y(), -1.0);
        }
        List<Position> free = new ArrayList<>();
        for (int x = 1; x <= width; x++) {
            for (int y = 1; y <= height; y++) {
                Position p = new Position(x, y);
                if (!p.equals(start) && !p.equals(goal) && !p.equals(trap)) {
                    free.add(p);
                }
            }
        }
        Collections.shuffle(free, random);
        int k = 0;
        for (int i = 0; i < extraTrapCount(width, height) && k < free.size(); i++, k++) {
            b.terminal(free.get(k).x(), free.get(k).y(), -1.0);
        }
        int wallsToPlace = walls ? wallCount(width, height) : 0;
        for (int i = 0; i < wallsToPlace && k < free.size(); i++, k++) {
            b.wall(free.get(k).x(), free.get(k).y());
        }
        return b;
    }

    /** Из старта можно дойти до +1, не проходя через выходы, и из каждой клетки достижим выход. */
    public static boolean isValid(GridWorld world) {
        Position start = world.start();
        // 1. Путь от старта до выхода +1 только по обычным клеткам.
        Set<Position> seen = new HashSet<>();
        Deque<Position> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        boolean goalReached = false;
        while (!queue.isEmpty()) {
            Position p = queue.poll();
            if (world.isTerminal(p)) {
                if (world.reward(p) > 0) {
                    goalReached = true;
                }
                continue;
            }
            for (Action a : Action.values()) {
                Position n = p.step(a);
                if (!world.isBlocked(n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        if (!goalReached) {
            return false;
        }
        // 2. Из каждой обычной клетки достижим какой-нибудь выход (обратный обход от выходов).
        Set<Position> reach = new HashSet<>(world.terminals());
        queue.addAll(world.terminals());
        while (!queue.isEmpty()) {
            Position p = queue.poll();
            for (Action a : Action.values()) {
                Position n = p.step(a);
                if (!world.isBlocked(n) && !world.isTerminal(n) && reach.add(n)) {
                    queue.add(n);
                }
            }
        }
        return reach.size() == world.states().size();
    }
}
