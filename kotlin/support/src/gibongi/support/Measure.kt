package gibongi.support

import java.lang.management.ManagementFactory

private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

/**
 * 현재 스레드가 block 실행 중 힙에 할당한 바이트 수.
 * HotSpot은 스레드별 TLAB 사용량을 누적하므로 GC와 무관하게 "얼마나 만들었나"를 잴 수 있다.
 * inline 이라 측정용 람다 객체 자체는 할당되지 않는다.
 */
inline fun allocatedBytes(block: () -> Unit): Long {
    val before = currentThreadAllocatedBytes()
    block()
    return currentThreadAllocatedBytes() - before
}

fun currentThreadAllocatedBytes(): Long = threads.currentThreadAllocatedBytes

/** 결과를 버리지 않게 하는 싱크 (JIT이 계산 전체를 지우는 것 방지). */
@Volatile var blackhole: Any? = null
