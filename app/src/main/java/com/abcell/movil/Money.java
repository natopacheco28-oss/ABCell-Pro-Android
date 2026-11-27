package com.abcell.movil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Money is calculated as integer cents, never with floating-point arithmetic. */
public final class Money {
    private Money() {}
    public static long cents(String text) {
        try {
            String value = text == null || text.trim().isEmpty() ? "0" : text.trim();
            if (!value.matches("[0-9]+([.,][0-9]{1,2})?")) throw new NumberFormatException();
            long cents = new BigDecimal(value.replace(',', '.')).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (cents > 99999999900L) throw new NumberFormatException();
            return cents;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Escribe un importe positivo con hasta 2 decimales, sin separadores de miles.");
        }
    }
    public static long stored(double value) { return Math.round(value * 100); }
    public static String edit(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    public static String format(long cents) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "MX")).format(BigDecimal.valueOf(cents, 2));
    }
    public static long balance(long total, long paid) {
        if (total < 0 || paid < 0 || paid > total)
            throw new IllegalArgumentException("El total no puede ser menor a los abonos registrados.");
        return total - paid;
    }
    public static long pay(long total, long paid, long payment) {
        if (payment <= 0 || payment > balance(total, paid))
            throw new IllegalArgumentException("El abono debe ser mayor a cero y no superar el saldo.");
        return paid + payment;
    }
}
