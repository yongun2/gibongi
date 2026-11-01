package s05.exercises

import gibongi.support.allocatedBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class X04ReadOnlyBoundaryTest {

    @Test
    fun `tracks 는 현재 곡 목록을 보여준다`() {
        val p = Playlist().apply { add("a"); add("b") }
        assertEquals(listOf("a", "b"), p.tracks)
    }

    @Test
    fun `MutableList 로 캐스팅해 수정해도 Playlist 는 바뀌지 않는다`() {
        val p = Playlist().apply { add("a"); add("b") }
        val exposed = p.tracks as MutableList<String>   // runCatching 밖에서 꺼낸다 (안에서 꺼내면 TODO()의 Error까지 삼킨다)
        runCatching { exposed.add("hacked") }
        runCatching { exposed[0] = "hacked" }
        assertEquals(listOf("a", "b"), p.tracks)
        assertEquals(2, p.size)
    }

    @Test
    fun `Java 컬렉션 API 로 수정해도 Playlist 는 바뀌지 않는다`() {
        val p = Playlist().apply { add("a") }
        val exposed = p.tracks as MutableCollection<String>
        runCatching { java.util.Collections.addAll(exposed, "x", "y") }
        assertEquals(1, p.size)
    }

    @Test
    fun `wordCounts - 처음 등장한 순서로 센다`() {
        val counts = wordCounts(listOf("kotlin", "jvm", "gc", "kotlin", "kotlin"))
        assertEquals(mapOf("kotlin" to 3, "jvm" to 1, "gc" to 1), counts)
        assertEquals(listOf("kotlin", "jvm", "gc"), counts.keys.toList())
    }

    @Test
    fun `wordCounts - 할당이 단어 종류 수에 비례한다`() {
        val kinds = List(10) { "word$it" }
        val words = List(400_000) { kinds[(it * 7) % 10] }   // 40만 단어, 10종류 → 단어당 4만 회
        wordCounts(words) // 워밍업
        val used = allocatedBytes { wordCounts(words) }
        assertTrue(used < 64 * 1024, "할당 ${used} B: 단어마다 무언가를 만들고 있다 (리스트? Integer 박싱?)")
    }
}
