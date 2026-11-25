package ru.spbstu.mdp;

import ru.spbstu.mdp.agent.Episode;
import ru.spbstu.mdp.agent.EpisodeStatistics;
import ru.spbstu.mdp.agent.MdpAgent;
import ru.spbstu.mdp.agent.MonteCarlo;
import ru.spbstu.mdp.decision.Algorithm;
import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.decision.PolicyEvaluator;
import ru.spbstu.mdp.decision.PolicyIteration;
import ru.spbstu.mdp.decision.SolverResult;
import ru.spbstu.mdp.decision.ValueIteration;
import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.util.Fmt;
import ru.spbstu.mdp.view.TextRenderer;
import ru.spbstu.mdp.world.CellType;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.StepRecord;
import ru.spbstu.mdp.world.StochasticEnvironment;
import ru.spbstu.mdp.world.Transition;
import ru.spbstu.mdp.world.TransitionModel;
import ru.spbstu.mdp.world.WorldGenerator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Самопроверка без внешних библиотек (запуск: {@code ./run.sh test}).
 * Код возврата 0 — все проверки пройдены, 1 — есть ошибки.
 */
public final class SelfTest {

    private int passed;
    private final List<String> failures = new ArrayList<>();
    private final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    /** Полезности из таблицы задания (мир 4×3, R(s) = −0.04, γ = 1). */
    private static final Map<Position, Double> EXPECTED_UTILITIES = new LinkedHashMap<>();
    /** Оптимальная стратегия с рисунка задания. */
    private static final Map<Position, Action> EXPECTED_POLICY = new LinkedHashMap<>();

    static {
        EXPECTED_UTILITIES.put(p(1, 3), 0.812);
        EXPECTED_UTILITIES.put(p(2, 3), 0.868);
        EXPECTED_UTILITIES.put(p(3, 3), 0.918);
        EXPECTED_UTILITIES.put(p(1, 2), 0.762);
        EXPECTED_UTILITIES.put(p(3, 2), 0.660);
        EXPECTED_UTILITIES.put(p(1, 1), 0.705);
        EXPECTED_UTILITIES.put(p(2, 1), 0.655);
        EXPECTED_UTILITIES.put(p(3, 1), 0.611);
        EXPECTED_UTILITIES.put(p(4, 1), 0.388);

        EXPECTED_POLICY.put(p(1, 3), Action.RIGHT);
        EXPECTED_POLICY.put(p(2, 3), Action.RIGHT);
        EXPECTED_POLICY.put(p(3, 3), Action.RIGHT);
        EXPECTED_POLICY.put(p(1, 2), Action.UP);
        EXPECTED_POLICY.put(p(3, 2), Action.UP);
        EXPECTED_POLICY.put(p(1, 1), Action.UP);
        EXPECTED_POLICY.put(p(2, 1), Action.LEFT);
        EXPECTED_POLICY.put(p(3, 1), Action.LEFT);
        EXPECTED_POLICY.put(p(4, 1), Action.LEFT);
    }

    public static void main(String[] args) {
        SelfTest t = new SelfTest();
        t.run("Действия и повороты", t::actions);
        t.run("Мир из задания", t::classicWorld);
        t.run("Модель переходов (рис. 1 и уравнение для (1,1))", t::transitionModel);
        t.run("Итерация по значениям = таблица задания", t::valueIterationMatchesTable);
        t.run("Итерация по стратегиям = таблица и рисунок задания", t::policyIterationMatchesTable);
        t.run("Независимость от начальной стратегии π₀", t::policyIterationSeeds);
        t.run("Уравнение Беллмана в найденной точке", t::bellmanFixedPoint);
        t.run("Оценка стратегии: точная и итеративная", t::policyEvaluation);
        t.run("Агент: решение, перемещение, полезность стратегии", t::agent);
        t.run("Риск и вознаграждение при разных R(s)", t::riskReward);
        t.run("Дисконтирование γ < 1", t::discounting);
        t.run("Ограниченность полезности и проверки входных данных", t::validation);
        t.run("Стохастичность среды: частоты сноса ветром", t::sampling);
        t.run("Монте-Карло: средняя сумма наград = U(старт)", t::monteCarlo);
        t.run("Эпизоды: сумма наград, воспроизводимость", t::episodes);
        t.run("Редактор мира (Builder)", t::builder);
        t.run("Произвольные миры: PI = VI", t::randomWorlds);
        t.run("Генератор мира", t::generator);
        t.run("Текстовый вывод и консольный режим", t::textOutput);
        t.run("Разбор аргументов командной строки", t::options);
        t.run("Отрисовка визуализации без окна", t::rendering);
        t.summary();
    }

    // ------------------------------------------------------------------ проверки

    private void actions() {
        for (Action a : Action.values()) {
            check(a + ": left∘right = id", a.turnLeft().turnRight() == a);
            check(a + ": четыре поворота влево = id", a.turnLeft().turnLeft().turnLeft().turnLeft() == a);
            check(a + ": два поворота = противоположное", a.turnLeft().turnLeft() == a.opposite());
            check(a + ": поворот перпендикулярен", a.dx() * a.turnLeft().dx() + a.dy() * a.turnLeft().dy() == 0);
        }
        check("UP повернуть влево = LEFT", Action.UP.turnLeft() == Action.LEFT);
        check("UP повернуть вправо = RIGHT", Action.UP.turnRight() == Action.RIGHT);
        check("UP смещает y на +1", p(1, 1).step(Action.UP).equals(p(1, 2)));
        check("порядок действий как в уравнении задания", List.of(Action.values())
                .equals(List.of(Action.UP, Action.LEFT, Action.DOWN, Action.RIGHT)));
    }

