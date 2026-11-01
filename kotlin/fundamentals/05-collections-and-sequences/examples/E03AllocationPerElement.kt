// @run semantic: -XX:-DoEscapeAnalysis -Xmx1g
// @run compact: -XX:-DoEscapeAnalysis -Xmx1g -XX:+UseCompactObjectHeaders
package s05.examples

import gibongi.support.allocatedBytes
import gibongi.support.blackhole
import java.util.Arrays
import java.util.function.ToLongFunction
import kotlin.system.measureNanoTime

// 관찰 포인트: 같은 계산(0..N-1 중 짝수의 제곱합)을 할 때 원소 하나당 힙에 몇 바이트를 만드나.
//   stdout: 원소당 할당 바이트 (결정적 → 골든 비교)
//   stderr: 걸린 시간 (환경마다 다름 → 표시만)
// -XX:-DoEscapeAnalysis : JIT의 스칼라 치환(할당 제거)을 꺼서 "코드가 의미상 만드는 객체"를 본다.
//   이 옵션을 지우고 ./check 를 돌려 JIT이 무엇을 지워 주는지(또는 못 지우는지)도 비교해 보라.
// "Stream<Integer> (Kotlin 람다)" 가 왜 0이 아닌지는 E02의 sumEvenSquaresStreamFromKotlin 바이트코드를 보라.
// compact 실행: JDK 25의 Compact Object Headers(JEP 519)로 객체 헤더가 12→8바이트가 되면 박싱 비용이 어떻게 바뀌나.

private const val N = 1_000_000

private val strategies: List<Pair<String, (IntArray, List<Int>) -> Long>> = listOf(
    "for 루프 (IntArray)" to { arr, _ ->
        var s = 0L
        for (x in arr) if (x % 2 == 0) s += x.toLong() * x
        s
    },
    "for 루프 (List<Int>)" to { _, list ->
        var s = 0L
        for (x in list) if (x % 2 == 0) s += x.toLong() * x
        s
    },
    "Iterable filter→map→sum" to { _, list ->
        list.filter { it % 2 == 0 }.map { it.toLong() * it }.sum()
    },
    "Sequence filter→map→sum" to { _, list ->
        list.asSequence().filter { it % 2 == 0 }.map { it.toLong() * it }.sum()
    },
    "IntArray.filter→map→sum" to { arr, _ ->
        arr.filter { it % 2 == 0 }.map { it.toLong() * it }.sum()   // IntArray.filter 는 List<Int> 를 돌려준다!
    },
    "IntArray.sumOf (inline)" to { arr, _ ->
        arr.sumOf { if (it % 2 == 0) it.toLong() * it else 0L }
    },
    "Stream<Integer> (Kotlin 람다)" to { _, list ->
        list.stream().filter { it % 2 == 0 }.mapToLong { it.toLong() * it }.sum()
    },
    "Stream<Integer> (SAM 생성자)" to { _, list ->
        list.stream().filter { it % 2 == 0 }.mapToLong(ToLongFunction { it.toLong() * it }).sum()
    },
    "Stream<Integer> (Java 코드)" to { _, list ->
        JavaPipelines.sumEvenSquaresStream(list)
    },
    "Java IntStream" to { arr, _ ->
        Arrays.stream(arr).filter { it % 2 == 0 }.asLongStream().map { it * it }.sum()
    },
)

fun main() {
    val arr = IntArray(N) { it }
    val list = arr.toList()
    val expected = strategies.first().second(arr, list)

    // 워밍업: 람다 call site 링크, 클래스 로딩 같은 1회성 할당을 측정에서 뺀다.
    val small = IntArray(1_000) { it }
    val smallList = small.toList()
    repeat(3) { strategies.forEach { (_, f) -> blackhole = f(small, smallList) } }

    println("원소당 할당 바이트 (N = $N)")
    for ((name, f) in strategies) {
        var result = 0L
        val bytes = allocatedBytes { result = f(arr, list) }
        check(result == expected) { "$name 결과가 다름" }
        val nanos = measureNanoTime { blackhole = f(arr, list) }
        println("  %-30s %6.1f B".format(name, bytes.toDouble() / N))
        System.err.println("%-30s %6d ms".format(name, nanos / 1_000_000))
    }
}
