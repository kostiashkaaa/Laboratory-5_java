package ru.spbstu.mdp.ui;

import ru.spbstu.mdp.model.Action;
import ru.spbstu.mdp.model.Policy;
import ru.spbstu.mdp.model.Position;
import ru.spbstu.mdp.model.Utilities;
import ru.spbstu.mdp.util.Fmt;
import ru.spbstu.mdp.world.CellType;
import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.StepRecord;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Рисует мир в стиле рисунков задания: синие границы клеток, стена, выходы +1/−1, полезности U(s),
 * стрелки стратегии π(s), путь и текущее положение агента.
 */
public final class GridPanel extends JPanel {

    /** Обработчик щелчка по клетке (редактор мира). */
    public interface CellClickListener {
        void cellClicked(Position cell, boolean primaryButton);
    }

    private static final Color BORDER = new Color(0x1F35C8);
    private static final Color WALL = new Color(0x1A28A8);
    private static final Color GOOD = new Color(0x1E8E3E);
    private static final Color BAD = new Color(0xC62828);
    private static final Color ARROW = new Color(0x1F35C8);
    private static final Color AGENT = new Color(0xF28C28);
    private static final Color TRAIL = new Color(51, 102, 204, 120);
    private static final Color LOW = new Color(0xFFD9D9);
    private static final Color MID = new Color(0xFFF7D6);
    private static final Color HIGH = new Color(0xD6F2D9);

    private GridWorld world;
    private Utilities utilities;
    private Policy policy;
    private Position agent;
    private List<Position> trail = List.of();
    private StepRecord lastStep;
    private boolean heatMap = true;
    private boolean showUtilities = true;
    private boolean showPolicy = true;
    private CellClickListener clickListener;