    private void classicWorld() {
        GridWorld w = GridWorld.classic(-0.04);
        check("размер 4×3", w.width() == 4 && w.height() == 3);
        check("12 ячеек, 1 стена → 11 состояний", w.states().size() == 11);
        check("стена (2,2)", w.isWall(p(2, 2)));
        check("+1 в (4,3)", w.isTerminal(p(4, 3)) && w.reward(p(4, 3)) == 1.0);
        check("−1 в (4,2)", w.isTerminal(p(4, 2)) && w.reward(p(4, 2)) == -1.0);
        check("старт (1,1)", w.start().equals(p(1, 1)));
        check("R(s) = −0.04 в обычных клетках", w.reward(p(1, 1)) == -0.04 && w.reward(p(3, 2)) == -0.04);
        check("граница непроходима", w.isBlocked(p(0, 1)) && w.isBlocked(p(5, 1)) && w.isBlocked(p(1, 4)));
        check("стена непроходима", w.isBlocked(p(2, 2)));
        throwsIae("у стены нет награды", () -> w.reward(p(2, 2)));
        throwsIae("клетка вне мира", () -> w.cell(p(5, 5)));
    }

    private void transitionModel() {
        Mdp mdp = Mdp.classic();
        // Уравнение состояния (1,1) из задания.
        expectTransitions(mdp, p(1, 1), Action.UP, Map.of(p(1, 2), 0.8, p(2, 1), 0.1, p(1, 1), 0.1));
        expectTransitions(mdp, p(1, 1), Action.LEFT, Map.of(p(1, 1), 0.9, p(1, 2), 0.1));
        expectTransitions(mdp, p(1, 1), Action.DOWN, Map.of(p(1, 1), 0.9, p(2, 1), 0.1));
        // В задании строка Right содержит опечатку (повтор строки Up); верно: 0.8·U(2,1) + 0.1·U(1,2) + 0.1·U(1,1).
        expectTransitions(mdp, p(1, 1), Action.RIGHT, Map.of(p(2, 1), 0.8, p(1, 2), 0.1, p(1, 1), 0.1));
        // Клетка рядом с выходом −1: из (3,2) вправо — 0.8 в −1.
        expectTransitions(mdp, p(3, 2), Action.RIGHT, Map.of(p(4, 2), 0.8, p(3, 3), 0.1, p(3, 1), 0.1));
        // Вверх из (3,2): 0.1 сносит влево в стену (2,2) — остаётся на месте, 0.1 вправо — в −1.
        expectTransitions(mdp, p(3, 2), Action.UP, Map.of(p(3, 3), 0.8, p(3, 2), 0.1, p(4, 2), 0.1));
        for (Position s : mdp.states()) {
            if (mdp.isTerminal(s)) {
                check("в конечном состоянии " + s + " нет действий", mdp.actions(s).isEmpty());
                continue;
            }
            for (Action a : Action.values()) {
                double sum = mdp.transitions(s, a).stream().mapToDouble(Transition::probability).sum();
                near("Σ T(" + s + "," + a + ",·) = 1", 1.0, sum, 1e-12);
                for (Transition t : mdp.transitions(s, a)) {
                    check("переход в состояние, а не в стену: " + t.next(), !mdp.world().isBlocked(t.next()));
                }
            }
        }
        TransitionModel det = new TransitionModel(1.0);
        check("P=1: детерминированная модель", det.transitions(mdp.world(), p(1, 1), Action.UP).size() == 1);
        TransitionModel side = new TransitionModel(0.0);
        near("P=0: снос в каждую сторону 0.5", 0.5, side.side(), 1e-15);
        throwsIae("P > 1 запрещено", () -> new TransitionModel(1.2));
        throwsIae("P < 0 запрещено", () -> new TransitionModel(-0.1));
        throwsIae("P = NaN запрещено", () -> new TransitionModel(Double.NaN));
    }

    private void valueIterationMatchesTable() {
        SolverResult r = new ValueIteration().solve(Mdp.classic());
        check("сошлось", r.converged());
        expectTable("VI", r.utilities());
        expectPolicy("VI", r.policy());
        check("история начинается с U₀ = 0", r.history().get(0).utilities().get(p(1, 1)) == 0.0);
        check("последний снимок = итог", r.history().get(r.history().size() - 1).utilities().maxDifference(r.utilities()) == 0);
    }

    private void policyIterationMatchesTable() {
        SolverResult r = new PolicyIteration(42).solve(Mdp.classic());
        check("сошлось", r.converged());
        check("итераций немного (≤ 20), получено " + r.iterations(), r.iterations() <= 20);
        expectTable("PI", r.utilities());
        expectPolicy("PI", r.policy());
        SolverResult vi = new ValueIteration().solve(Mdp.classic());
        near("PI и VI дают одинаковые U", 0, r.utilities().maxDifference(vi.utilities()), 1e-8);
        check("PI и VI дают одинаковую стратегию", r.policy().equals(vi.policy()));
        // Полезность монотонно не убывает от итерации к итерации (теорема об улучшении стратегии).
        double prev = Double.NEGATIVE_INFINITY;
        boolean monotone = true;
        for (var s : r.history()) {
            for (Position st : Mdp.classic().states()) {
                if (s.iteration() > 0 && s.utilities().get(st) < prevValue(r, s.iteration() - 1, st) - 1e-9) {
                    monotone = false;
                }
            }
            prev = s.utilities().get(p(1, 1));
        }
        check("U^{πᵢ} не убывает по i во всех клетках", monotone);
        check("U(1,1) итоговой стратегии = " + prev, Math.abs(prev - 0.7053) < 0.001);
    }

    private static double prevValue(SolverResult r, int iteration, Position s) {
        return r.history().get(iteration).utilities().get(s);
    }

