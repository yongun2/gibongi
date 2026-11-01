package s05.examples

// 관찰 포인트
//  1) first{} 같은 short-circuit 터미널 연산: Iterable은 앞 단계를 전부 끝내고, Sequence는 찾는 즉시 멈춘다.
//  2) sorted() 같은 stateful 연산은 Sequence 안에서도 "전부 모아야" 다음으로 넘어간다 → 지연의 이점이 끊긴다.
//  3) 무한 시퀀스는 Sequence로만 표현 가능 (Iterable은 eager라 끝나지 않음).
//  4) 어떤 Sequence는 한 번만 순회할 수 있다 (iterator 기반, constrainOnce).

fun main() {
    println("== 1. first{} ==")
    var mapped = 0
    (1..1_000).map { mapped++; it * it }.first { it > 50 }
    println("  Iterable: map ${mapped}회")
    mapped = 0
    (1..1_000).asSequence().map { mapped++; it * it }.first { it > 50 }
    println("  Sequence: map ${mapped}회")

    println("== 2. 중간의 sorted() ==")
    val log = mutableListOf<String>()
    sequenceOf(3, 1, 2)
        .onEach { log += "before($it)" }
        .sorted()
        .onEach { log += "after($it)" }
        .first()
    println("  " + log.joinToString(" "))

    println("== 3. 무한 시퀀스 ==")
    val powers = generateSequence(1L) { it * 2 }
    println("  2의 거듭제곱 중 1000 초과 첫 5개: ${powers.filter { it > 1000 }.take(5).toList()}")

    println("== 4. 한 번만 순회 가능한 Sequence ==")
    val once = listOf("a", "b").iterator().asSequence()
    println("  첫 순회: ${once.toList()}")
    val second = runCatching { once.toList() }
    println("  두 번째 순회: ${second.exceptionOrNull()?.let { it::class.simpleName + ": " + it.message }}")
    val reusable = listOf("a", "b").asSequence()
    println("  List.asSequence()는 재순회 가능: ${reusable.toList()} ${reusable.toList()}")
}
