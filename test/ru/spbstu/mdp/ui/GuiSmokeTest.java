package ru.spbstu.mdp.ui;

import ru.spbstu.mdp.model.Position;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Проверка GUI без участия человека: окно собирается (не показывается), прогоняется сценарий
 * (шаги агента, редактор мира, недопустимые параметры, генерация мира), снимки сохраняются в PNG.
 * Запуск: java -cp out/build:out/test-build ru.spbstu.mdp.ui.GuiSmokeTest каталог_для_снимков
 */
public final class GuiSmokeTest {

    private GuiSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "out/gui");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("Не удалось создать " + dir);
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                scenario(dir);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.exit(0);
    }

    private static void scenario(File dir) throws Exception {
        MainFrame frame = new MainFrame();
        frame.setSize(1280, 860);
        frame.validate();
        require(frame.agent() != null, "агент создан");
        for (int i = 0; i < 4 && !frame.agent().environment().isTerminal(); i++) {
            frame.doStep();
        }
        save(frame, new File(dir, "1_classic_steps.png"));

        frame.setParameters(-2.0, 1.0);
        require(frame.agent().policy().get(new Position(3, 2)) == ru.spbstu.mdp.model.Action.RIGHT, "R = −2 → (3,2) вправо");
        save(frame, new File(dir, "2_reward_minus2.png"));

        frame.setParameters(0.3, 1.0);
        require(frame.agent() == null, "γ = 1, R > 0 отклонено");
        require(frame.logText().contains("полезность не ограничена"), "сообщение об ошибке в журнале");
        save(frame, new File(dir, "3_error.png"));

        frame.setParameters(-0.04, 1.0);
        frame.clickCell(new Position(3, 1), true);   // пусто → стена
        frame.clickCell(new Position(1, 3), false);  // старт в (1,3)
        require(frame.agent() != null && frame.agent().mdp().world().isWall(new Position(3, 1)), "редактор: стена (3,1)");
        require(frame.agent().mdp().world().start().equals(new Position(1, 3)), "редактор: старт (1,3)");
        frame.clickCell(new Position(1, 3), true);   // клетку старта менять нельзя
        require(frame.logText().contains("Клетку старта менять нельзя"), "редактор: защита старта");
        for (int i = 0; i < 3 && !frame.agent().environment().isTerminal(); i++) {
            frame.doStep();
        }
        save(frame, new File(dir, "4_edited_world.png"));
        frame.generateForTest(7, 5);
        require(frame.agent() != null && frame.agent().mdp().world().width() == 7
                && frame.agent().mdp().world().height() == 5, "генерация мира 7×5");
        require(frame.logText().contains("Сгенерирован мир 7×5"), "генерация в журнале");
        save(frame, new File(dir, "5_generated_world.png"));
        frame.dispose();
        System.out.println("GUI: сценарий пройден, снимки в " + dir);
    }

    private static void save(MainFrame frame, File file) throws Exception {
        if (!frame.isDisplayable()) {
            frame.addNotify(); // создаёт ресурсы окна, не показывая его — иначе текстовые поля рисуются пустыми
        }
        var root = frame.getRootPane();
        root.setSize(frame.getSize());
        root.doLayout();
        validateTree(root);
        BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            root.printAll(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(img, "png", file);
    }

    private static void validateTree(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container cc) {
                validateTree(cc);
            }
        }
    }

    private static void require(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("GUI: не выполнено — " + what);
        }
        System.out.println("  ✓ " + what);
    }
}
