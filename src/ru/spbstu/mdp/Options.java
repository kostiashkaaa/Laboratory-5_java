package ru.spbstu.mdp;

import ru.spbstu.mdp.world.GridWorld;
import ru.spbstu.mdp.world.TransitionModel;

/** Параметры командной строки. */
public final class Options {

    public enum Mode { GUI, CONSOLE, SNAPSHOT, HELP }

    public Mode mode = Mode.GUI;
    public double reward = GridWorld.DEFAULT_STEP_REWARD;
    public double gamma = 1.0;
    public double forward = TransitionModel.DEFAULT_FORWARD;
    /** pi, vi или both. */
    public String algorithm = "both";
    public int episodes = 10_000;
    public int maxSteps = 1_000;
    public long seed = 42;
    public boolean riskDemo;
    public String snapshotPath = "visualization.png";

    public static final String USAGE = """
            Использование:
              java -jar laba5.jar                 — графический интерфейс
              java -jar laba5.jar --console [опции] — текстовый режим
              java -jar laba5.jar --snapshot файл.png [опции] — сохранить картинку визуализации
            Опции:
              --reward R      награда R(s) обычной клетки (по умолчанию −0.04)
              --gamma G       коэффициент дисконтирования γ ∈ (0; 1] (по умолчанию 1)
              --p P           вероятность движения в выбранном направлении (по умолчанию 0.8)
              --algo A        pi | vi | both — итерация по стратегиям / по значениям / обе
              --episodes N    число эпизодов для проверки Монте-Карло (по умолчанию 10000)
              --max-steps N   лимит шагов в эпизоде (по умолчанию 1000)
              --seed S        зерно генератора случайных чисел (по умолчанию 42)
              --risk-demo     показать, как стратегия меняется при разных R(s)
              --help          эта справка
            """;

    public static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--console" -> o.mode = Mode.CONSOLE;
                case "--help", "-h" -> o.mode = Mode.HELP;
                case "--risk-demo" -> o.riskDemo = true;
                case "--snapshot" -> {
                    o.mode = Mode.SNAPSHOT;
                    o.snapshotPath = value(args, ++i, arg);
                }
                case "--reward" -> o.reward = number(args, ++i, arg);
                case "--gamma" -> o.gamma = number(args, ++i, arg);
                case "--p" -> o.forward = number(args, ++i, arg);
                case "--algo" -> {
                    o.algorithm = value(args, ++i, arg);
                    if (!o.algorithm.equals("pi") && !o.algorithm.equals("vi") && !o.algorithm.equals("both")) {
                        throw new IllegalArgumentException("--algo: ожидается pi, vi или both, получено " + o.algorithm);
                    }
                }
                case "--episodes" -> o.episodes = positive(args, ++i, arg);
                case "--max-steps" -> o.maxSteps = positive(args, ++i, arg);
                case "--seed" -> o.seed = (long) number(args, ++i, arg);
                default -> throw new IllegalArgumentException("Неизвестный аргумент: " + arg);
            }
        }
        return o;
    }

    private static String value(String[] args, int i, String name) {
        if (i >= args.length) {
            throw new IllegalArgumentException("После " + name + " ожидается значение");
        }
        return args[i];
    }

    private static double number(String[] args, int i, String name) {
        String v = value(args, i, name);
        try {
            double d = Double.parseDouble(v.replace(',', '.').replace('−', '-'));
            if (!Double.isFinite(d)) {
                throw new NumberFormatException();
            }
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": ожидается число, получено " + v);
        }
    }

    private static int positive(String[] args, int i, String name) {
        double d = number(args, i, name);
        if (d < 1 || d > 10_000_000 || d != Math.rint(d)) {
            throw new IllegalArgumentException(name + ": ожидается целое положительное число, получено " + args[i]);
        }
        return (int) d;
    }
}