    private void policyIterationSeeds() {
        SolverResult reference = new ValueIteration().solve(Mdp.classic());
        for (long seed = 0; seed < 30; seed++) {
            SolverResult r = new PolicyIteration(seed).solve(Mdp.classic());
            check("seed " + seed + ": сошлось", r.converged());
            check("seed " + seed + ": та же стратегия", r.policy().equals(reference.policy()));
            near("seed " + seed + ": те же U", 0, r.utilities().maxDifference(reference.utilities()), 1e-8);
        }
    }

    private void bellmanFixedPoint() {
        for (Algorithm algorithm : Algorithm.values()) {
            Mdp mdp = Mdp.classic();
            SolverResult r = algorithm.createSolver(7).solve(mdp);
            for (Position s : mdp.states()) {
                double rhs = mdp.reward(s);
                if (!mdp.isTerminal(s)) {
                    double best = Double.NEGATIVE_INFINITY;
                    for (Action a : Action.values()) {
                        best = Math.max(best, mdp.expectedUtility(s, a, r.utilities()));
                    }
                    rhs += mdp.gamma() * best;
                    near(algorithm + ": π(s) достигает максимума в " + s, best,
                            mdp.expectedUtility(s, r.policy().get(s), r.utilities()), 1e-9);
                } else {
                    check(algorithm + ": в выходе " + s + " нет действия", r.policy().get(s) == null);
                }
                near(algorithm + ": U(s) = R(s) + γ·max Σ T·U в " + s, rhs, r.utilities().get(s), 1e-8);
            }
        }
    }

    private void policyEvaluation() {
        Mdp mdp = Mdp.classic();
        SolverResult vi = new ValueIteration().solve(mdp);
        PolicyEvaluator evaluator = new PolicyEvaluator();
        Optional<Utilities> exact = evaluator.exact(mdp, vi.policy());
        check("СЛАУ для оптимальной стратегии разрешима", exact.isPresent());
        exact.ifPresent(u -> near("точная U^π* = U*", 0, u.maxDifference(vi.utilities()), 1e-8));
        PolicyEvaluator.Result iterative = evaluator.iterative(mdp, vi.policy(), Utilities.zeros(4, 3));
        check("итеративная оценка сошлась", iterative.converged());
        exact.ifPresent(u -> near("итеративная = точная", 0, u.maxDifference(iterative.utilities()), 1e-8));

        // Стратегия «всё время вниз» никогда не выходит из нижнего ряда — при γ = 1 СЛАУ вырождена.
        Action[][] down = new Action[4][3];
        for (Position s : mdp.states()) {
            if (!mdp.isTerminal(s)) {
                down[s.x() - 1][s.y() - 1] = Action.DOWN;
            }
        }
        Policy improper = new Policy(down);
        check("несобственная стратегия: СЛАУ вырождена", evaluator.exact(mdp, improper).isEmpty());
        PolicyEvaluator.Result capped = new PolicyEvaluator(1e-11, 500).iterative(mdp, improper, Utilities.zeros(4, 3));
        check("несобственная стратегия: итерации не сходятся", !capped.converged());
        check("несобственная стратегия: U(1,1) уходит в −∞", capped.utilities().get(p(1, 1)) < -10);

        // При γ < 1 даже несобственная стратегия имеет конечную полезность: −0.04/(1 − 0.9) = −0.4 в (1,1).
        Mdp discounted = new Mdp(GridWorld.classic(-0.04), TransitionModel.standard(), 0.9);
        Optional<Utilities> u = evaluator.exact(discounted, improper);
        check("γ = 0.9: СЛАУ разрешима", u.isPresent());
        u.ifPresent(v -> near("γ = 0.9: U(1,1) стратегии «вниз» = −0.4", -0.4, v.get(p(1, 1)), 1e-9));

        // Ручной расчёт для мира 2×2 c одним выходом: U(1,1) = −0.04 + 0.9·U(1,1) + 0.1·U(2,1)... проверяем через линейную модель.
        GridWorld tiny = GridWorld.builder(2, 2).terminal(2, 2, 1.0).start(1, 1).build();
        Mdp tinyMdp = new Mdp(tiny, new TransitionModel(1.0), 1.0);
        SolverResult r = new PolicyIteration(1).solve(tinyMdp);
        near("детерминированный мир 2×2: U(1,1) = 1 − 2·0.04", 0.92, r.utilities().get(p(1, 1)), 1e-9);
        near("детерминированный мир 2×2: U(2,1) = 1 − 0.04", 0.96, r.utilities().get(p(2, 1)), 1e-9);
    }

