package org.randomfetcher;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Utility‑класс для быстрой, но довольно строгой проверки «случайности»
 * целочисленных последовательностей.
 *
 * <p><b>Новое</b> (v2):<br>
 *  – вычисляются и сохраняются точные p‑values для KS, χ² и Runs‑теста;<br>
 *  – добавлены публичные геттеры, чтобы UI/LLM могли их использовать.</p>
 */
public class RandomnessTester {

    /* ------------------- входные данные ------------------- */
    private final List<Integer> sequence;
    private final int minValue;
    private final int maxValue;

    /* ------------------- кеш значений --------------------- */
    private boolean cachedKs = false;
    private boolean cachedChi = false;
    private boolean cachedRuns = false;

    private double ksP;             // p‑value KS
    private double chiSq;           // статистика χ²
    private double chiP;            // p‑value χ²
    private double runsZ;           // z‑оценка Runs
    private double runsP;           // p‑value Runs

    /* ====================================================== */
    public RandomnessTester(List<Integer> sequence, int minValue, int maxValue) {
        if (sequence == null || sequence.isEmpty())
            throw new IllegalArgumentException("Последовательность не должна быть пустой");
        if (minValue >= maxValue)
            throw new IllegalArgumentException("minValue должен быть меньше maxValue");

        this.sequence = List.copyOf(sequence);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /* ====================================================== *
     *                1.  СТАТИСТИЧЕСКИЕ ТЕСТЫ                *
     * ====================================================== */

    /**
     * Kolmogorov–Smirnov — возвращает true, если H0 «равномерно» не отвергается.
     */
    public boolean kolmogorovSmirnovTest(double alpha) {
        if (!cachedKs) computeKs();
        return ksP > alpha;
    }

    /**
     * Chi‑Square (равномерность).
     */
    public boolean chiSquareTest(int bins, double alpha) {
        if (!cachedChi) computeChiSquare(bins);
        return chiP > alpha;
    }

    /**
     * Runs‑test Wald–Wolfowitz (B/A ≥ медианы).
     */
    public boolean runsTest(double alpha) {
        if (!cachedRuns) computeRuns();
        return runsP > alpha;
    }

    /**
     * Автокорреляция заданного лага.
     */
    public double autocorrelation(int lag) {
        if (lag <= 0 || lag >= sequence.size())
            throw new IllegalArgumentException("lag in 1..n-1");

        int n = sequence.size();
        double mean = sequence.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
        double varSum = 0, covSum = 0;
        for (int i = 0; i < n; i++) {
            double diff = sequence.get(i) - mean;
            varSum += diff * diff;
            if (i + lag < n) covSum += diff * (sequence.get(i + lag) - mean);
        }
        return varSum == 0 ? 0 : covSum / ((n - lag) * (varSum / n));
    }

    /**
     * Максимальная длина цепочки одинаковых чисел.
     */
    public int countConsecutiveRepeats() {
        int max = 1, cur = 1;
        for (int i = 1; i < sequence.size(); i++) {
            if (sequence.get(i).equals(sequence.get(i - 1))) {
                cur++;
                max = Math.max(max, cur);
            } else cur = 1;
        }
        return max;
    }

    /**
     * CRC‑32 байтов последовательности (удобный «отпечаток»).
     */
    public long crc32() {
        CRC32 crc = new CRC32();
        byte[] b = new byte[sequence.size()];
        for (int i = 0; i < sequence.size(); i++) b[i] = (byte) (sequence.get(i) & 0xFF);
        crc.update(b);
        return crc.getValue();
    }

    /* ====================================================== *
     *            2.  К Е Ш  &  ВНУТРЕННИЕ РАСЧЁТЫ           *
     * ====================================================== */

    private void computeKs() {
        double[] data = sequence.stream()
                .mapToDouble(v -> (v - minValue + 0.5) / (maxValue - minValue + 1.0))
                .toArray();

        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();

        // 3‑аргументный метод сразу возвращает p‑value (exact = true для n ≤ 10000)
        ksP = ks.kolmogorovSmirnovTest(new UniformRealDistribution(0, 1),
                data,
                true);  // exact calculation

        cachedKs = true;
    }

    private void computeChiSquare(int bins) {
        if (bins < 2) bins = 2;
        int[] observed = new int[bins];
        double step = (maxValue - minValue + 1.0) / bins;
        for (int v : sequence) {
            int bin = Math.min((int) ((v - minValue) / step), bins - 1);
            observed[bin]++;
        }
        double expected = (double) sequence.size() / bins;
        chiSq = 0d;
        for (int o : observed)
            chiSq += (o - expected) * (o - expected) / expected;

        ChiSquaredDistribution dist = new ChiSquaredDistribution(bins - 1);
        chiP = 1.0 - dist.cumulativeProbability(chiSq);
        cachedChi = true;
    }

    private void computeRuns() {
        double median = calcMedian();
        int runs = 1, n1 = 0, n0 = 0;
        int prev = sequence.getFirst() >= median ? 1 : 0;
        if (prev == 1) n1++; else n0++;

        for (int i = 1; i < sequence.size(); i++) {
            int curr = sequence.get(i) >= median ? 1 : 0;
            if (curr != prev) runs++;
            if (curr == 1) n1++; else n0++;
            prev = curr;
        }

        int n = sequence.size();
        double exp = (2.0 * n1 * n0) / n + 1;
        double var = (2.0 * n1 * n0 * (2.0 * n1 * n0 - n)) /
                (Math.pow(n, 2) * (n - 1));
        runsZ = Math.abs((runs - exp) / Math.sqrt(var));

        NormalDistribution nd = new NormalDistribution();
        runsP = 2 * (1.0 - nd.cumulativeProbability(runsZ)); // two‑tailed
        cachedRuns = true;
    }

    private double calcMedian() {
        List<Integer> c = new ArrayList<>(sequence);
        Collections.sort(c);
        int n = c.size();
        return (n % 2 == 0) ? (c.get(n / 2 - 1) + c.get(n / 2)) / 2.0 : c.get(n / 2);
    }

    /* ====================================================== *
     *                       3.  Г Е Т Т Е Р Ы                *
     * ====================================================== */

    public double ksPValue() {
        if (!cachedKs) computeKs();
        return ksP;
    }

    public double chiSquare() {
        if (!cachedChi) computeChiSquare(8);
        return chiSq;
    }

    public double chiPValue() {
        if (!cachedChi) computeChiSquare(8);
        return chiP;
    }

    public double runsZ() {
        if (!cachedRuns) computeRuns();
        return runsZ;
    }

    public double runsPValue()    { if (!cachedRuns) computeRuns();      return runsP; }
}
