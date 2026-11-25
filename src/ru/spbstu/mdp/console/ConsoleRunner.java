package ru.spbstu.mdp.console;

import ru.spbstu.mdp.Options;
import ru.spbstu.mdp.agent.Episode;
import ru.spbstu.mdp.agent.EpisodeStatistics;
import ru.spbstu.mdp.agent.MdpAgent;
import ru.spbstu.mdp.decision.Algorithm;
import ru.spbstu.mdp.decision.IterationSnapshot;
import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.decision.PolicyEvaluator;
import ru.spbstu.mdp.decision.SolverResult;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.util.Fmt;
import ru.spbstu.mdp.view.TextRenderer;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.StepRecord;
import ru.spbstu.mdp.world.TransitionModel;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Текстовый режим: расчёт стратегии, таблицы полезностей, демонстрационный эпизод и проверка Монте-Карло. */
public final class ConsoleRunner {

    private ConsoleRunner() {
    }

    public static void run(Options o, PrintStream out) {
        GridWorld world = GridWorld.classic(o.reward);
        Mdp mdp = new Mdp(world, new TransitionModel(o.forward), o.gamma);

        out.println("=== Лабораторная работа 5: агент в стохастической среде (MDP) ===");
        out.println("Мир " + world.width() + "×" + world.height() + ", старт " + world.start()
                + ", координаты (столбец, строка), (1,1) — левый нижний угол");
        out.println("R(s) = " + Fmt.signed(o.reward) + ", γ = " + Fmt.num(o.gamma)
                + ", P(вперёд) = " + Fmt.num(mdp.model().forward())
                + ", P(снос влево) = P(снос вправо) = " + Fmt.num(mdp.model().side()));
        out.println();
        out.print(TextRenderer.world(world));
        out.println();

        List<Algorithm> algorithms = switch (o.algorithm) {
            case "pi" -> List.of(Algorithm.POLICY_ITERATION);
            case "vi" -> List.of(Algorithm.VALUE_ITERATION);
            default -> List.of(Algorithm.POLICY_ITERATION, Algorithm.VALUE_ITERATION);
        };
        Map<Algorithm, SolverResult> results = new LinkedHashMap<>();
        MdpAgent agent = null;
        for (Algorithm algorithm : algorithms) {
            MdpAgent a = new MdpAgent(mdp, mdp.createEnvironment(o.seed));
            SolverResult r = a.computePolicy(algorithm.createSolver(o.seed));
            results.put(algorithm, r);
            if (agent == null) {
                agent = a;
            }
            printResult(out, world, r);
        }

        if (results.size() == 2) {
            SolverResult pi = results.get(Algorithm.POLICY_ITERATION);
            SolverResult vi = results.get(Algorithm.VALUE_ITERATION);
            out.println("--- Сверка алгоритмов ---");
            out.println("max |U_PI − U_VI| = " + Fmt.sci(pi.utilities().maxDifference(vi.utilities())));
            out.println("Клеток с разными действиями: " + pi.policy().countDifferences(vi.policy()));
            out.println();
        }

        SolverResult main = results.values().iterator().next();
        Optional<Utilities> exact = new PolicyEvaluator().exact(mdp, main.policy());
        out.println("--- Проверка расчёта полезности стратегии ---");
        exact.ifPresentOrElse(
                u -> out.println("Точное решение СЛАУ U = R + γ·P_π·U, max |U_точн − U| = "
                        + Fmt.sci(u.maxDifference(main.utilities()))),
                () -> out.println("СЛАУ вырождена: стратегия не из всех клеток ведёт к выходу"));
        out.println();

        out.println("--- Выбор действия в стартовой клетке (уравнение Беллмана) ---");
        out.println(TextRenderer.decision(mdp, main.utilities(), main.policy(), world.start()));
        out.println();

        out.println("--- Демонстрационный эпизод (seed " + o.seed + ") ---");
        Episode episode = agent.runEpisode(o.maxSteps);
        for (StepRecord step : episode.steps()) {
            out.println(step.describe());
        }
        out.println(episode.reachedTerminal()
                ? "Итог: выход " + episode.finalPosition() + " за " + episode.length() + " шагов, сумма наград "
                + Fmt.fixed(episode.totalReward(), 2)
                : "Итог: эпизод прерван по лимиту " + o.maxSteps + " шагов");
        out.println();

        EpisodeStatistics stats = agent.simulate(o.episodes, o.seed + 1, o.maxSteps);
        printStatistics(out, stats, main.utilities().get(world.start()), world.start());

        if (o.riskDemo) {
            riskDemo(out, o);
        }
    }