    private void agent() {
        Mdp mdp = Mdp.classic();
        MdpAgent agent = new MdpAgent(mdp, mdp.createEnvironment(5));
        throwsIse("решение без стратегии запрещено", agent::decideDirection);
        SolverResult r = agent.computePolicy(Algorithm.POLICY_ITERATION.createSolver(5));
        check("hasPolicy после computePolicy", agent.hasPolicy());
        check("в старте решение — вверх", agent.decideDirection() == Action.UP);
        Map<Action, Double> au = agent.actionUtilities(p(1, 1));
        check("в (1,1) оценены 4 действия", au.size() == 4);
        // Числа из уравнения состояния (1,1) задания при U из таблицы.
        double u11 = r.utilities().get(p(1, 1));
        double u12 = r.utilities().get(p(1, 2));
        double u21 = r.utilities().get(p(2, 1));
        near("Up = 0.8·U(1,2) + 0.1·U(2,1) + 0.1·U(1,1)", 0.8 * u12 + 0.1 * u21 + 0.1 * u11, au.get(Action.UP), 1e-12);
        near("Left = 0.9·U(1,1) + 0.1·U(1,2)", 0.9 * u11 + 0.1 * u12, au.get(Action.LEFT), 1e-12);
        near("Down = 0.9·U(1,1) + 0.1·U(2,1)", 0.9 * u11 + 0.1 * u21, au.get(Action.DOWN), 1e-12);
        near("Right = 0.8·U(2,1) + 0.1·U(1,2) + 0.1·U(1,1)", 0.8 * u21 + 0.1 * u12 + 0.1 * u11, au.get(Action.RIGHT), 1e-12);
        check("Up — максимальное", au.get(Action.UP) > au.get(Action.LEFT) && au.get(Action.UP) > au.get(Action.DOWN)
                && au.get(Action.UP) > au.get(Action.RIGHT));
        near("U(1,1) = −0.04 + max", u11, -0.04 + au.get(Action.UP), 1e-9);
        near("полезность стратегии U^π(1,1)", u11, agent.policyUtility(p(1, 1)), 1e-8);
        check("в выходе нет действий", agent.actionUtilities(p(4, 3)).isEmpty());

        StepRecord step = agent.move();
        check("шаг 1 из старта", step.step() == 1 && step.from().equals(p(1, 1)));
        check("намерение = решение агента", step.intended() == Action.UP);
        check("агент там, куда сообщил шаг", agent.position().equals(step.to()));
        check("фактическое направление — вперёд или перпендикулярно",
                step.actual() == Action.UP || step.actual() == Action.LEFT || step.actual() == Action.RIGHT);

        Episode e = agent.runEpisode(1000);
        check("эпизод заканчивается в выходе", e.reachedTerminal() && mdp.isTerminal(e.finalPosition()));
        throwsIse("в выходе двигаться нельзя", agent::move);
        check("в выходе решение = null", agent.decideDirection() == null);
        agent.startNewEpisode();
        check("новый эпизод с начала", agent.position().equals(p(1, 1)) && agent.environment().steps() == 0);

        GridWorld other = GridWorld.classic(-0.04);
        throwsIae("среда и модель — один мир", () -> new MdpAgent(mdp, new StochasticEnvironment(other, TransitionModel.standard(), 1.0, 1)));
    }

    private void riskReward() {
        // Пороговые значения R(s) — Рассел, Норвиг, рис. 17.2.
        Policy rush = solve(-2.0, 1.0).policy();
        check("R = −2: из (3,2) сразу в −1", rush.get(p(3, 2)) == Action.RIGHT);
        check("R = −2: из (4,1) вверх, в −1", rush.get(p(4, 1)) == Action.UP);
        check("R = −2: из (2,1) вправо", rush.get(p(2, 1)) == Action.RIGHT);

        Policy shortPath = solve(-0.3, 1.0).policy();
        check("R = −0.3: из (3,1) вверх (короткий путь с риском)", shortPath.get(p(3, 1)) == Action.UP);
        check("R = −0.3: из (2,1) вправо", shortPath.get(p(2, 1)) == Action.RIGHT);
        check("R = −0.3: из (3,2) вверх", shortPath.get(p(3, 2)) == Action.UP);

        Policy careful = solve(-0.01, 1.0).policy();
        check("R = −0.01: из (3,2) влево, в стену (без риска)", careful.get(p(3, 2)) == Action.LEFT);
        check("R = −0.01: из (4,1) вниз, в стену", careful.get(p(4, 1)) == Action.DOWN);

        SolverResult positive = solve(0.5, 0.9);
        Policy avoid = positive.policy();
        check("R = +0.5, γ = 0.9: из (3,3) влево (не к +1)", avoid.get(p(3, 3)) == Action.LEFT);
        check("R = +0.5, γ = 0.9: из (3,2) влево", avoid.get(p(3, 2)) == Action.LEFT);
        check("R = +0.5, γ = 0.9: из (4,1) вниз", avoid.get(p(4, 1)) == Action.DOWN);
        near("R = +0.5, γ = 0.9: U = R/(1 − γ) = 5", 5.0, positive.utilities().get(p(1, 1)), 1e-6);
        EpisodeStatistics st = MonteCarlo.simulate(new Mdp(GridWorld.classic(0.5), TransitionModel.standard(), 0.9),
                avoid, 50, 1, 300);
        check("R > 0: агент не выходит из мира (все эпизоды по лимиту)", st.timeouts() == 50);

        double prev = Double.POSITIVE_INFINITY;
        for (double reward : new double[]{0.0, -0.01, -0.04, -0.1, -0.3, -1.0, -2.0}) {
            double u = solve(reward, 1.0).utilities().get(p(1, 1));
            check("U(1,1) убывает при уменьшении R: R = " + reward, u < prev + 1e-12);
            prev = u;
        }
    }

    private void discounting() {
        Mdp mdp = new Mdp(GridWorld.classic(-0.04), TransitionModel.standard(), 0.9);
        SolverResult pi = new PolicyIteration(3).solve(mdp);
        ValueIteration strict = new ValueIteration(1e-9, 100_000);
        SolverResult vi = strict.solve(mdp);
        check("γ = 0.9: оба сошлись", pi.converged() && vi.converged());
        near("γ = 0.9: PI = VI", 0, pi.utilities().maxDifference(vi.utilities()), 1e-8);
        Utilities exact = new PolicyEvaluator().exact(mdp, pi.policy()).orElseThrow();
        check("γ = 0.9: погрешность VI ≤ ε", exact.maxDifference(vi.utilities()) <= 1e-9);
        // Монте-Карло с дисконтированием.
        EpisodeStatistics st = MonteCarlo.simulate(mdp, pi.policy(), 40_000, 2024, 1000);
        double u = pi.utilities().get(p(1, 1));
        check("γ = 0.9: Σγᵗ·R ≈ U(1,1): " + Fmt.fixed(st.meanReturn(), 4) + " vs " + Fmt.fixed(u, 4),
                Math.abs(st.meanReturn() - u) < 4 * st.standardError() + 1e-9);
        // Дисконтированная сумма в среде считается как Σγᵗ·R(sₜ).
        StochasticEnvironment env = mdp.createEnvironment(1);
        double expected = mdp.reward(env.position());
        double factor = 1;
        while (!env.isTerminal()) {
            StepRecord s = env.execute(pi.policy().get(env.position()));
            factor *= 0.9;
            expected += factor * s.reward();
        }
        near("дисконтированная сумма эпизода", expected, env.discountedReturn(), 1e-12);
    }

