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
    fun filter(predicate: (T) -> Boolean): Pipe<T> = Pipe {
        val upstream = newIterator()
        object : Iterator<T> {
            private var ready = false
            private var item: T? = null
            override fun hasNext(): Boolean {
                while (!ready && upstream.hasNext()) {
                    val x = upstream.next()
                    if (predicate(x)) { item = x; ready = true }
                }
                return ready
            }
            override fun next(): T {
                if (!hasNext()) throw NoSuchElementException()
                ready = false
                @Suppress("UNCHECKED_CAST") return item as T
            }
        }
    }

    fun <R> map(transform: (T) -> R): Pipe<R> = Pipe {
        val upstream = newIterator()
        object : Iterator<R> {
            override fun hasNext() = upstream.hasNext()
            override fun next(): R = transform(upstream.next())
        }
    }

    /** n 개를 내보낸 뒤에는 상류(upstream) iterator 를 더 당기지 않는다. */
    fun take(n: Int): Pipe<T> = Pipe {
        val upstream = newIterator()
        object : Iterator<T> {
            private var left = n
            override fun hasNext() = left > 0 && upstream.hasNext()
            override fun next(): T {
                if (left <= 0) throw NoSuchElementException()
                left--
                return upstream.next()
            }
        }
    }

    fun toList(): List<T> = buildList { for (x in this@Pipe) add(x) }

    companion object {
        fun <T> from(source: Iterable<T>): Pipe<T> = Pipe { source.iterator() }

        /** seed, next(seed), next(next(seed)), ... 로 끝없이 이어지는 Pipe. */
        fun <T : Any> generate(seed: T, next: (T) -> T): Pipe<T> = Pipe {
            object : Iterator<T> {
                private var current: T? = null
                override fun hasNext() = true
                override fun next(): T = (current?.let(next) ?: seed).also { current = it }
            }
        }
    }
}
