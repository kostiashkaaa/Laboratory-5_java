package ru.spbstu.mdp;

import ru.spbstu.mdp.console.ConsoleRunner;
import ru.spbstu.mdp.ui.MainFrame;
import ru.spbstu.mdp.ui.SnapshotRenderer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Лабораторная работа 5 — «Движение агента в стохастической среде».
 * Без аргументов запускает графический интерфейс, с {@code --console} — текстовый режим.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            out.println("Ошибка: " + e.getMessage());
            out.println(Options.USAGE);
            System.exit(2);
            return;
        }
        try {
            switch (options.mode) {
                case HELP -> out.println(Options.USAGE);
                case CONSOLE -> ConsoleRunner.run(options, out);
                case SNAPSHOT -> {
                    SnapshotRenderer.render(options);
                    out.println("Визуализация сохранена: " + options.snapshotPath);
                }
                case GUI -> SwingUtilities.invokeLater(() -> {
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                        // остаётся стандартный вид Swing
                    }
                    new MainFrame().setVisible(true);
                });
            }
        } catch (IllegalArgumentException e) {
            out.println("Ошибка: " + e.getMessage());
            System.exit(2);
        } catch (java.io.IOException e) {
            out.println("Ошибка записи файла: " + e.getMessage());
            System.exit(1);
        }
    }
}
