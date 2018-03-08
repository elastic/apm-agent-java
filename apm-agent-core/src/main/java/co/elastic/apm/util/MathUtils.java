package co.elastic.apm.util;

public class MathUtils {
    public static int getNextPowerOf2(int i) {
        return Math.max(2, Integer.highestOneBit(i - 1) << 1);
    }
}