    private void validation() {
        throwsIae("γ = 0 запрещено", () -> new Mdp(GridWorld.classic(-0.04), TransitionModel.standard(), 0.0));
        throwsIae("γ > 1 запрещено", () -> new Mdp(GridWorld.classic(-0.04), TransitionModel.standard(), 1.01));
        throwsIae("γ = NaN запрещено", () -> new Mdp(GridWorld.classic(-0.04), TransitionModel.standard(), Double.NaN));
        throwsIae("γ = 1 и R > 0: полезность не ограничена",
                () -> new Mdp(GridWorld.classic(0.1), TransitionModel.standard(), 1.0));
        noThrow("γ = 0.9 и R > 0 допустимо", () -> new Mdp(GridWorld.classic(0.1), TransitionModel.standard(), 0.9));
        noThrow("γ = 1 и R = 0 допустимо", () -> new Mdp(GridWorld.classic(0.0), TransitionModel.standard(), 1.0));
        // Замкнутая область без выхода.
        GridWorld trapped = GridWorld.builder(4, 3).wall(2, 1).wall(2, 2).wall(2, 3).terminal(4, 3, 1).start(1, 1).build();
        throwsIae("γ = 1: из замкнутой области нет выхода",
                () -> new Mdp(trapped, TransitionModel.standard(), 1.0));
        SolverResult r = new PolicyIteration(1).solve(new Mdp(trapped, TransitionModel.standard(), 0.9));
        check("γ < 1: замкнутая область решается", r.converged());
        near("γ < 1: U в замкнутой области = −0.04/(1 − γ)", -0.4, r.utilities().get(p(1, 1)), 1e-8);
        GridWorld noExit = GridWorld.builder(3, 3).build();
        throwsIae("мир без выходов", () -> new Mdp(noExit, TransitionModel.standard(), 0.9));
        throwsIae("R(s) = NaN", () -> GridWorld.classic(Double.NaN));
        throwsIae("R(s) = ∞", () -> GridWorld.classic(Double.POSITIVE_INFINITY));
        throwsIae("размер больше максимума", () -> GridWorld.builder(11, 3));
        throwsIae("размер меньше минимума", () -> GridWorld.builder(1, 3));
        throwsIae("стена на старте", () -> GridWorld.builder(3, 3).wall(1, 1));
        throwsIae("старт в стену", () -> GridWorld.builder(3, 3).wall(2, 2).start(2, 2));
        throwsIae("переход из стены", () -> Mdp.classic().transitions(p(2, 2), Action.UP));
        throwsIae("переход из выхода", () -> Mdp.classic().transitions(p(4, 3), Action.UP));
        Mdp classic = Mdp.classic();
        Action[][] partial = new Action[4][3];
        throwsIae("стратегия без действия в состоянии",
                () -> new PolicyEvaluator().iterative(classic, new Policy(partial), Utilities.zeros(4, 3)));
    }

    private void sampling() {
        GridWorld open = GridWorld.builder(5, 5).terminal(5, 5, 1).start(1, 1).build();
        TransitionModel model = TransitionModel.standard();
        Random random = new Random(12345);
        int n = 200_000;
        for (Action intended : Action.values()) {
            Map<Action, Integer> counts = new EnumMap<>(Action.class);
            for (int i = 0; i < n; i++) {
                counts.merge(model.sampleDirection(intended, random), 1, Integer::sum);
            }
            near(intended + ": доля «вперёд» ≈ 0.8", 0.8, counts.getOrDefault(intended, 0) / (double) n, 0.005);
            near(intended + ": доля «снос влево» ≈ 0.1", 0.1, counts.getOrDefault(intended.turnLeft(), 0) / (double) n, 0.004);
            near(intended + ": доля «снос вправо» ≈ 0.1", 0.1, counts.getOrDefault(intended.turnRight(), 0) / (double) n, 0.004);
            check(intended + ": назад не сносит", counts.getOrDefault(intended.opposite(), 0) == 0);
        }
        // Эмпирическое распределение исходов среды совпадает с T(s, a, ·) — в т. ч. с ударами о стену.
        Mdp mdp = Mdp.classic();
        for (Position s : List.of(p(1, 1), p(3, 2), p(4, 1), p(1, 3))) {
            for (Action a : Action.values()) {
                Map<Position, Integer> hits = new HashMap<>();
                Random rnd = new Random(s.hashCode() * 31L + a.ordinal());
                int m = 20_000;
                for (int i = 0; i < m; i++) {
                    Position to = TransitionModel.destination(mdp.world(), s, mdp.model().sampleDirection(a, rnd));
                    hits.merge(to, 1, Integer::sum);
                }
                for (Transition t : mdp.transitions(s, a)) {
                    near("частота " + s + " " + a + " → " + t.next(), t.probability(), hits.getOrDefault(t.next(), 0) / (double) m, 0.015);
                }
                check("исходы " + s + "/" + a + " только из T", hits.keySet().stream()
                        .allMatch(cell -> mdp.transitions(s, a).stream().anyMatch(t -> t.next().equals(cell))));
            }
        }
        check("мир 5×5 открыт", open.states().size() == 25);
    }

