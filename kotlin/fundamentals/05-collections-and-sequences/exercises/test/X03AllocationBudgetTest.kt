package s05.exercises

import gibongi.support.allocatedBytes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class X03AllocationBudgetTest {
    private val xs = Random(42).let { r -> IntArray(1_000_000) { r.nextInt(0, 1_000) } }

    private fun assertBudget(budget: Long, block: () -> Unit) {
        val used = allocatedBytes(block)
        assertTrue(used <= budget, "할당 예산 초과: ${used} B > ${budget} B (박싱이나 중간 컬렉션이 생기지 않았나?)")
    }

    @Test
    fun `sumOfEvenSquares - 정답`() {
        assertEquals(4L + 16 + 36, sumOfEvenSquares(intArrayOf(1, 2, 3, 4, 5, 6)))
        assertEquals(xs.filter { it % 2 == 0 }.sumOf { it.toLong() * it }, sumOfEvenSquares(xs))
    }

    @Test
    fun `sumOfEvenSquares - 할당 예산 1KiB`() = assertBudget(1024) { sumOfEvenSquares(xs) }

    @Test
    fun `histogram - 정답과 예산`() {
        val expected = IntArray(1_000).also { h -> xs.forEach { h[it]++ } }
        var actual = IntArray(0)
        assertBudget(16 + 4L * 1_000 + 1024) { actual = histogram(xs, 1_000) }
        assertEquals(expected.toList(), actual.toList())
    }

    @Test
    fun `countDistinct - 정답과 예산`() {
        assertEquals(3, countDistinct(intArrayOf(5, 1, 5, 3, 1), bound = 10))
        var actual = 0
        assertBudget(1_000L + 16 + 1024) { actual = countDistinct(xs, 1_000) }
        assertEquals(xs.toSet().size, actual)
    }
}
