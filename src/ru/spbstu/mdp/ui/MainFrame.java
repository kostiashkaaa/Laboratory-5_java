package ru.spbstu.mdp.ui;

import ru.spbstu.mdp.agent.EpisodeStatistics;
import ru.spbstu.mdp.agent.MdpAgent;
import ru.spbstu.mdp.decision.Algorithm;
import ru.spbstu.mdp.decision.IterationSnapshot;
import ru.spbstu.mdp.decision.Mdp;
import ru.spbstu.mdp.decision.SolverResult;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.util.Fmt;
import ru.spbstu.mdp.view.TextRenderer;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.StepRecord;
import ru.spbstu.mdp.world.StochasticEnvironment;
import ru.spbstu.mdp.world.TransitionModel;
import ru.spbstu.mdp.world.WorldGenerator;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Главное окно: мир с полезностями и стратегией, параметры MDP, выбор алгоритма, пошаговый просмотр итераций,
 * анимация движения агента, серия эпизодов и редактор мира.
 */
public final class MainFrame extends JFrame {

    static final int MAX_EPISODE_STEPS = 1_000;
    static final int SERIES_EPISODES = 5_000;
    private static final int LOG_LIMIT = 200_000;

    private record Preset(String title, double reward, double gamma) {
        @Override
        public String toString() {
            return title;
        }
    }

    private static final Preset[] PRESETS = {
            new Preset("— выбрать —", Double.NaN, Double.NaN),
            new Preset("R = −0.04 (пример из задания)", -0.04, 1.0),
            new Preset("R = −2 (скорее к любому выходу)", -2.0, 1.0),
            new Preset("R = −0.3 (короткий путь с риском)", -0.3, 1.0),
            new Preset("R = −0.01 (максимальная осторожность)", -0.01, 1.0),
            new Preset("R = 0", 0.0, 1.0),
            new Preset("R = +0.5, γ = 0.9 (избегать выходов)", 0.5, 0.9),
    };

