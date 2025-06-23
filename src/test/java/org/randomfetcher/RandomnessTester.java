package org.randomfetcher;

import java.util.List;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.apache.commons.math3.distribution.UniformRealDistribution;


public class RandomnessTester {
    private final List<Integer> sequence;
    private final int minValue;
    private final int maxValue;

    /**
     * Конструктор класса RandomnessTester.
     * @param sequence Последовательность чисел для тестирования.
     * @param minValue Минимальное значение диапазона.
     * @param maxValue Максимальное значение диапазона.
     */
    public RandomnessTester(List<Integer> sequence, int minValue, int maxValue) {
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("Последовательность не должна быть пустой или null");
        }
        if (minValue >= maxValue) {
            throw new IllegalArgumentException("minValue должен быть меньше maxValue");
        }
        this.sequence = sequence;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /**
     * Тест Колмогорова-Смирнова для проверки соответствия равномерному распределению.
     * @param alpha Уровень значимости (обычно 0.05).
     * @return true, если последовательность проходит тест (p-value > alpha).
     */


    public boolean kolmogorovSmirnovTest(double alpha) {
        double[] data = sequence.stream()
                .mapToDouble(Integer::doubleValue)
                .toArray();

        // равномерное распределение на [minValue, maxValue]
        UniformRealDistribution uniform =
                new UniformRealDistribution(minValue, maxValue + 1); // +1 - чтобы maxValue включался

        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        double pValue = ks.kolmogorovSmirnovTest(uniform, data);

        return pValue > alpha;
    }


    /**
     * Тест на равномерность с использованием хи-квадрат.
     * @param bins Количество интервалов для гистограммы.
     * @param alpha Уровень значимости.
     * @return true, если последовательность проходит тест.
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

        // Для простоты используем приближенное критическое значение для 95% (alpha = 0.05)
        double criticalValue = bins - 1 + 2 * Math.sqrt(bins - 1); // Примерное значение
        return chiSquare < criticalValue;
    }

    /**
     * Тест на автокорреляцию для заданного лага.
     * @param lag Смещение для автокорреляции.
     * @return Значение автокорреляции (от -1 до 1).
     */
    public double autocorrelation(int lag) {
        double mean = sequence.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
        double variance = sequence.stream().mapToDouble(x -> Math.pow(x - mean, 2)).sum();
        double autocorrelation = 0;

        for (int i = 0; i < sequence.size() - lag; i++) {
            autocorrelation += (sequence.get(i) - mean) * (sequence.get(i + lag) - mean);
        }

        return autocorrelation / variance;
    }

    /**
     * Подсчет максимального количества последовательных повторений.
     * @return Максимальная длина цепочки повторяющихся чисел.
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

    /**
     * Получение исходной последовательности.
     * @return Список чисел.
     */
    public List<Integer> getSequence() {
        return List.copyOf(sequence);
    }
}