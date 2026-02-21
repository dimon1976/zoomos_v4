package com.java.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Утилиты для работы со штрихкодами.
 * Используется в справочнике штрихкодов (BarcodeHandbookService)
 * и утилите сопоставления (BarcodeMatchService).
 */
public final class BarcodeUtils {

    private BarcodeUtils() {}

    /**
     * Разбирает строку штрихкодов, разделённых запятой, и нормализует каждый:
     * убирает пробелы, неразрывные пробелы, управляющие символы, нули слева.
     *
     * Примеры:
     *   "4607086563665"          → ["4607086563665"]
     *   "4607086563665, 123"     → ["4607086563665", "123"]
     *   " 4607086563665 "        → ["4607086563665"]
     *   "04607086563665"         → ["4607086563665"]  (ведущий ноль снимается только если результат 13 цифр)
     *
     * @param raw исходная строка из файла
     * @return список нормализованных штрихкодов (без пустых)
     */
    public static List<String> parseAndNormalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(BarcodeUtils::normalize)
                .filter(b -> !b.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Нормализует один штрихкод:
     * - убирает пробелы, табы, неразрывные пробелы (NBSP, NNBSP и др.)
     * - убирает управляющие символы
     * - убирает ведущие нули если итоговая длина 14 цифр → оставляем 13
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        // Убираем все пробельные и управляющие символы по краям
        String s = raw.replaceAll("[\\s\\u00A0\\u202F\\u200B\\u2060\\uFEFF]+", " ").trim();
        // Убираем управляющие символы внутри
        s = s.replaceAll("[\\p{Cntrl}]", "").trim();
        // Если строка выглядит как число с ведущим нулём и длиной 14 → снимаем один ноль
        if (s.matches("0\\d{13}")) {
            s = s.substring(1);
        }
        return s;
    }
}
