package s05.exercises

/**
 * X01. kotlin.sequences 를 쓰지 않고 지연(lazy) 파이프라인을 직접 만든다.
 *
 * 규칙
 *  - filter/map/take 는 "새 Pipe"를 돌려줄 뿐, 원소를 하나도 건드리지 않는다.
 *  - 원소는 터미널 연산(toList)이 iterator 를 당길 때 한 개씩 파이프라인 끝까지 흐른다.
 *  - 터미널 연산마다 새 iterator 체인을 만들므로 같은 Pipe 를 여러 번 순회할 수 있다.
 */
class Pipe<T> private constructor(private val newIterator: () -> Iterator<T>) {

    operator fun iterator(): Iterator<T> = newIterator()

    /** predicate 를 통과한 원소만. hasNext() 를 여러 번 불러도 원소를 건너뛰면 안 된다. */
    fun filter(predicate: (T) -> Boolean): Pipe<T> = TODO("X01-1")

    fun <R> map(transform: (T) -> R): Pipe<R> = TODO("X01-2")

    /** n 개를 내보낸 뒤에는 상류(upstream) iterator 를 더 당기지 않는다. */
    fun take(n: Int): Pipe<T> = TODO("X01-3")

    fun toList(): List<T> = TODO("X01-4")

    companion object {
        fun <T> from(source: Iterable<T>): Pipe<T> = Pipe { source.iterator() }

        /** seed, next(seed), next(next(seed)), ... 로 끝없이 이어지는 Pipe. */
        fun <T : Any> generate(seed: T, next: (T) -> T): Pipe<T> = TODO("X01-5")
    }
}
