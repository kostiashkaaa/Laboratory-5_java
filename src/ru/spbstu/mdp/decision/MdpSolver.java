package ru.spbstu.mdp.decision;

/** Алгоритм поиска оптимальной стратегии для MDP. */
public interface MdpSolver {

    String name();

    SolverResult solve(Mdp mdp);
}
