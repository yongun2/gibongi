# 14. 동시성 II: asyncio · 5h · 선행 05, 08, 13

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `async def` 는 무엇을 만드나? 이벤트 루프는 어떻게 스레드 하나로 수천 개 작업을 돌리나? 어떤 코드가 루프를 멈추나? 태스크는 왜 "사라질" 수 있나?

**README**
- 코루틴은 05장 generator 의 친척이다. `async def` 를 호출하면 실행되지 않고 코루틴 객체만 생긴다(await 하지 않으면 "coroutine … was never awaited" `RuntimeWarning`). `await` 는 양보 지점이다. dis 로 `RETURN_GENERATOR`/`SEND`/`YIELD_VALUE` 를 확인해 05장과 연결한다.
- 이벤트 루프는 ready 큐와 selector(macOS 는 kqueue)로 협력적 멀티태스킹을 한다. 한 번에 하나만 실행하므로 **await 하지 않는 CPU 루프나 블로킹 호출(`time.sleep`, 동기 HTTP 클라이언트)은 전체를 멈춘다.** 디버그 모드(`PYTHONASYNCIODEBUG=1`, `asyncio.run(debug=True)`)는 느린 콜백(기본 100ms)을 경고한다.
- Task: `create_task` 는 즉시 스케줄된다. **이벤트 루프는 태스크를 약참조로만 들고 있다.** 참조를 잡아 두지 않으면 실행 도중 GC 될 수 있다(공식 문서 경고). 그래서 `TaskGroup`(3.11, 구조적 동시성)을 쓴다. 하나가 실패하면 나머지가 취소되고 `ExceptionGroup` 이 올라온다. 3.15 `TaskGroup.cancel()` 은 예외 없이 조기 종료한다. `gather` 와의 차이(`return_exceptions`, 실패해도 나머지는 계속 실행)도 짚는다.
- 취소: `CancelledError` 는 `BaseException` 이라서 `except Exception` 이 잡지 않는 것이 의도된 동작이다. 잡았다면 다시 던진다. `asyncio.timeout()`(3.11)은 취소를 `TimeoutError` 로 바꾸는데, 그래서 `TimeoutError` 는 컨텍스트 매니저 **밖**에서만 잡힌다. `shield`.
- 동기 코드 연결: `asyncio.to_thread`(contextvars 전파), `run_in_executor`. asyncio 의 Lock/Queue/Semaphore 는 스레드용과 섞어 쓰지 않는다. `contextvars` 는 Java `ThreadLocal` 에 대응한다.
- **Java 대비**: Java 21 virtual thread 는 블로킹 코드를 그대로 두고 런타임이 스케줄한다. Python asyncio 는 "함수 색깔"이 있어서 async 가 호출 체인 전체로 전파된다. `CompletableFuture` 와 `Task` 를 비교해 본다.
- 버전:
  - 3.12: eager task factory
  - 3.14: `python -m asyncio ps PID`/`pstree PID` 로 실행 중인 프로세스의 태스크 트리를 본다. asyncio 성능 개선
  - 3.15: 이벤트 루프 정책 시스템이 deprecated 됐다(3.16 제거 예정). `asyncio.run(main(), loop_factory=…)` 를 쓴다
- 언제: 동시 I/O 가 많고 async 생태계(HTTP 클라이언트, DB 드라이버)가 갖춰졌으면 asyncio, 동기 라이브러리 위주면 스레드 풀, CPU 작업은 13장.

**Java 습관 함정**: async 함수 안에서 동기 I/O 를 호출한다, `create_task` 하고 결과를 버린다, `except Exception` 으로 취소를 삼킨다(또는 bare except), 스레드 Lock 을 async 코드에 쓴다.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_coroutine_is_object.py` | 호출만 하면 실행되지 않는다, never-awaited 경고, 05장 generator 와 같은 바이트코드 | `warnings` 캡처, `inspect.getcoroutinestate`, dis `SEND` 판정 |
| `ex02_event_loop_order.py` | 두 코루틴이 `await asyncio.sleep(0)` 에서 번갈아 실행된다 | 결정론적 실행 로그 |
| `ex03_blocking_the_loop.py` | `time.sleep` 과 `asyncio.sleep` 을 동시에 3개씩, `to_thread` 로 해결 | 소요 시간 판정(넉넉한 경계) |
| `ex04_unretrieved_exceptions.py` | 꺼내지 않은 태스크 예외("Task exception was never retrieved"), background set 패턴 | 로깅 캡처 |
| `ex05_taskgroup_cancel.py` | 하나가 실패하면 형제가 취소된다, `ExceptionGroup`, 3.15 `tg.cancel()`, `timeout` | 로그 |
| `ex06_swallowed_cancel.py` | bare except 로 `CancelledError` 를 삼키면 timeout 이 듣지 않는 버그 | 판정 |

| 실습 (`aio.py`) | 테스트가 확인하는 것 |
|---|---|
| `fetch_all(urls, fetch, limit)` (Semaphore + TaskGroup) | 결과 순서 보존, 가짜 fetch 로 잰 최대 동시 실행 수 ≤ limit, 전체 시간 상한, 실패하면 `ExceptionGroup` |
| `retry_with_timeout(fn, timeout, retries)` | timeout 이면 재시도, `CancelledError` 는 재시도하지 않고 전파 |
| `TokenBucket` (`loop.time` 주입) | 가짜 시계로 결정론적 허용/대기 |
| `async_pipeline(source, stage, workers)` (`asyncio.Queue`) | 정확히 한 번 처리, 종료 sentinel, 예외 전파 |

테스트에서는 pytest-asyncio 없이 테스트 함수 안에서 `asyncio.run()` 을 부른다. 외부 의존을 늘리지 않기 위해서다(§7).