    private void monteCarlo() {
        Mdp mdp = Mdp.classic();
        SolverResult r = new PolicyIteration(1).solve(mdp);
        EpisodeStatistics st = MonteCarlo.simulate(mdp, r.policy(), 50_000, 777, 1000);
        double u = r.utilities().get(p(1, 1));
        check("Σ R ≈ U(1,1) = 0.705: получено " + Fmt.fixed(st.meanReturn(), 4) + " ± " + Fmt.fixed(st.confidence95(), 4),
                Math.abs(st.meanReturn() - u) < 4 * st.standardError());
        check("нет эпизодов по лимиту", st.timeouts() == 0);
        int total = st.exits().values().stream().mapToInt(Integer::intValue).sum();
        check("все эпизоды закончились в выходах", total == st.episodes());
        // Из каждой клетки, а не только из старта: средняя сумма ≈ U(s).
        for (Position s : List.of(p(3, 1), p(4, 1), p(3, 2), p(1, 3))) {
            GridWorld from = mdp.world().toBuilder().start(s.x(), s.y()).build();
            Mdp m2 = new Mdp(from, mdp.model(), 1.0);
            EpisodeStatistics st2 = MonteCarlo.simulate(m2, r.policy(), 30_000, 99 + s.x(), 1000);
            check("из " + s + ": " + Fmt.fixed(st2.meanReturn(), 4) + " ≈ U = " + Fmt.fixed(r.utilities().get(s), 4),
                    Math.abs(st2.meanReturn() - r.utilities().get(s)) < 4 * st2.standardError());
        }
    }

    private void episodes() {
        Mdp mdp = Mdp.classic();
        MdpAgent a = new MdpAgent(mdp, mdp.createEnvironment(2));
        a.computePolicy(new PolicyIteration(2));
        for (int i = 0; i < 200; i++) {
            Episode e = a.runEpisode(1000);
            double terminal = mdp.reward(e.finalPosition());
            near("эпизод " + i + ": сумма = −0.04·шагов + R(выход)", -0.04 * e.length() + terminal, e.totalReward(), 1e-9);
            near("эпизод " + i + ": при γ = 1 дисконтированная = обычной", e.totalReward(), e.discountedReturn(), 1e-12);
            for (int k = 0; k < e.steps().size(); k++) {
                StepRecord s = e.steps().get(k);
                check("шаги нумеруются подряд", s.step() == k + 1);
                check("клетки соседние или та же", Math.abs(s.from().x() - s.to().x()) + Math.abs(s.from().y() - s.to().y()) <= 1);
                check("в стену не попадает", !mdp.world().isBlocked(s.to()));
                check("терминальный только последний шаг", s.terminal() == (k == e.steps().size() - 1));
                if (k > 0) {
                    check("продолжение пути", s.from().equals(e.steps().get(k - 1).to()));
                }
            }
        }
        MdpAgent b1 = new MdpAgent(mdp, mdp.createEnvironment(99));
        MdpAgent b2 = new MdpAgent(mdp, mdp.createEnvironment(99));
        b1.computePolicy(new ValueIteration());
        b2.computePolicy(new ValueIteration());
        check("одинаковый seed → одинаковый путь", b1.runEpisode(1000).equals(b2.runEpisode(1000)));
        MdpAgent c = new MdpAgent(mdp, mdp.createEnvironment(5));
        c.computePolicy(new ValueIteration());
        Episode limited = c.runEpisode(1);
        check("лимит шагов соблюдается", limited.length() == 1);
    }

    private void builder() {
        GridWorld.Builder b = GridWorld.builder(3, 3);
        Position cell = p(2, 2);
        check("пусто → стена", b.cycle(cell) && b.cell(cell) == CellType.WALL);
        check("стена → +1", b.cycle(cell) && b.cell(cell) == CellType.TERMINAL && b.terminalReward(cell) == 1.0);
        check("+1 → −1", b.cycle(cell) && b.cell(cell) == CellType.TERMINAL && b.terminalReward(cell) == -1.0);
        check("−1 → пусто", b.cycle(cell) && b.cell(cell) == CellType.EMPTY);
        check("старт не редактируется", !b.cycle(p(1, 1)) && b.cell(p(1, 1)) == CellType.EMPTY);
        b.cycle(p(3, 3));
        check("старт нельзя поставить в стену", !b.moveStart(p(3, 3)));
        check("старт переносится в обычную клетку", b.moveStart(p(2, 1)) && b.start().equals(p(2, 1)));
        b.resize(5, 4);
        check("resize сохраняет клетки", b.cell(p(3, 3)) == CellType.WALL && b.width() == 5 && b.height() == 4);
        b.moveStart(p(5, 4));
        b.resize(2, 2);
        check("resize: старт вне мира → (1,1)", b.start().equals(p(1, 1)));
        GridWorld w = GridWorld.classic(-0.04);
        GridWorld copy = w.toBuilder().build();
        check("toBuilder сохраняет мир", copy.states().equals(w.states()) && copy.terminals().equals(w.terminals())
                && copy.start().equals(w.start()));
        check("withStepReward меняет только R(s)", w.withStepReward(-0.5).reward(p(1, 1)) == -0.5
                && w.withStepReward(-0.5).reward(p(4, 3)) == 1.0);
        check("исходный мир не изменился (неизменяемость)", w.reward(p(1, 1)) == -0.04);
    }

