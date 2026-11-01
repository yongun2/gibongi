package s05.examples

import gibongi.support.methodShapes
import gibongi.support.printShape

// 관찰 포인트: "짝수만 골라 제곱해 더하기"를 네 가지로 짰을 때 바이트코드가 무엇을 new 하고 무엇을 호출하나.
//   - Iterable.filter/map 은 inline 함수 → 람다 객체도, Function1.invoke 호출도 없다. 대신 ArrayList 를 new 한다.
//   - Sequence.filter/map 은 inline 이 아니다 → 람다가 invokedynamic(LambdaMetafactory)으로 객체가 되어 전달된다.
//   - Java Stream 도 람다 → invokedynamic. 원소는 Stream 내부 Sink 체인을 통과.
// 원본 바이트코드 전체는:  ./check kotlin 05 javap s05.examples.E02BytecodeShapeKt
//                          ./check kotlin 05 javap s05.examples.JavaPipelines

fun sumEvenSquaresLoop(xs: List<Int>): Long {
    var sum = 0L
    for (x in xs) if (x % 2 == 0) sum += x.toLong() * x
    return sum
}

fun sumEvenSquaresIterable(xs: List<Int>): Long =
    xs.filter { it % 2 == 0 }.map { it.toLong() * it }.sum()

fun sumEvenSquaresSequence(xs: List<Int>): Long =
    xs.asSequence().filter { it % 2 == 0 }.map { it.toLong() * it }.sum()

// Java Stream 을 Kotlin 람다로 호출하면? (Java 버전 sumEvenSquaresStream 과 비교)
fun sumEvenSquaresStreamFromKotlin(xs: List<Int>): Long =
    xs.stream().filter { it % 2 == 0 }.mapToLong { it.toLong() * it }.sum()

fun main() {
    val xs = (1..10).toList()
    check(setOf(
        sumEvenSquaresLoop(xs), sumEvenSquaresIterable(xs), sumEvenSquaresSequence(xs),
        sumEvenSquaresStreamFromKotlin(xs),
        JavaPipelines.sumEvenSquaresLoop(xs), JavaPipelines.sumEvenSquaresStream(xs),
    ).size == 1)

    println("== Kotlin (E02BytecodeShapeKt) ==")
    methodShapes(
        Class.forName("s05.examples.E02BytecodeShapeKt"),
        "sumEvenSquaresLoop", "sumEvenSquaresIterable", "sumEvenSquaresSequence", "sumEvenSquaresStreamFromKotlin",
    ).forEach(::printShape)

    println("== Java (JavaPipelines) ==")
    methodShapes(JavaPipelines::class.java, "sumEvenSquaresLoop", "sumEvenSquaresStream").forEach(::printShape)
}
