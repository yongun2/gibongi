package s05.exercises

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class X01LazyPipeTest {
    private val words = "The quick brown fox jumps over the lazy dog".split(" ")

    @Test
    fun `filter-map-take 결과가 표준 Sequence 와 같다`() {
        val expected = words.asSequence().filter { it.length > 3 }.map { it.uppercase() }.take(3).toList()
        val actual = Pipe.from(words).filter { it.length > 3 }.map { it.uppercase() }.take(3).toList()
        assertEquals(expected, actual)
    }

    @Test
    fun `터미널 연산 전에는 람다가 한 번도 실행되지 않고, 실행되면 원소 단위(수직)로 흐른다`() {
        val log = mutableListOf<String>()
        val pipe = Pipe.from(words)
            .filter { log += "f:$it"; it.length > 3 }
            .map { log += "m:$it"; it.length }
            .take(2)
        assertTrue(log.isEmpty(), "파이프라인 구성만으로 실행됨: $log")

        assertEquals(listOf(5, 5), pipe.toList())
        assertEquals(listOf("f:The", "f:quick", "m:quick", "f:brown", "m:brown"), log)
    }

    @Test
    fun `take 는 필요한 만큼만 상류를 당긴다`() {
        val source = CountingIterable(1..1_000_000)
        val result = Pipe.from(source).map { it * 10 }.take(3).toList()
        assertEquals(listOf(10, 20, 30), result)
        assertEquals(3, source.pulled, "상류에서 꺼낸 원소 수")
    }

    @Test
    fun `무한 Pipe 도 take 로 끝낼 수 있다`() {
        val result = Pipe.generate(1L) { it * 2 }.filter { it > 100 }.take(3).toList()
        assertEquals(listOf(128L, 256L, 512L), result)
    }

    @Test
    fun `같은 Pipe 를 두 번 순회할 수 있다`() {
        val pipe = Pipe.from(listOf(1, 2, 3, 4)).filter { it % 2 == 0 }
        assertEquals(pipe.toList(), pipe.toList())
    }

    @Test
    fun `filter 의 hasNext 를 여러 번 불러도 원소를 건너뛰지 않는다`() {
        val it = Pipe.from(listOf(1, 2, 3, 4)).filter { it % 2 == 0 }.iterator()
        assertTrue(it.hasNext()); assertTrue(it.hasNext())
        assertEquals(2, it.next())
        assertEquals(4, it.next())
        assertTrue(!it.hasNext())
    }

    private class CountingIterable(private val range: IntRange) : Iterable<Int> {
        var pulled = 0
        override fun iterator(): Iterator<Int> = object : Iterator<Int> {
            val inner = range.iterator()
            override fun hasNext() = inner.hasNext()
            override fun next(): Int { pulled++; return inner.next() }
        }
    }
}