    private void randomWorlds() {
        Random random = new Random(2026);
        int solved = 0;
        for (int i = 0; i < 60; i++) {
            int width = 2 + random.nextInt(9);
            int height = 2 + random.nextInt(9);
            GridWorld.Builder b = GridWorld.builder(width, height);
            for (int k = 0; k < width * height / 4; k++) {
                Position cell = p(1 + random.nextInt(width), 1 + random.nextInt(height));
                if (!cell.equals(b.start())) {
                    if (random.nextInt(4) == 0) {
                        b.terminal(cell.x(), cell.y(), random.nextBoolean() ? 1.0 : -1.0);
                    } else {
                        b.wall(cell.x(), cell.y());
                    }
                }
            }
            Position exit = p(width, height);
            if (!exit.equals(b.start())) {
                b.terminal(exit.x(), exit.y(), 1.0);
            }
            double reward = -0.01 - random.nextDouble() * 0.5;
            double gamma = random.nextBoolean() ? 1.0 : 0.5 + random.nextDouble() * 0.49;
            double forward = 0.5 + random.nextDouble() * 0.5;
            Mdp mdp;
            try {
                mdp = new Mdp(b.stepReward(reward).build(), new TransitionModel(forward), gamma);
            } catch (IllegalArgumentException e) {
                continue; // замкнутая область при γ = 1 — корректно отклонена
            }
            SolverResult pi = new PolicyIteration(i).solve(mdp);
            SolverResult vi = new ValueIteration().solve(mdp);
            check("мир " + i + ": PI сошлась", pi.converged());
            check("мир " + i + ": VI сошлась", vi.converged());
            near("мир " + i + " (" + width + "×" + height + "): U_PI = U_VI", 0, pi.utilities().maxDifference(vi.utilities()), 1e-6);
            for (Position s : mdp.states()) {
                if (!mdp.isTerminal(s)) {
                    double best = Double.NEGATIVE_INFINITY;
                    for (Action a : Action.values()) {
                        best = Math.max(best, mdp.expectedUtility(s, a, vi.utilities()));
                    }
                    near("мир " + i + ": π_PI оптимальна в " + s, best, mdp.expectedUtility(s, pi.policy().get(s), vi.utilities()), 1e-6);
                }
            }
            solved++;
        }
        check("решено достаточно миров (" + solved + ")", solved >= 30);
    }

    private void generator() {
        int full = 0;
        int total = 0;
        for (int w = GridWorld.MIN_SIZE; w <= GridWorld.MAX_SIZE; w++) {
            for (int h = GridWorld.MIN_SIZE; h <= GridWorld.MAX_SIZE; h++) {
                for (long seed = 0; seed < 3; seed++) {
                    GridWorld g = WorldGenerator.generate(w, h, -0.04, seed);
                    String id = w + "×" + h + "/" + seed;
                    check(id + ": размер", g.width() == w && g.height() == h);
                    check(id + ": старт (1,1)", g.start().equals(p(1, 1)) && g.cell(p(1, 1)) == CellType.EMPTY);
                    check(id + ": +1 в правом верхнем углу", g.isTerminal(p(w, h)) && g.reward(p(w, h)) == 1.0);
                    check(id + ": −1 под выходом +1", g.isTerminal(p(w, h - 1)) && g.reward(p(w, h - 1)) == -1.0);
                    check(id + ": мир корректен", WorldGenerator.isValid(g));
                    int walls = w * h - g.states().size();
                    check(id + ": стен не больше нормы", walls <= WorldGenerator.wallCount(w, h));
                    check(id + ": ловушек по норме", g.terminals().size() == 2 + WorldGenerator.extraTrapCount(w, h));
                    total++;
                    if (walls == WorldGenerator.wallCount(w, h)) {
                        full++;
                    }
                    Mdp mdp = new Mdp(g, TransitionModel.standard(), 1.0); // не бросает: полезность ограничена
                    SolverResult r = new PolicyIteration(seed).solve(mdp);
                    check(id + ": стратегия найдена", r.converged() && Double.isFinite(r.utilities().get(g.start())));
                }
            }
        }
        check("почти всегда ставятся все стены (" + full + " из " + total + ")", full >= total * 0.9);
        GridWorld a = WorldGenerator.generate(7, 5, -0.04, 11);
        GridWorld b = WorldGenerator.generate(7, 5, -0.04, 11);
        check("одинаковый seed → одинаковый мир", a.states().equals(b.states()) && a.terminals().equals(b.terminals()));
        int differs = 0;
        for (long seed = 12; seed < 22; seed++) {
            if (!WorldGenerator.generate(7, 5, -0.04, seed).states().equals(a.states())) {
                differs++;
            }
        }
        check("разные seed → разные миры (" + differs + " из 10)", differs >= 8);
        check("R(s) передаётся в мир", WorldGenerator.generate(5, 5, -0.3, 1).stepReward() == -0.3);
        GridWorld closed = GridWorld.builder(4, 3).wall(2, 1).wall(2, 2).wall(2, 3).terminal(4, 3, 1).start(1, 1).build();
        check("замкнутый мир отклоняется", !WorldGenerator.isValid(closed));
        check("мир из задания корректен", WorldGenerator.isValid(GridWorld.classic(-0.04)));
    }

