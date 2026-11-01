package s05.examples

import java.util.stream.Collectors

// 관찰 포인트: 같은 filter → map → take(4) 파이프라인이 "어떤 순서로, 몇 번" 람다를 부르는가.
//   Iterable : 단계(step)마다 컬렉션 전체를 처리하고 중간 List를 만든다 → 수평(horizontal) 처리
//   Sequence : 원소 하나가 파이프라인 끝까지 내려간 뒤 다음 원소 → 수직(vertical) 처리, 필요한 만큼만
//   Stream   : Java Stream도 Sequence와 같은 수직 처리 + short-circuit

private val words = "The quick brown fox jumps over the lazy dog".split(" ")

fun main() {
    println("== 1. Iterable: filter 9번이 전부 끝난 뒤에야 map이 시작된다 ==")
    val t1 = Trace()
    val r1 = words
        .filter { t1.log("f", it); it.length > 3 }
        .map { t1.log("m", it); it.length }
        .take(4)
    t1.print(r1)

    println("== 2. Sequence: 터미널 연산 전에는 아무것도 실행되지 않는다 ==")
    val t2 = Trace()
    val pipeline = words.asSequence()
        .filter { t2.log("f", it); it.length > 3 }
        .map { t2.log("m", it); it.length }
        .take(4)
    println("  toList() 호출 전 람다 호출 수: ${t2.size}")
    println("  pipeline 객체: ${pipeline::class.simpleName}")
    val r2 = pipeline.toList()
    t2.print(r2)

    println("== 3. Java Stream: Sequence와 같은 순서, limit에서 멈춘다 ==")
    val t3 = Trace()
    val r3 = words.stream()
        .filter { t3.log("f", it); it.length > 3 }
        .map { t3.log("m", it); it.length }
        .limit(4)
        .collect(Collectors.toList())
    t3.print(r3)
}

private class Trace {
    private val events = mutableListOf<String>()
    val size get() = events.size
    fun log(step: String, word: String) { events += "$step($word)" }
    fun print(result: List<Int>) {
        events.chunked(7).forEach { println("  " + it.joinToString(" ")) }
        println("  filter ${events.count { it.startsWith("f(") }}회, map ${events.count { it.startsWith("m(") }}회 → $result")
    }
}