    public GridPanel() {
        setPreferredSize(new Dimension(660, 500));
        setBackground(Color.WHITE);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (clickListener == null || world == null) {
                    return;
                }
                Position p = cellAt(e.getX(), e.getY());
                if (p != null) {
                    boolean primary = SwingUtilities.isLeftMouseButton(e) && !e.isControlDown() && !e.isShiftDown();
                    clickListener.cellClicked(p, primary);
                }
            }
        });
    }

    public void setWorld(GridWorld world) {
        this.world = world;
        repaint();
    }

    public void setSolution(Utilities utilities, Policy policy) {
        this.utilities = utilities;
        this.policy = policy;
        repaint();
    }

    public void setAgent(Position agent, List<Position> trail, StepRecord lastStep) {
        this.agent = agent;
        this.trail = trail == null ? List.of() : List.copyOf(trail);
        this.lastStep = lastStep;
        repaint();
    }

    public void setHeatMap(boolean heatMap) {
        this.heatMap = heatMap;
        repaint();
    }

    public void setShowUtilities(boolean showUtilities) {
        this.showUtilities = showUtilities;
        repaint();
    }

    public void setShowPolicy(boolean showPolicy) {
        this.showPolicy = showPolicy;
        repaint();
    }

    public void setCellClickListener(CellClickListener listener) {
        this.clickListener = listener;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (world == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            render(g);
        } finally {
            g.dispose();
        }
    }

    /** Отрисовка в произвольный Graphics2D (используется и для сохранения картинки без окна). */
    public void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Geometry geo = geometry();
        double[] range = utilityRange();
        for (int y = 1; y <= world.height(); y++) {
            for (int x = 1; x <= world.width(); x++) {
                paintCell(g, geo, new Position(x, y), range);
            }
        }
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(Math.max(2f, geo.cell / 40f)));
        for (int y = 1; y <= world.height(); y++) {
            for (int x = 1; x <= world.width(); x++) {
                Rectangle r = cellRect(geo, new Position(x, y));
                g.drawRect(r.x, r.y, r.width, r.height);
            }
        }
        paintAxes(g, geo);
        paintTrail(g, geo);
        paintAgent(g, geo);
    }

    private void paintCell(Graphics2D g, Geometry geo, Position p, double[] range) {
        Rectangle r = cellRect(geo, p);
        CellType type = world.cell(p);
        if (type == CellType.WALL) {
            g.setColor(WALL);
            g.fillRect(r.x, r.y, r.width, r.height);
            return;
        }
        boolean terminal = type == CellType.TERMINAL;
        g.setColor(heatMap && utilities != null && !terminal ? heat(utilities.get(p), range) : Color.WHITE);
        g.fillRect(r.x, r.y, r.width, r.height);
        int cell = geo.cell;
        int cx = r.x + cell / 2;
        int cy = r.y + cell / 2;

        if (terminal) {
            double reward = world.reward(p);
            Color c = reward >= 0 ? GOOD : BAD;
            int inset = (int) (cell * 0.2);
            g.setColor(c);
            g.setStroke(new BasicStroke(Math.max(2f, cell / 28f)));
            g.drawRect(r.x + inset, r.y + (int) (cell * 0.3), cell - 2 * inset, (int) (cell * 0.4));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, cell / 5)));
            drawCentered(g, Fmt.signed(reward), cx, cy);
            return;
        }
        if (showPolicy && policy != null && policy.get(p) != null) {
            drawArrow(g, cx, cy - (showUtilities && utilities != null ? cell / 9 : 0), policy.get(p), cell * 0.42, cell / 14f);
        }
        if (showUtilities && utilities != null) {
            g.setColor(new Color(0x1F2F8F));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, cell / 7)));
            drawCentered(g, Fmt.fixed(utilities.get(p), 3), cx, r.y + (int) (cell * 0.82));
        }
        if (p.equals(world.start())) {
            g.setColor(new Color(0x1F2F8F));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, cell / 9)));
            g.drawString("Start", r.x + cell / 16, r.y + cell / 16 + g.getFontMetrics().getAscent());
        }
    }

    private void paintAxes(Graphics2D g, Geometry geo) {
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int y = 1; y <= world.height(); y++) {
            Rectangle r = cellRect(geo, new Position(1, y));
            drawCentered(g, String.valueOf(y), r.x - 16, r.y + r.height / 2);
        }
        for (int x = 1; x <= world.width(); x++) {
            Rectangle r = cellRect(geo, new Position(x, 1));
            drawCentered(g, String.valueOf(x), r.x + r.width / 2, r.y + r.height + 16);
        }
    }

    private void paintTrail(Graphics2D g, Geometry geo) {
        if (trail.size() < 2) {
            return;
        }
        g.setColor(TRAIL);
        g.setStroke(new BasicStroke(Math.max(3f, geo.cell / 18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D path = new Path2D.Double();
        for (int i = 0; i < trail.size(); i++) {
            double[] a = anchor(geo, trail.get(i));
            double px = a[0];
            double py = a[1];
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        g.draw(path);
    }

    private void paintAgent(Graphics2D g, Geometry geo) {
        if (agent == null) {
            return;
        }
        double[] a = anchor(geo, agent);
        int radius = Math.max(7, (int) (geo.cell * 0.13));
        int cx = (int) Math.round(a[0]);
        int cy = (int) Math.round(a[1]);
        if (lastStep != null && lastStep.drifted() && !world.isTerminal(agent)) {
            // Пунктирное кольцо: на последнем шаге агента снесло ветром.
            g.setColor(BAD);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
            g.drawOval(cx - radius - 6, cy - radius - 6, 2 * radius + 12, 2 * radius + 12);
        }
        g.setColor(new Color(AGENT.getRed(), AGENT.getGreen(), AGENT.getBlue(), 230));
        g.fillOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
        g.setColor(new Color(0x7A3E00));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, radius)));
        g.setColor(Color.WHITE);
        drawCentered(g, "A", cx, cy);
    }

    /**
     * Точка агента и его пути внутри клетки — правый верхний угол, чтобы не закрывать
     * стрелку стратегии, полезность и метку выхода (+1 / −1).
     */
    private double[] anchor(Geometry geo, Position p) {
        Rectangle r = cellRect(geo, p);
        return new double[]{r.x + geo.cell * 0.82, r.y + geo.cell * 0.19};
    }

    private static void drawArrow(Graphics2D g, int cx, int cy, Action a, double length, float thickness) {
        double half = length / 2;
        double x1 = cx - a.dx() * half;
        double y1 = cy + a.dy() * half;
        double x2 = cx + a.dx() * half;
        double y2 = cy - a.dy() * half;
        double head = Math.max(6, length * 0.38);
        // Стержень заканчивается у основания наконечника.
        double bx = x2 - a.dx() * head * 0.8;
        double by = y2 + a.dy() * head * 0.8;
        g.setColor(ARROW);
        g.setStroke(new BasicStroke(Math.max(2f, thickness), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.drawLine((int) x1, (int) y1, (int) bx, (int) by);
        double px = a.dy();   // перпендикуляр в экранных координатах
        double py = a.dx();
        Polygon tip = new Polygon();
        tip.addPoint((int) Math.round(x2), (int) Math.round(y2));
        tip.addPoint((int) Math.round(x2 - a.dx() * head + px * head * 0.55), (int) Math.round(y2 + a.dy() * head + py * head * 0.55));
        tip.addPoint((int) Math.round(x2 - a.dx() * head - px * head * 0.55), (int) Math.round(y2 + a.dy() * head - py * head * 0.55));
        g.fillPolygon(tip);
    }

    private static void drawCentered(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    private double[] utilityRange() {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        if (utilities != null) {
            for (Position s : world.states()) {
                if (!world.isTerminal(s)) {
                    min = Math.min(min, utilities.get(s));
                    max = Math.max(max, utilities.get(s));
                }
            }
        }
        return new double[]{min, max};
    }

    private static Color heat(double value, double[] range) {
        double span = range[1] - range[0];
        double t = span > 1e-12 ? (value - range[0]) / span : 0.5;
        return t < 0.5 ? mix(LOW, MID, t * 2) : mix(MID, HIGH, (t - 0.5) * 2);
    }

    private static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private record Geometry(int originX, int originY, int cell) {
    }

    private Geometry geometry() {
        int left = 36;
        int bottom = 32;
        int top = 12;
        int right = 12;
        int availableW = Math.max(1, getWidth() - left - right);
        int availableH = Math.max(1, getHeight() - top - bottom);
        int cell = Math.max(12, Math.min(availableW / world.width(), availableH / world.height()));
        int ox = left + Math.max(0, (availableW - cell * world.width()) / 2);
        int oy = top + Math.max(0, (availableH - cell * world.height()) / 2);
        return new Geometry(ox, oy, cell);
    }

    private Rectangle cellRect(Geometry geo, Position p) {
        return new Rectangle(geo.originX + (p.x() - 1) * geo.cell,
                geo.originY + (world.height() - p.y()) * geo.cell, geo.cell, geo.cell);
    }

    /** Клетка под точкой экрана или {@code null}. */
    Position cellAt(int px, int py) {
        Geometry geo = geometry();
        if (px < geo.originX || py < geo.originY) {
            return null;
        }
        int col = (px - geo.originX) / geo.cell;
        int row = (py - geo.originY) / geo.cell;
        if (col >= world.width() || row >= world.height()) {
            return null;
        }
        return new Position(col + 1, world.height() - row);
    }
}
