# 11. 코루틴 1: suspend의 정체와 디스패처 (6h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: suspend 함수는 JVM에서 무엇이 되나? 코루틴은 플랫폼 스레드나 가상 스레드와 무엇이 다른가?

**내부 동작**
- CPS 변환: `suspend fun fetch(x: Int): Int` → `Object fetch(int, Continuation<? super Integer>)`(확인함). 반환값은 결과이거나 `COROUTINE_SUSPENDED` 마커다.
- 상태 머신: 중단 지점마다 `label`이 바뀌고, 지역 변수는 `ContinuationImpl` 하위 객체 필드로 spill된다. 함수마다 continuation 클래스 하나 → **실제로 중단되는 호출 경로는 continuation 객체를 할당한다**(14와 연결).
- `sequence {}` 빌더도 같은 메커니즘의 제한판이다(05와 연결).
- 빌더: `runBlocking`(스레드를 막음, main/테스트 전용), `launch`(Job), `async`(Deferred), `coroutineScope`, `withContext`.
- 디스패처: Default = CPU 코어 수(최소 2, 공식 문서), IO = `max(64, 코어 수)`이고 탄력적(elastic)이며 `limitedParallelism` 뷰는 IO 한도와 별개(공식 API 문서). 서버에는 `Main`이 없다.
- 블로킹 vs 중단: `Thread.sleep`은 스레드를 점유하고 `delay`는 타이머 등록 후 반환한다 → Default에서 블로킹 호출을 하면 스레드가 고갈된다.
- 가상 스레드(JDK 21) 비교: 암시적 중단(블로킹 I/O에서 자동 unmount) vs 명시적 `suspend`. JDK 24 JEP 491로 `synchronized` pinning이 해소됐다(네이티브 프레임과 클래스 초기화는 여전히 pinning). Java 구조적 동시성은 아직 preview다(JDK 25 JEP 505 5차, 26 JEP 525 6차, 27 JEP 533 7차).
- 디버깅: `-Dkotlinx.coroutines.debug`(스레드 이름에 코루틴 이름 추가, 공식 문서), stack trace recovery, `DebugProbes`.

**판단 기준**: Kotlin 코드베이스의 비동기는 코루틴이 기본이다(취소, 구조화, Flow 생태계). 블로킹 Java 라이브러리는 `withContext(Dispatchers.IO)` 또는 `runInterruptible`로 감싼다. 가상 스레드는 "기존 블로킹 Java 코드를 그대로 확장"할 때의 선택지로 비교해서 이해한다.

**예제**
| 파일 | 무엇을 | 관찰 |
|---|---|---|
| E01SuspendBytecode | suspend 함수 시그니처와 생성된 continuation 클래스(`label`, spill 필드) | `methodShapes` + 리플렉션 필드 목록 골든 |
| E02HandRolledCoroutine | `createCoroutine`/`suspendCoroutine`(stdlib만)으로 코루틴을 손으로 resume | 실행 순서 trace 골든 |
| E03ThreadsVsCoroutinesVsVirtual | 동시 작업 10만 개: 플랫폼 스레드(1만으로 제한), 가상 스레드, 코루틴 | stdout은 완료 개수(골든), 시간과 메모리는 stderr |
| E04BlockingStarvation | Default에서 `Thread.sleep` vs `delay` vs `withContext(IO)` | stdout 완료 여부, 처리 시간은 stderr |
| E05DebugNames | `-Dkotlinx.coroutines.debug`(`@run debug:`) | 스레드 이름 `main @coroutine#2` 골든(단일 스레드라 결정적) |

**실습**: X01 stdlib만으로 미니 스케줄러(`yield()`, 가상 시간 `sleep(ms)`) — 테스트: 인터리빙 순서와 가상 시각. / X02 `parallelMap(concurrency)`(`async` + `Semaphore`) — 테스트: `runTest` 가상 시간으로 최대 동시성과 총 소요 시간. / X03 블로킹 Java API를 suspend로 감싸기(`runInterruptible`) — 테스트: 취소 시 인터럽트 전달.
