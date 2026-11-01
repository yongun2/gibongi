package s05.examples;

import java.util.List;

/** E02와 나란히 javap 로 비교하기 위한 Java 버전. */
public final class JavaPipelines {
    private JavaPipelines() {}

    public static long sumEvenSquaresLoop(List<Integer> xs) {
        long sum = 0;
        for (int x : xs) if (x % 2 == 0) sum += (long) x * x;
        return sum;
    }

    public static long sumEvenSquaresStream(List<Integer> xs) {
        return xs.stream().filter(x -> x % 2 == 0).mapToLong(x -> (long) x * x).sum();
    }
}
