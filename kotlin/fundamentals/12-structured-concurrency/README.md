# 12. 코루틴 2: 구조적 동시성·취소·예외·공유 상태 (6h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: 코루틴 하나가 실패하거나 취소되면 무엇이 같이 멈추나? 취소는 어떻게 전달되고 왜 협조적이어야 하나? 공유 상태는 어떻게 지키나?

**내부 동작**
- Job 트리: 부모는 자식 완료를 기다린다. 자식이 실패하면 부모가 취소되고 형제도 취소된다. `coroutineScope` vs `supervisorScope`/`SupervisorJob`. `launch` 예외는 전파되고(`CoroutineExceptionHandler`는 루트에서만 의미 있음), `async`는 `await`에서 던지지만 구조적으로는 부모도 취소된다.
- 취소: `CancellationException`, 중단 지점에서만 검사한다(`delay`, `yield`, `ensureActive`). CPU 루프는 검사하지 않으면 멈추지 않는다. `finally`에서 suspend하려면 `withContext(NonCancellable)`. `withTimeout`이 던지는 `TimeoutCancellationException`은 `CancellationException`의 하위 타입이다.
- 함정: `catch (e: Exception)`이나 `runCatching`이 취소를 삼키면 좀비 코루틴이 된다(07과 연결). `GlobalScope`, `async` 결과 무시, `runBlocking` 중첩.
- 공유 상태: `@Volatile`, `AtomicInteger`/`AtomicLong`, `synchronized`(블록 안에 중단 지점을 넣으면 컴파일 오류), `Mutex`(중단형이고 **재진입 불가**), `limitedParallelism(1)`로 스레드 한정, actor(Channel, 13). `ThreadLocal`은 코루틴과 맞지 않는다 → `asContextElement`.
- Java `StructuredTaskScope`(preview)와 개념 비교.

**예제**: E01 FailurePropagation(coroutineScope vs supervisorScope trace, `runTest`로 결정적, 골든) / E02 CooperativeCancellation(취소 후 CPU 루프 반복 횟수, 골든) / E03 SwallowedCancellation(`runCatching` 루프가 취소 후에도 계속 도는 것과 수정본, 골든) / E04 TimeoutSemantics(가상 시간, 골든) / E05 SharedCounter(100만 증가: plain var는 stderr에만, Atomic·Mutex·confined는 정확한 값으로 골든).

**실습**: X01 `fetchAll(ids)` — 부분 실패 허용(supervisor) + 항목별 타임아웃 + 전체 타임아웃. 테스트: 가상 시간, 결과 분류. / X02 `CancellableWorker` — 취소 시 `NonCancellable`로 정리. 테스트: 정리 순서, 자식 누수 없음(`job.children` 비어 있음). / X03 토큰 버킷 `RateLimiter`(Mutex + 가상 시간) — 테스트: 처리율과 공정성.
