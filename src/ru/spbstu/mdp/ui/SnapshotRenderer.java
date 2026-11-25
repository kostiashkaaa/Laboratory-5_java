package ru.spbstu.mdp.ui;

import ru.spbstu.mdp.Options;
import ru.spbstu.mdp.agent.Episode;
import ru.spbstu.mdp.agent.MdpAgent;
import ru.spbstu.mdp.decision.Algorithm;
import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.decision.SolverResult;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.StepRecord;
import ru.spbstu.mdp.world.TransitionModel;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Сохранение визуализации (стратегия, полезности и путь одного эпизода) в PNG без открытия окна. */
public final class SnapshotRenderer {

    private SnapshotRenderer() {
    }

    public static void render(Options o) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Mdp mdp = new Mdp(GridWorld.classic(o.reward), new TransitionModel(o.forward), o.gamma);
        MdpAgent agent = new MdpAgent(mdp, mdp.createEnvironment(o.seed));
        Algorithm algorithm = "vi".equals(o.algorithm) ? Algorithm.VALUE_ITERATION : Algorithm.POLICY_ITERATION;
        SolverResult result = agent.computePolicy(algorithm.createSolver(o.seed));
        Episode episode = agent.runEpisode(o.maxSteps);

        List<Position> trail = new ArrayList<>();
        trail.add(mdp.world().start());
        for (StepRecord s : episode.steps()) {
            trail.add(s.to());
        }
        GridPanel panel = new GridPanel();
        panel.setWorld(mdp.world());
        panel.setSolution(result.utilities(), result.policy());
        panel.setAgent(episode.finalPosition(), trail,
                episode.steps().isEmpty() ? null : episode.steps().get(episode.steps().size() - 1));
        int width = 760;
        int height = 600;
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            panel.render(g);
        } finally {
            g.dispose();
        }
        File file = new File(o.snapshotPath);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Не удалось создать каталог " + parent);
        }
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("Формат PNG не поддерживается");
        }
    }
}
