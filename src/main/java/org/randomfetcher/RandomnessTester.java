package org.randomfetcher;

import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * Utility-class for quick (but reasonably rigorous) checks of «random-looking» integer sequences.
 * <p>
 * Features:
 *  <ul>
 *      <li>CRC-32 checksum calculation</li>
 *      <li>Pattern search (sub-sequence match)</li>
 *      <li>Duplicate index map</li>
 *      <li>Runs-test (Wald–Wolfowitz) for stricter pseudo-randomness checking</li>
 *      <li>Serial autocorrelation helper</li>
 *      <li>Kolmogorov-Smirnov test for uniform distribution</li>
 *      <li>Chi-square test for uniformity</li>
 *  </ul>
 * All methods are <strong>side-effect-free</strong> and dependency-free.
 */
public class RandomnessTester {
    private final List<Integer> sequence;
    private final int minValue;
    private final int maxValue;

    /**
     * Конструктор класса RandomnessTester.
     *
     * @param sequence  Последовательность чисел для тестирования.
     * @param minValue  Минимальное значение диапазона.
     * @param maxValue  Максимальное значение диапазона.
     */
    public RandomnessTester(List<Integer> sequence, int minValue, int maxValue) {
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("Последовательность не должна быть пустой или null");
        }
        if (minValue >= maxValue) {
            throw new IllegalArgumentException("minValue должен быть меньше maxValue");
        }
        this.sequence = List.copyOf(sequence);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    // =====================
    //  СТАТИСТИЧЕСКИЕ ТЕСТЫ
    // =====================

    /**
     * Тест Колмогорова–Смирнова на соответствие равномерному распределению.
     *
     * @param alpha уровень значимости (обычно 0.05)
     * @return {@code true} если гипотеза о равномерном распределении не отвергается
     */
    public boolean kolmogorovSmirnovTest(double alpha) {
        List<Integer> sorted = new ArrayList<>(sequence);
        Collections.sort(sorted);
        int n = sequence.size();
        double dMax = 0;

        // Empirical CDF vs. uniform CDF
        for (int i = 0; i < n; i++) {
            double x = sorted.get(i);
            double empiricalCdf = (i + 1.0) / n;
            double uniformCdf = (x - minValue) / (maxValue - minValue + 1.0);
            dMax = Math.max(dMax, Math.abs(empiricalCdf - uniformCdf));
        }

        // Approximate critical value for KS test
        double criticalValue = Math.sqrt(-0.5 * Math.log(alpha / 2.0)) / Math.sqrt(n);
        return dMax < criticalValue;
    }

    /**
     * Тест на равномерность (хи-квадрат).
     *
     * @param bins количество интервалов
     * @param alpha уровень значимости
     * @return {@code true} если последовательность проходит тест
     */
    public boolean chiSquareTest(int bins, double alpha) {
        int[] observed = new int[bins];
        double range = (maxValue - minValue + 1) / (double) bins;
        for (int value : sequence) {
            int bin = Math.min((int) ((value - minValue) / range), bins - 1);
            observed[bin]++;
        }
        double expected = (double) sequence.size() / bins;
        double chiSquare = 0;
        for (int obs : observed) {
            chiSquare += Math.pow(obs - expected, 2) / expected;
        }

        // Simplified critical value approximation for chi-square
        double criticalValue = bins - 1 + 2 * Math.sqrt(bins - 1);
        return chiSquare < criticalValue;
    }

    /**
     * Стандартная автокорреляция (Пирсона) для произвольного лага.
     *
     * @param lag смещение ≥1
     * @return значение автокорреляции в диапазоне [-1; 1]
     */
    public double autocorrelation(int lag) {
        if (lag <= 0 || lag >= sequence.size()) {
            throw new IllegalArgumentException("lag должен быть в диапазоне 1 .. size-1");
        }
        double mean = sequence.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
        double variance = sequence.stream().mapToDouble(x -> Math.pow(x - mean, 2)).sum();
        double autocorr = 0;
        for (int i = 0; i < sequence.size() - lag; i++) {
            autocorr += (sequence.get(i) - mean) * (sequence.get(i + lag) - mean);
        }
        return variance == 0 ? 0 : autocorr / variance;
    }