    private static void printResult(PrintStream out, GridWorld world, SolverResult r) {
        out.println("=== " + r.algorithm() + " ===");
        out.println("Итераций: " + r.iterations() + (r.converged() ? " (сошлось)" : " (НЕ сошлось — достигнут лимит)")
                + ", время " + Fmt.fixed(r.elapsedMillis(), 2) + " мс");
        List<IterationSnapshot> shown = selectForTrace(r.history());
        for (IterationSnapshot s : shown) {
            if (s == null) {
                out.println("   …");
                continue;
            }
            out.println(String.format("  i = %4d  U%s = %8s  %s", s.iteration(), world.start(),
                    Fmt.fixed(s.utilities().get(world.start()), 4), s.note()));
        }
        out.println("Полезности U(s):");
        out.print(TextRenderer.utilities(world, r.utilities()));
        out.println("Оптимальная стратегия π*(s):");
        out.print(TextRenderer.policy(world, r.policy()));
        out.println();
    }

    /** Первые 6 и последние 3 итерации ({@code null} — пропуск). */
    private static List<IterationSnapshot> selectForTrace(List<IterationSnapshot> history) {
        if (history.size() <= 10) {
            return history;
        }
        List<IterationSnapshot> shown = new ArrayList<>(history.subList(0, 6));
        shown.add(null);
        shown.addAll(history.subList(history.size() - 3, history.size()));
        return shown;
    }

    static void printStatistics(PrintStream out, EpisodeStatistics stats, double expected, Position start) {
        out.println("--- Проверка методом Монте-Карло: " + stats.episodes() + " эпизодов ---");
        out.println("Средняя сумма наград Σγᵗ·R(sₜ) = " + Fmt.fixed(stats.meanReturn(), 4)
                + " ± " + Fmt.fixed(stats.confidence95(), 4) + " (95%)");
        out.println("Расчётная полезность U" + start + " = " + Fmt.fixed(expected, 4)
                + (Math.abs(stats.meanReturn() - expected) <= Math.max(stats.confidence95(), 1e-9) ? " — в пределах доверительного интервала"
                : " — вне 95%-интервала (возможна случайность, увеличьте --episodes)"));
        out.println("Среднее число шагов: " + Fmt.fixed(stats.meanSteps(), 2));
        stats.exits().forEach((p, n) -> out.println("Выход " + p + ": " + n + " раз ("
                + Fmt.fixed(100.0 * n / stats.episodes(), 1) + "%)"));
        if (stats.timeouts() > 0) {
            out.println("Прервано по лимиту шагов: " + stats.timeouts());
        }
        out.println();
    }

    /** Как равновесие между риском и вознаграждением меняется в зависимости от R(s). */
    private static void riskDemo(PrintStream out, Options o) {
        out.println("=== Зависимость оптимальной стратегии от R(s) ===");
        double[][] cases = {{-2.0, 1.0}, {-0.3, 1.0}, {-0.04, 1.0}, {-0.01, 1.0}, {0.0, 1.0}, {0.5, 0.9}};
        for (double[] c : cases) {
            Mdp mdp = new Mdp(GridWorld.classic(c[0]), new TransitionModel(o.forward), c[1]);
            SolverResult r = Algorithm.POLICY_ITERATION.createSolver(o.seed).solve(mdp);
            out.println("R(s) = " + Fmt.signed(c[0]) + ", γ = " + Fmt.num(c[1]) + "  (U(1,1) = "
                    + Fmt.fixed(r.utilities().get(new Position(1, 1)), 3) + ")");
            out.print(TextRenderer.policy(mdp.world(), r.policy()));
            out.println();
        }
    }
}