    private final GridPanel gridPanel = new GridPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea logArea = new JTextArea(9, 90);
    private final JTextArea decisionArea = new JTextArea(7, 30);

    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(4, GridWorld.MIN_SIZE, GridWorld.MAX_SIZE, 1));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(3, GridWorld.MIN_SIZE, GridWorld.MAX_SIZE, 1));
    private final JCheckBox editModeBox = new JCheckBox("Редактировать мир мышью");
    private final JButton classicButton = new JButton("Мир из задания");
    private final JButton generateButton = new JButton("Сгенерировать мир");

    private final JSpinner rewardSpinner = new JSpinner(new SpinnerNumberModel(-0.04, -5.0, 1.0, 0.01));
    private final JSpinner gammaSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 1.0, 0.01));
    private final JSpinner forwardSpinner = new JSpinner(new SpinnerNumberModel(0.8, 0.0, 1.0, 0.05));
    private final JComboBox<Preset> presetBox = new JComboBox<>(PRESETS);

    private final JComboBox<Algorithm> algorithmBox = new JComboBox<>(Algorithm.values());
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(42, 0, 1_000_000, 1));
    private final JSlider iterationSlider = new JSlider(0, 0, 0);
    private final JLabel iterationLabel = new JLabel(" ");

    private final JButton stepButton = new JButton("Шаг");
    private final JToggleButton runButton = new JToggleButton("Пуск");
    private final JButton newEpisodeButton = new JButton("Новый эпизод");
    private final JButton seriesButton = new JButton("Серия × " + SERIES_EPISODES);
    private final JSlider speedSlider = new JSlider(50, 1200, 450);

    private final JCheckBox heatMapBox = new JCheckBox("Тепловая карта", true);
    private final JCheckBox utilitiesBox = new JCheckBox("Полезности U(s)", true);
    private final JCheckBox policyBox = new JCheckBox("Стратегия π(s)", true);

    private final Timer timer;

    private GridWorld.Builder builder = GridWorld.classicBuilder();
    private Mdp mdp;
    private MdpAgent agent;
    private SolverResult result;
    private final List<Position> trail = new ArrayList<>();
    private StepRecord lastStep;
    private boolean updating;
    private long seriesCounter;
    private long generationCounter;

    public MainFrame() {
        super("Лабораторная работа 5 — агент в стохастической среде (MDP)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        timer = new Timer(speedSlider.getValue(), e -> doStep());

        setLayout(new BorderLayout(6, 6));
        JPanel center = new JPanel(new BorderLayout(4, 4));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
        statusLabel.setFont(statusLabel.getFont().deriveFont(13f));
        center.add(statusLabel, BorderLayout.NORTH);
        center.add(gridPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(worldSection());
        controls.add(parametersSection());
        controls.add(algorithmSection());
        controls.add(agentSection());
        controls.add(viewSection());
        controls.add(decisionSection());
        JPanel wrapper = new WidthTrackingPanel();
        wrapper.add(controls, BorderLayout.NORTH);
        JScrollPane controlScroll = new JScrollPane(wrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlScroll.setBorder(BorderFactory.createEmptyBorder());
        controlScroll.setPreferredSize(new Dimension(420, 640));
        controlScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(controlScroll, BorderLayout.EAST);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Журнал"));
        add(logScroll, BorderLayout.SOUTH);

        wireEvents();
        log("Агент ищет оптимальную стратегию в стохастическом мире: с вероятностью P идёт куда решил, "
                + "иначе его сносит ветром перпендикулярно движению.");
        recompute();
        pack();
        setMinimumSize(new Dimension(1000, 720));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ построение интерфейса

    /** Панель настроек подстраивается под ширину области прокрутки — без горизонтальной полосы. */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private JPanel worldSection() {
        JPanel p = section("Мир");
        addRow(p, new JLabel("Ширина"), widthSpinner);
        addRow(p, new JLabel("Высота"), heightSpinner);
        JPanel worldButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        worldButtons.add(generateButton);
        worldButtons.add(classicButton);
        addRow(p, worldButtons, null);
        addRow(p, editModeBox, null);
        JLabel hint = new JLabel("<html><small>Смена размера или кнопка «Сгенерировать» строят новый мир:<br>"
                + "старт (1,1), +1 в правом верхнем углу, −1 под ним, стены ≈ 12 %.<br>"
                + "Редактор: ЛКМ — пусто → стена → +1 → −1 → пусто;<br>"
                + "ПКМ или Shift+щелчок — перенести старт</small></html>");
        hint.setForeground(Color.DARK_GRAY);
        addRow(p, hint, null);
        return p;
    }

    private JPanel parametersSection() {
        JPanel p = section("Параметры MDP");
        rewardSpinner.setEditor(new JSpinner.NumberEditor(rewardSpinner, "0.000"));
        gammaSpinner.setEditor(new JSpinner.NumberEditor(gammaSpinner, "0.00"));
        forwardSpinner.setEditor(new JSpinner.NumberEditor(forwardSpinner, "0.00"));
        addRow(p, new JLabel("R(s) обычной клетки"), rewardSpinner);
        addRow(p, new JLabel("γ (дисконтирование)"), gammaSpinner);
        addRow(p, new JLabel("P(движение вперёд)"), forwardSpinner);
        addRow(p, new JLabel("Пример"), presetBox);
        return p;
    }

    private JPanel algorithmSection() {
        JPanel p = section("Алгоритм");
        seedSpinner.setEditor(new JSpinner.NumberEditor(seedSpinner, "#"));
        addRow(p, new JLabel("Метод"), algorithmBox);
        addRow(p, new JLabel("Seed (π₀, эпизоды)"), seedSpinner);
        addRow(p, new JLabel("Итерация"), iterationSlider);
        addRow(p, iterationLabel, null);
        return p;
    }

    private JPanel agentSection() {
        JPanel p = section("Агент");
        JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
        buttons.add(stepButton);
        buttons.add(runButton);
        buttons.add(newEpisodeButton);
        buttons.add(seriesButton);
        addRow(p, buttons, null);
        addRow(p, new JLabel("Задержка, мс"), speedSlider);
        return p;
    }

    private JPanel viewSection() {
        JPanel p = section("Отображение");
        addRow(p, heatMapBox, null);
        addRow(p, utilitiesBox, null);
        addRow(p, policyBox, null);
        return p;
    }

    private JPanel decisionSection() {
        JPanel p = section("Решение агента в текущей клетке");
        decisionArea.setEditable(false);
        decisionArea.setLineWrap(false);
        decisionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(decisionArea);
        scroll.setPreferredSize(new Dimension(300, 150));
        addRow(p, scroll, null);
        return p;
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.putClientProperty("row", 0);
        return p;
    }

    private static void addRow(JPanel p, JComponent left, JComponent right) {
        int row = (Integer) p.getClientProperty("row");
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = row;
        gc.insets = new Insets(2, 4, 2, 4);
        gc.anchor = GridBagConstraints.WEST;
        if (right == null) {
            gc.gridx = 0;
            gc.gridwidth = 2;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;
            p.add(left, gc);
        } else {
            gc.gridx = 0;
            p.add(left, gc);
            gc.gridx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;
            p.add(right, gc);
        }
        p.putClientProperty("row", row + 1);
    }

    private void wireEvents() {
        rewardSpinner.addChangeListener(e -> recompute());
        gammaSpinner.addChangeListener(e -> recompute());
        forwardSpinner.addChangeListener(e -> recompute());
        algorithmBox.addActionListener(e -> recompute());
        seedSpinner.addChangeListener(e -> recompute());
        generateButton.addActionListener(e -> {
            generationCounter++;
            generateWorld();
        });
        widthSpinner.addChangeListener(e -> resizeWorld());
        heightSpinner.addChangeListener(e -> resizeWorld());
        classicButton.addActionListener(e -> {
            updating = true;
            builder = GridWorld.classicBuilder();
            widthSpinner.setValue(4);
            heightSpinner.setValue(3);
            updating = false;
            log("Загружен мир из задания: 4×3, стена (2,2), +1 в (4,3), −1 в (4,2), старт (1,1).");
            recompute();
        });
        presetBox.addActionListener(e -> applyPreset());
        iterationSlider.addChangeListener(e -> {
            if (!updating) {
                showSnapshot(iterationSlider.getValue());
            }
        });
        stepButton.addActionListener(e -> {
            stopTimer();
            doStep();
        });
        runButton.addActionListener(e -> {
            if (runButton.isSelected()) {
                if (agent == null) {
                    stopTimer();
                    return;
                }
                if (episodeOver()) {
                    newEpisode();
                }
                runButton.setText("Пауза");
                timer.start();
            } else {
                stopTimer();
            }
        });
        newEpisodeButton.addActionListener(e -> {
            stopTimer();
            newEpisode();
        });
        seriesButton.addActionListener(e -> runSeries());
        speedSlider.addChangeListener(e -> timer.setDelay(speedSlider.getValue()));
        heatMapBox.addActionListener(e -> gridPanel.setHeatMap(heatMapBox.isSelected()));
        utilitiesBox.addActionListener(e -> gridPanel.setShowUtilities(utilitiesBox.isSelected()));
        policyBox.addActionListener(e -> gridPanel.setShowPolicy(policyBox.isSelected()));
        editModeBox.addActionListener(e -> {
            if (editModeBox.isSelected()) {
                log("Редактор включён: щёлкайте по клеткам мира.");
            }
        });
        gridPanel.setCellClickListener(this::onCellClicked);
    }

    // ------------------------------------------------------------------ логика

    private void onCellClicked(Position cell, boolean primary) {
        if (!editModeBox.isSelected()) {
            log("Чтобы менять клетки, включите «Редактировать мир мышью».");
            return;
        }
        boolean changed = primary ? builder.cycle(cell) : builder.moveStart(cell);
        if (!changed) {
            log(primary ? "Клетку старта менять нельзя — сначала перенесите старт (ПКМ или Shift+щелчок)."
                    : "Старт можно поставить только в обычную клетку.");
            return;
        }
        recompute();
    }

    private void resizeWorld() {
        if (updating) {
            return;
        }
        generateWorld();
    }

    /** Новый случайный мир текущего размера (старт, +1 и −1 — как в мире из задания). */
    private void generateWorld() {
        int width = (Integer) widthSpinner.getValue();
        int height = (Integer) heightSpinner.getValue();
        long seed = ((Number) seedSpinner.getValue()).longValue() * 7_919L + generationCounter;
        GridWorld world = WorldGenerator.generate(width, height, read(rewardSpinner), seed);
        builder = world.toBuilder();
        log("Сгенерирован мир " + width + "×" + height + " (вариант " + (generationCounter + 1) + "): стен "
                + (width * height - world.states().size()) + ", выходов " + world.terminals().size()
                + ", старт " + world.start() + ".");
        recompute();
    }

    private void applyPreset() {
        Preset preset = (Preset) presetBox.getSelectedItem();
        if (preset == null || Double.isNaN(preset.reward())) {
            return;
        }
        updating = true;
        rewardSpinner.setValue(preset.reward());
        gammaSpinner.setValue(preset.gamma());
        updating = false;
        recompute();
    }

    /** Пересчёт стратегии после любого изменения мира или параметров. */
    private void recompute() {
        if (updating) {
            return;
        }
        stopTimer();
        GridWorld world = builder.stepReward(read(rewardSpinner)).build();
        gridPanel.setWorld(world);
        try {
            mdp = new Mdp(world, new TransitionModel(read(forwardSpinner)), read(gammaSpinner));
            Algorithm algorithm = (Algorithm) algorithmBox.getSelectedItem();
            long seed = ((Number) seedSpinner.getValue()).longValue();
            StochasticEnvironment environment = mdp.createEnvironment(seed);
            agent = new MdpAgent(mdp, environment);
            result = agent.computePolicy(algorithm.createSolver(seed));
        } catch (IllegalArgumentException ex) {
            mdp = null;
            agent = null;
            result = null;
            gridPanel.setSolution(null, null);
            gridPanel.setAgent(null, List.of(), null);
            decisionArea.setText("");
            iterationLabel.setText(" ");
            statusLabel.setText("<html><font color='#C62828'><b>Нельзя рассчитать стратегию:</b> " + ex.getMessage() + "</font></html>");
            log("Ошибка: " + ex.getMessage());
            setAgentControlsEnabled(false);
            return;
        }
        setAgentControlsEnabled(true);
        updating = true;
        iterationSlider.setMaximum(result.history().size() - 1);
        iterationSlider.setValue(result.history().size() - 1);
        updating = false;
        showSnapshot(result.history().size() - 1);
        log(String.format("%s: %d итер., %s, %s мс. U%s = %s, R(s) = %s, γ = %s, P = %s",
                result.algorithm(), result.iterations(), result.converged() ? "сошлось" : "НЕ сошлось (лимит)",
                Fmt.fixed(result.elapsedMillis(), 1), mdp.world().start(),
                Fmt.fixed(result.utilities().get(mdp.world().start()), 4), Fmt.signed(mdp.world().stepReward()),
                Fmt.num(mdp.gamma()), Fmt.num(mdp.model().forward())));
        newEpisode();
    }

    private void showSnapshot(int index) {
        if (result == null) {
            return;
        }
        IterationSnapshot s = result.history().get(Math.max(0, Math.min(index, result.history().size() - 1)));
        gridPanel.setSolution(s.utilities(), s.policy());
        boolean last = index == result.history().size() - 1;
        iterationLabel.setText("<html>Итерация <b>" + s.iteration() + "</b> из " + result.history().get(result.history().size() - 1).iteration()
                + (last ? " (итог)" : " (промежуточная)")
                + "<br><small>" + s.note() + "</small></html>");
    }

    private void newEpisode() {
        if (agent == null) {
            return;
        }
        agent.startNewEpisode();
        trail.clear();
        trail.add(agent.position());
        lastStep = null;
        refreshAgentView();
    }

    private boolean episodeOver() {
        return agent == null || agent.environment().isTerminal() || agent.environment().steps() >= MAX_EPISODE_STEPS;
    }

    /** Один шаг агента: решение → перемещение (исход разыгрывает среда). */
    void doStep() {
        if (agent == null) {
            stopTimer();
            return;
        }
        if (episodeOver()) {
            stopTimer();
            log("Эпизод завершён. Нажмите «Новый эпизод» или «Пуск».");
            return;
        }
        if (iterationSlider.getValue() != iterationSlider.getMaximum()) {
            iterationSlider.setValue(iterationSlider.getMaximum());
        }
        lastStep = agent.move();
        trail.add(lastStep.to());
        log(lastStep.describe());
        StochasticEnvironment env = agent.environment();
        if (lastStep.terminal()) {
            log("Эпизод завершён в " + lastStep.to() + " за " + env.steps() + " шагов, сумма наград "
                    + Fmt.fixed(env.totalReward(), 2) + (mdp.gamma() < 1
                    ? ", дисконтированная " + Fmt.fixed(env.discountedReturn(), 3) : ""));
            stopTimer();
        } else if (env.steps() >= MAX_EPISODE_STEPS) {
            log("Эпизод прерван по лимиту " + MAX_EPISODE_STEPS + " шагов (стратегия не ведёт к выходу).");
            stopTimer();
        }
        refreshAgentView();
    }

    private void runSeries() {
        if (agent == null) {
            return;
        }
        long seed = ((Number) seedSpinner.getValue()).longValue() * 1_000_003L + (++seriesCounter);
        EpisodeStatistics stats = agent.simulate(SERIES_EPISODES, seed, MAX_EPISODE_STEPS);
        Position start = mdp.world().start();
        double expected = result.utilities().get(start);
        StringBuilder sb = new StringBuilder();
        sb.append("Серия ").append(stats.episodes()).append(" эпизодов: средняя Σγᵗ·R = ")
                .append(Fmt.fixed(stats.meanReturn(), 4)).append(" ± ").append(Fmt.fixed(stats.confidence95(), 4))
                .append(" (95%), расчётная U").append(start).append(" = ").append(Fmt.fixed(expected, 4))
                .append(", среднее число шагов ").append(Fmt.fixed(stats.meanSteps(), 2));
        stats.exits().forEach((p, n) -> sb.append(", выход ").append(p).append(": ")
                .append(Fmt.fixed(100.0 * n / stats.episodes(), 1)).append('%'));
        if (stats.timeouts() > 0) {
            sb.append(", прервано по лимиту: ").append(stats.timeouts());
        }
        log(sb.toString());
    }

    private void refreshAgentView() {
        if (agent == null) {
            return;
        }
        gridPanel.setAgent(agent.position(), trail, lastStep);
        StochasticEnvironment env = agent.environment();
        String state = env.isTerminal() ? "эпизод завершён" : "в пути";
        String drift = lastStep != null && lastStep.drifted()
                ? " · <font color='#C62828'>снесло ветром: решение " + lastStep.intended().arrow() + ", фактически " + lastStep.actual().arrow() + "</font>"
                : "";
        statusLabel.setText("<html>Агент: <b>" + agent.position() + "</b> · шаг " + env.steps()
                + " · сумма наград <b>" + Fmt.fixed(env.totalReward(), 2) + "</b> · " + state + drift
                + " · U(старт) = " + Fmt.fixed(result.utilities().get(mdp.world().start()), 3) + "</html>");
        decisionArea.setText(TextRenderer.decision(mdp, result.utilities(), result.policy(), agent.position()));
        decisionArea.setCaretPosition(0);
    }

    private void setAgentControlsEnabled(boolean enabled) {
        stepButton.setEnabled(enabled);
        runButton.setEnabled(enabled);
        newEpisodeButton.setEnabled(enabled);
        seriesButton.setEnabled(enabled);
        iterationSlider.setEnabled(enabled);
    }

    private void stopTimer() {
        timer.stop();
        runButton.setSelected(false);
        runButton.setText("Пуск");
    }

    private void log(String line) {
        logArea.append(line + "\n");
        if (logArea.getDocument().getLength() > LOG_LIMIT) {
            logArea.replaceRange("", 0, logArea.getDocument().getLength() - LOG_LIMIT / 2);
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /** Значение спиннера, округлённое до 1e-6 (шаги 0.01 накапливают ошибку вроде 0.9999999999). */
    private static double read(JSpinner spinner) {
        return Math.round(((Number) spinner.getValue()).doubleValue() * 1e6) / 1e6;
    }

    // ------------------------------------------------------------------ для автоматической проверки

    GridPanel gridPanel() {
        return gridPanel;
    }

    MdpAgent agent() {
        return agent;
    }

    String logText() {
        return logArea.getText();
    }

    void setParameters(double reward, double gamma) {
        updating = true;
        rewardSpinner.setValue(reward);
        gammaSpinner.setValue(gamma);
        updating = false;
        recompute();
    }

    void generateForTest(int width, int height) {
        updating = true;
        widthSpinner.setValue(width);
        heightSpinner.setValue(height);
        updating = false;
        generateWorld();
    }

    void clickCell(Position cell, boolean primary) {
        editModeBox.setSelected(true);
        onCellClicked(cell, primary);
    }

    void runSeriesForTest() {
        runSeries();
    }

    void newEpisodeForTest() {
        newEpisode();
    }

    StepRecord lastStepForTest() {
        return lastStep;
    }
}
