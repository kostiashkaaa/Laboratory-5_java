package ru.spbstu.mdp.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Форматирование чисел с точкой в качестве разделителя, независимо от локали системы. */
public final class Fmt {

    private Fmt() {
    }

    /** Компактная запись: до 4 знаков после точки без хвостовых нулей (0.8, −0.04, 1). */
    public static String num(double value) {
        String s = new DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
        return "-0".equals(s) ? "0" : s;
    }

    /** То же, но со знаком «+» у положительных чисел (+1, −0.04). */
    public static String signed(double value) {
        String s = num(value);
        return value > 0 && !"0".equals(s) ? "+" + s : s;
    }

    /** Фиксированное число знаков после точки. */
    public static String fixed(double value, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", value);
    }

    /** Экспоненциальная запись с двумя знаками (1.23e-10). */
    public static String sci(double value) {
        return String.format(Locale.ROOT, "%.2e", value);
    }
}
