package s05.exercises

data class Request(val method: String, val path: String, val status: Int, val latencyMs: Int)

/**
 * X02-1. "GET /api/users 200 123ms" 한 줄을 파싱한다. 형식이 틀리면 예외 대신 null.
 * (토큰 4개, status 는 정수, latency 는 정수 + "ms" 접미사)
 */
fun parseRequest(line: String): Request? = TODO("X02-1")

/**
 * X02-2. latency 가 thresholdMs 를 "초과"한 요청의 path 를 처음 등장한 순서대로, 중복 없이, 최대 n개.
 * lines 는 수 GB 짜리 로그일 수 있다: 답을 확정하는 데 필요한 줄까지만 읽어야 한다.
 * 형식이 틀린 줄은 건너뛴다.
 */
fun firstSlowPaths(lines: Sequence<String>, thresholdMs: Int, n: Int): List<String> = TODO("X02-2")
