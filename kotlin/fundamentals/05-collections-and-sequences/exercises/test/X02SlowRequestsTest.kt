package s05.exercises

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class X02SlowRequestsTest {

    @Test
    fun `정상 줄을 파싱한다`() {
        assertEquals(Request("GET", "/api/users", 200, 123), parseRequest("GET /api/users 200 123ms"))
    }

    @Test
    fun `형식이 틀린 줄은 null`() {
        listOf("", "GET /a 200", "GET /a OK 12ms", "GET /a 200 12", "GET /a 200 xms", "GET /a 200 12ms extra")
            .forEach { assertNull(parseRequest(it), "\"$it\" 는 null 이어야 함") }
    }

    @Test
    fun `느린 요청의 path 를 등장 순서대로 중복 없이 n개`() {
        val lines = sequenceOf(
            "GET /a 200 50ms",
            "GET /slow1 200 900ms",
            "garbage line",
            "POST /slow1 500 1200ms",
            "GET /b 200 10ms",
            "GET /slow2 200 501ms",
            "GET /slow3 200 700ms",
        )
        assertEquals(listOf("/slow1", "/slow2"), firstSlowPaths(lines, thresholdMs = 500, n = 2))
        assertEquals(listOf("/slow1", "/slow2", "/slow3"), firstSlowPaths(lines, thresholdMs = 500, n = 10))
    }

    @Test
    fun `답이 확정되면 더 읽지 않는다`() {
        var read = 0
        val lines = sequence {
            yield("GET /x 200 900ms")
            yield("GET /x 200 900ms")
            yield("GET /y 200 900ms")
            error("세 번째 줄까지면 충분한데 더 읽었다")
        }.onEach { read++ }

        assertEquals(listOf("/x", "/y"), firstSlowPaths(lines, thresholdMs = 500, n = 2))
        assertEquals(3, read)
    }
}
