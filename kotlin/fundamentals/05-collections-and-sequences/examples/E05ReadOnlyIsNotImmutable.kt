package s05.examples

// 관찰 포인트: Kotlin의 List는 "읽기 전용 인터페이스"이지 불변 컬렉션이 아니다.
//   - 런타임에는 전부 java.util.* 구현체이고, kotlin.collections.List 는 컴파일 타임에만 존재하는 타입.
//   - 그래서 캐스팅이나 Java 코드를 거치면 바뀔 수 있다.

fun main() {
    println("== 1. 팩토리 함수가 실제로 만드는 클래스 ==")
    listOf(
        "emptyList()" to emptyList<Int>(),
        "listOf(1)" to listOf(1),
        "listOf(1, 2, 3)" to listOf(1, 2, 3),
        "mutableListOf(1)" to mutableListOf(1),
        "List(3) { it }" to List(3) { it },
        "buildList { add(1) }" to buildList { add(1) },
        "(1..3).toList()" to (1..3).toList(),
        "listOf(1,2).map { it }" to listOf(1, 2).map { it },
    ).forEach { (expr, list) -> println("  %-24s → %s".format(expr, list.javaClass.name)) }

    println("== 2. List 로 받았는데 바뀐다 ==")
    val fixed: List<Int> = listOf(1, 2, 3)
    (fixed as MutableList<Int>)[0] = 100                       // Arrays$ArrayList 는 set 을 지원한다
    println("  listOf 결과에 set: $fixed")
    println("  add 는? " + runCatching { fixed.add(4) }.exceptionOrNull()?.javaClass?.simpleName)

    val backing = mutableListOf("a")
    val view: List<String> = backing                          // 같은 객체를 읽기 전용 타입으로 본 것뿐
    backing += "b"
    println("  view 는 원본의 변경을 그대로 본다: $view")

    println("== 3. Java 코드는 Kotlin의 읽기 전용 타입을 모른다 ==")
    val names: List<String> = mutableListOf("kim")
    JavaMutator.addTo(names, "lee")                           // Java 쪽 시그니처는 java.util.List<String>
    println("  Java 호출 후: $names")

    println("== 4. 방어적 복사 vs 읽기 전용 뷰 ==")
    val source = mutableListOf(1, 2)
    val copy = source.toList()
    val unmodifiable = java.util.Collections.unmodifiableList(source)
    source += 3
    println("  toList() 복사본: $copy, unmodifiableList 뷰: $unmodifiable")
}