    /**
     * Runs-test (Wald–Wolfowitz) – проверка случайности через количество серий.
     * <p>Числа ≥ медианы – группа «B», ниже – группа «A».</p>
     *
     * @param alpha уровень значимости (обычно 0.05)
     * @return {@code true} если последовательность проходит тест
     */
    public boolean runsTest(double alpha) {
        double median = calcMedian();
        int runs = 1;
        int n1 = 0; // B – ≥ median
        int n0 = 0; // A – < median

        int prev = sequence.getFirst() >= median ? 1 : 0;
        if (prev == 1) n1++; else n0++;

        for (int i = 1; i < sequence.size(); i++) {
            int curr = sequence.get(i) >= median ? 1 : 0;
            if (curr != prev) runs++;
            if (curr == 1) n1++; else n0++;
            prev = curr;
        }

        int n = sequence.size();
        double expectedRuns = (2.0 * n1 * n0) / n + 1;
        double varianceRuns = (2.0 * n1 * n0 * (2.0 * n1 * n0 - n)) / (Math.pow(n, 2) * (n - 1));
        double z = Math.abs((runs - expectedRuns) / Math.sqrt(varianceRuns));

        // Approximate critical value for z (standard normal, two-tailed)
        double criticalZ = 1.96; // For alpha = 0.05
        return z < criticalZ;
    }

    /**
     * Подсчёт максимальной длины цепочки подряд идущих одинаковых чисел.
     *
     * @return максимальная длина цепочки повторяющихся чисел
     */
    public int countConsecutiveRepeats() {
        int maxRepeats = 1;
        int currentRepeats = 1;
        for (int i = 1; i < sequence.size(); i++) {
            if (sequence.get(i).equals(sequence.get(i - 1))) {
                currentRepeats++;
                maxRepeats = Math.max(maxRepeats, currentRepeats);
            } else {
                currentRepeats = 1;
            }
        }
        return maxRepeats;
    }

    // --------------------
    //  МЕТОДЫ «ИНЖЕНЕРНЫЕ»
    // --------------------

    /**
     * CRC-32 контрольная сумма последовательности (байтовая трактовка каждого int).
     *
     * @return 32-битное значение CRC в диапазоне 0 .. 0xFFFF_FFFFL
     */
    public long crc32() {
        CRC32 crc = new CRC32();
        byte[] bytes = new byte[sequence.size()];
        for (int i = 0; i < sequence.size(); i++) {
            bytes[i] = (byte) (sequence.get(i) & 0xFF);
        }
        crc.update(bytes);
        return crc.getValue();
    }

    /**
     * Поиск первой позиции вхождения под-последовательности.
     *
     * @param pattern список чисел для поиска
     * @return индекс первого совпадения или –1, если под-последовательность не найдена
     */
    public int findPattern(List<Integer> pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Pattern не должен быть null или пустым");
        }
        int patSize = pattern.size();
        if (patSize > sequence.size()) return -1;
        outer:
        for (int i = 0; i <= sequence.size() - patSize; i++) {
            for (int j = 0; j < patSize; j++) {
                if (!sequence.get(i + j).equals(pattern.get(j))) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * Карта «число → индексы появления» для всех значений, встречающихся ≥2 раза.
     *
     * @return {@link Map} где ключ – число, значение – read-only-список его позиций
     */
    public Map<Integer, List<Integer>> duplicatePositions() {
        Map<Integer, List<Integer>> map = new HashMap<>();
        for (int i = 0; i < sequence.size(); i++) {
            int val = sequence.get(i);
            map.computeIfAbsent(val, k -> new ArrayList<>()).add(i);
        }
        return map.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    // =====
    //  Вспомогательные методы
    // =====

    private double calcMedian() {
        List<Integer> copy = new ArrayList<>(sequence);
        Collections.sort(copy);
        int n = copy.size();
        return (n % 2 == 0) ? (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0 : copy.get(n / 2);
    }

}
