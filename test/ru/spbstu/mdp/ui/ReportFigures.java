package ru.spbstu.mdp.ui;

import ru.spbstu.mdp.agent.EpisodeStatistics;
import ru.spbstu.mdp.agent.MonteCarlo;
import ru.spbstu.mdp.decision.IterationSnapshot;
import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.decision.PolicyIteration;
import ru.spbstu.mdp.decision.SolverResult;
import ru.spbstu.mdp.decision.ValueIteration;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.StepRecord;
import ru.spbstu.mdp.world.TransitionModel;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Рисунки и данные для отчёта: окно программы, итерации алгоритмов, стратегии при разных R(s),
 * сходимость и проверка Монте-Карло. Запуск: java -cp out/build:out/test-build ru.spbstu.mdp.ui.ReportFigures каталог
 */
public final class ReportFigures {

    private ReportFigures() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "out/report");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Не удалось создать " + dir);
        }
        Mdp classic = Mdp.classic();

        // Итерации по стратегиям (seed 42) — панели для каждого πᵢ.
        SolverResult pi = new PolicyIteration(42).solve(classic);
        try (PrintWriter w = writer(new File(dir, "pi.csv"))) {
            w.println("iteration;u11;changes;delta");
            for (IterationSnapshot s : pi.history()) {
                grid(classic.world(), s.utilities(), s.policy(), null, List.of(), new File(dir, "pi_" + s.iteration() + ".png"), 520, 400);
                w.printf(Locale.ROOT, "%d;%.6f;%d;%.6e%n", s.iteration(), s.utilities().get(new Position(1, 1)), s.policyChanges(), s.delta());
            }
        }
        // Итерации по значениям.
        SolverResult vi = new ValueIteration().solve(classic);
        try (PrintWriter w = writer(new File(dir, "vi.csv"))) {
            w.println("iteration;u11;u33;u41;delta");
            for (IterationSnapshot s : vi.history()) {
                w.printf(Locale.ROOT, "%d;%.6f;%.6f;%.6f;%.6e%n", s.iteration(), s.utilities().get(new Position(1, 1)),
                        s.utilities().get(new Position(3, 3)), s.utilities().get(new Position(4, 1)), s.delta());
            }
            for (int k : new int[]{1, 3, 6, 10}) {
                IterationSnapshot s = vi.history().get(k);
                grid(classic.world(), s.utilities(), s.policy(), null, List.of(), new File(dir, "vi_" + k + ".png"), 520, 400);
            }
        }
        // Стратегии при разных R(s).
        double[][] cases = {{-2.0, 1.0}, {-0.3, 1.0}, {-0.04, 1.0}, {-0.01, 1.0}, {0.5, 0.9}};
        try (PrintWriter w = writer(new File(dir, "risk.csv"))) {
            w.println("reward;gamma;u11");
            for (double[] c : cases) {
                Mdp m = new Mdp(GridWorld.classic(c[0]), TransitionModel.standard(), c[1]);
                SolverResult r = new PolicyIteration(42).solve(m);
                String name = String.format(Locale.ROOT, "risk_%s.png", String.valueOf(c[0]).replace('-', 'm').replace('.', '_'));
                grid(m.world(), r.utilities(), r.policy(), null, List.of(), new File(dir, name), 520, 400);
                w.printf(Locale.ROOT, "%.2f;%.2f;%.4f%n", c[0], c[1], r.utilities().get(new Position(1, 1)));
            }
        }
        // Монте-Карло при разном числе эпизодов.
        try (PrintWriter w = writer(new File(dir, "montecarlo.csv"))) {
            w.println("episodes;mean;ci95;steps;exitPlus;exitMinus;u11");
            for (int n : new int[]{100, 1_000, 10_000, 100_000}) {
                EpisodeStatistics st = MonteCarlo.simulate(classic, pi.policy(), n, 2026, 1000);
                w.printf(Locale.ROOT, "%d;%.4f;%.4f;%.2f;%d;%d;%.4f%n", n, st.meanReturn(), st.confidence95(), st.meanSteps(),
                        st.exits().get(new Position(4, 3)), st.exits().get(new Position(4, 2)), pi.utilities().get(new Position(1, 1)));
            }
        }
        // Окно программы: эпизод, в котором агента снесло ветром.
        SwingUtilities.invokeAndWait(() -> {
            try {
                window(dir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("Рисунки и данные сохранены в " + dir);
        System.exit(0);
    }

    private static void window(File dir) throws IOException {
        MainFrame frame = new MainFrame();
        frame.setSize(1280, 1010);
        frame.addNotify();
        frame.validate();
        save(frame, new File(dir, "window_start.png"));
        // Ищем эпизод со сносом ветром и останавливаемся сразу после него.
        boolean drifted = false;
        for (int episode = 0; episode < 50 && !drifted; episode++) {
            frame.newEpisodeForTest();
            while (!frame.agent().environment().isTerminal()) {
                frame.doStep();
                StepRecord s = frame.lastStepForTest();
                if (s != null && s.drifted() && !s.terminal() && frame.agent().environment().steps() >= 2) {
                    drifted = true;
                    break;
                }
            }
        }
        save(frame, new File(dir, "window_drift.png"));
        while (!frame.agent().environment().isTerminal()) {
            frame.doStep();
        }
        save(frame, new File(dir, "window_finish.png"));
        frame.setParameters(-0.04, 1.0);
        frame.clickCell(new Position(3, 1), true);
        frame.clickCell(new Position(2, 3), true);
        frame.clickCell(new Position(2, 3), true);
        frame.clickCell(new Position(2, 3), true); // (2,3): пусто → стена → +1 → −1
        save(frame, new File(dir, "window_edited.png"));
        frame.generateForTest(7, 5);
        save(frame, new File(dir, "window_generated.png"));
        frame.dispose();
    }

    private static void grid(GridWorld world, Utilities u, Policy p, Position agent, List<Position> trail, File file,
                             int width, int height) throws IOException {
        GridPanel panel = new GridPanel();
        panel.setWorld(world);
        panel.setSolution(u, p);
        panel.setAgent(agent, trail, null);
        panel.setSize(width, height);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            panel.render(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", file);
    }

    private static void save(MainFrame frame, File file) throws IOException {
        var root = frame.getRootPane();
        root.setSize(frame.getSize());
        layout(root);
        BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            root.printAll(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", file);
    }

    private static void layout(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container cc) {
                layout(cc);
            }
        }
    }

    private static PrintWriter writer(File f) throws IOException {
        return new PrintWriter(f, StandardCharsets.UTF_8);
    }
}
