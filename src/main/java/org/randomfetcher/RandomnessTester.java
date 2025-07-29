package org.randomfetcher;


import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

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
 * All methods are <strong>side-effect-free</strong>.
 */
public class RandomnessTester {
    private final List<Integer> sequence;
    private final int minValue;
    private final int maxValue;

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

    public boolean kolmogorovSmirnovTest(double alpha) {
        double range = (maxValue - minValue + 1.0);
        double[] data = sequence.stream()
                .mapToDouble(v -> (v - minValue + 0.5) / range)
                .toArray();

        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        return !ks.kolmogorovSmirnovTest(new UniformRealDistribution(0, 1), data, alpha);
    }

    public boolean chiSquareTest(int bins, double alpha) {
        if (bins < 2) throw new IllegalArgumentException("bins >= 2");
        int[] observed = new int[bins];
        double step = (maxValue - minValue + 1.0) / bins;

        for (int v : sequence) {
            int bin = Math.min((int) ((v - minValue) / step), bins - 1);
            observed[bin]++;
        }
        double expected = (double) sequence.size() / bins;
        double chiSq = 0d;
        for (int o : observed) {
            chiSq += (o - expected) * (o - expected) / expected;
        }
        ChiSquaredDistribution dist = new ChiSquaredDistribution(bins - 1);
        double critical = dist.inverseCumulativeProbability(1 - alpha);
        return chiSq <= critical;
    }

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

    public boolean runsTest(double alpha) {
        double median = calcMedian();
        int runs = 1;
        int n1 = 0, n0 = 0;

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

        NormalDistribution nd = new NormalDistribution();
        double criticalZ = nd.inverseCumulativeProbability(1 - alpha / 2.0);
        return z < criticalZ;
    }

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

    public long crc32() {
        CRC32 crc = new CRC32();
        byte[] bytes = new byte[sequence.size()];
        for (int i = 0; i < sequence.size(); i++) {
            bytes[i] = (byte) (sequence.get(i) & 0xFF);
        }
        crc.update(bytes);
        return crc.getValue();
    }

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

    private double calcMedian() {
        List<Integer> copy = new ArrayList<>(sequence);
        Collections.sort(copy);
        int n = copy.size();
        return (n % 2 == 0) ? (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0 : copy.get(n / 2);
    }
}