    private void textOutput() {
        GridWorld w = GridWorld.classic(-0.04);
        SolverResult r = new PolicyIteration(1).solve(Mdp.classic());
        String table = TextRenderer.utilities(w, r.utilities());
        for (double v : EXPECTED_UTILITIES.values()) {
            check("таблица содержит " + v, table.contains(Fmt.fixed(v, 3)));
        }
        check("таблица: верхняя строка — y = 3", table.lines().findFirst().orElse("").contains("0.812"));
        String policy = TextRenderer.policy(w, r.policy());
        check("стрелки стратегии", policy.contains("→") && policy.contains("↑") && policy.contains("←"));
        String decision = TextRenderer.decision(Mdp.classic(), r.utilities(), r.policy(), p(1, 1));
        check("уравнение (1,1): Up", decision.contains("Up    0.8·U(1,2) + 0.1·U(1,1) + 0.1·U(2,1)"));
        check("уравнение (1,1): Left", decision.contains("Left  0.9·U(1,1) + 0.1·U(1,2)"));
        check("уравнение (1,1): выбор Up", decision.lines().filter(l -> l.contains("◀")).allMatch(l -> l.startsWith("Up")));
        check("Fmt: точка как разделитель", Fmt.fixed(0.5, 2).equals("0.50") && Fmt.num(-0.04).equals("-0.04")
                && Fmt.signed(1).equals("+1") && Fmt.num(-0.0).equals("0"));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Options o = Options.parse(new String[]{"--console", "--episodes", "2000", "--risk-demo"});
        ru.spbstu.mdp.console.ConsoleRunner.run(o, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String text = buffer.toString(StandardCharsets.UTF_8);
        check("консоль: обе таблицы", text.contains("Итерация по стратегиям") && text.contains("Итерация по значениям"));
        check("консоль: сверка алгоритмов без расхождений", text.contains("Клеток с разными действиями: 0"));
        check("консоль: Монте-Карло", text.contains("Проверка методом Монте-Карло: 2000 эпизодов"));
        check("консоль: демонстрация риска", text.contains("R(s) = -2, γ = 1"));
        check("консоль: нет «НЕ сошлось»", !text.contains("НЕ сошлось"));
    }

    private void options() {
        Options o = Options.parse(new String[]{"--console", "--reward", "-0,1", "--gamma", "0.9", "--p", "0.7",
                "--algo", "vi", "--episodes", "100", "--seed", "5", "--max-steps", "50"});
        check("режим консоли", o.mode == Options.Mode.CONSOLE);
        near("reward с запятой", -0.1, o.reward, 1e-12);
        near("gamma", 0.9, o.gamma, 1e-12);
        near("p", 0.7, o.forward, 1e-12);
        check("algo", o.algorithm.equals("vi"));
        check("episodes/seed/max-steps", o.episodes == 100 && o.seed == 5 && o.maxSteps == 50);
        check("по умолчанию GUI", Options.parse(new String[0]).mode == Options.Mode.GUI);
        throwsIae("неизвестный аргумент", () -> Options.parse(new String[]{"--foo"}));
        throwsIae("нет значения", () -> Options.parse(new String[]{"--gamma"}));
        throwsIae("не число", () -> Options.parse(new String[]{"--gamma", "abc"}));
        throwsIae("неверный алгоритм", () -> Options.parse(new String[]{"--algo", "xx"}));
        throwsIae("episodes = 0", () -> Options.parse(new String[]{"--episodes", "0"}));
        throwsIae("episodes дробное", () -> Options.parse(new String[]{"--episodes", "2.5"}));
    }

    private void rendering() {
        System.setProperty("java.awt.headless", "true");
        try {
            java.io.File file = java.io.File.createTempFile("laba5", ".png");
            file.deleteOnExit();
            Options o = Options.parse(new String[]{"--snapshot", file.getPath()});
            ru.spbstu.mdp.ui.SnapshotRenderer.render(o);
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file);
            check("PNG создан", img != null && img.getWidth() == 760 && img.getHeight() == 600);
            if (img != null) {
                int nonWhite = 0;
                for (int x = 0; x < img.getWidth(); x += 4) {
                    for (int y = 0; y < img.getHeight(); y += 4) {
                        if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                            nonWhite++;
                        }
                    }
                }
                check("на картинке есть изображение", nonWhite > 1000);
            }
        } catch (java.io.IOException e) {
            check("PNG записан: " + e.getMessage(), false);
        }
    }

    // ------------------------------------------------------------------ вспомогательное

    private SolverResult solve(double reward, double gamma) {
        return new PolicyIteration(11).solve(new Mdp(GridWorld.classic(reward), TransitionModel.standard(), gamma));
    }

    private void expectTable(String label, Utilities u) {
        EXPECTED_UTILITIES.forEach((s, v) -> near(label + ": U" + s + " = " + v, v, u.get(s), 0.0006));
        near(label + ": U(4,3) = +1", 1.0, u.get(p(4, 3)), 1e-12);
        near(label + ": U(4,2) = −1", -1.0, u.get(p(4, 2)), 1e-12);
    }

    private void expectPolicy(String label, Policy policy) {
        EXPECTED_POLICY.forEach((s, a) -> check(label + ": π" + s + " = " + a, policy.get(s) == a));
    }

    private void expectTransitions(Mdp mdp, Position s, Action a, Map<Position, Double> expected) {
        List<Transition> actual = mdp.transitions(s, a);
        check("T" + s + " " + a + ": " + expected.size() + " исхода", actual.size() == expected.size());
        for (Transition t : actual) {
            Double p = expected.get(t.next());
            check("T" + s + " " + a + " → " + t.next() + " ожидался", p != null);
            if (p != null) {
                near("T" + s + " " + a + " → " + t.next(), p, t.probability(), 1e-12);
            }
        }
    }

    private static Position p(int x, int y) {
        return new Position(x, y);
    }

    private void run(String name, Runnable body) {
        int before = failures.size();
        int passedBefore = passed;
        try {
            body.run();
        } catch (RuntimeException | AssertionError e) {
            failures.add(name + ": исключение " + e);
            e.printStackTrace(out);
        }
        out.printf("%s %-55s %4d проверок%n", failures.size() == before ? "[OK]  " : "[FAIL]", name, passed - passedBefore + failures.size() - before);
    }

    private void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name);
        }
    }

    private void near(String name, double expected, double actual, double tolerance) {
        check(name + " (ожидалось " + expected + ", получено " + actual + ")", Math.abs(expected - actual) <= tolerance);
    }

    private void throwsIae(String name, Runnable r) {
        try {
            r.run();
            check(name + ": ожидалось IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            check(name, true);
        }
    }

    private void throwsIse(String name, Runnable r) {
        try {
            r.run();
            check(name + ": ожидалось IllegalStateException", false);
        } catch (IllegalStateException e) {
            check(name, true);
        }
    }

    private void noThrow(String name, Runnable r) {
        try {
            r.run();
            check(name, true);
        } catch (RuntimeException e) {
            check(name + ": " + e.getMessage(), false);
        }
    }

    private void summary() {
        out.println();
        if (failures.isEmpty()) {
            out.println("ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ: " + passed);
            System.exit(0);
        }
        out.println("ОШИБКИ (" + failures.size() + " из " + (passed + failures.size()) + "):");
        failures.forEach(f -> out.println("  ✗ " + f));
        System.exit(1);
    }
}
