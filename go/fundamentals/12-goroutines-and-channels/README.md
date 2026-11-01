# 12. 고루틴·채널·스케줄러

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 10 11 · 4.5h · `.race` · 관련 변화: 타이머 GC·동기 채널(1.23) → `asynctimerchan` 제거(1.27), `WaitGroup.Go`·synctest GA·vet `waitgroup`(1.25), **goroutine leak 프로파일(1.26 실험 → 1.27 GA)**, `synctest.Sleep`(1.27)

**핵심 질문**

1. 고루틴은 OS 스레드, Java 플랫폼 스레드, Java 21 가상 스레드와 무엇이 같고 다른가?
2. 런타임 스케줄러(G·M·P)는 블로킹 시스템 콜, 네트워크 대기, 무한 루프를 어떻게 다루나?
3. 채널은 내부적으로 무엇이고(락 + 링 버퍼 + 대기 큐), 버퍼 크기는 의미론을 어떻게 바꾸나?
4. 고루틴은 언제 끝나나? 누가 끝내나? 누수는 어떻게 찾나?

**README**

- ① 내부 동작
  - **G·M·P**
    - G는 고루틴, M은 OS 스레드, P는 논리 프로세서(GOMAXPROCS개)다.
    - P마다 로컬 런큐(256)가 있고, 전역 큐와 work stealing이 있다.
    - 블로킹 시스템 콜이 걸리면 M이 P를 내놓고, 다른 M이 그 P로 계속 실행한다(handoff).
    - 네트워크 I/O는 netpoller(kqueue/epoll)가 맡아 고루틴만 파킹한다.
    - 1.14부터 신호 기반 비동기 선점이 있어 함수 호출 없는 루프도 선점된다.
  - **생성 비용**: 스택 수 KB + g 구조체뿐이라 수십만 개도 가능하다. Java 플랫폼 스레드는 스택을 약 1MB 예약하고 커널 스레드를 쓴다.
  - **고루틴에 없는 것**: ID, join, kill, interrupt가 없다. 종료는 협력적(채널 close, context)이고, 결과는 채널이나 WaitGroup으로 받는다.
  - **채널(hchan)**
    - 구성: mutex, 링 버퍼(`buf/sendx/recvx/qcount`), 대기 큐(`recvq/sendq`, sudog).
    - unbuffered 채널은 송신자가 대기 중인 수신자의 스택으로 **직접 복사**한다(랑데부). 이것이 happens-before를 만든다.
    - close: 대기자를 모두 깨운다. 이후 수신은 `(zero, false)`, 송신은 panic, 두 번 close도 panic이다.
    - nil 채널은 송수신이 영원히 막힌다. select에서 case를 끄는 데 쓴다.
  - **select**
    - 준비된 case가 여럿이면 무작위로 고른다(기아 방지).
    - `default`가 있으면 논블로킹이다.
    - `for { select { ... break } }`의 break는 select만 빠져나간다.
  - **타이머**
    - 1.23부터 멈추지 않은 타이머도 GC되고, 타이머 채널은 동기(cap 0)다.
    - 1.27에서 되돌리는 스위치(`asynctimerchan`)가 제거되어 항상 새 동작이다 **[RN 1.23, 1.27]**.
  - **데드락 감지**: 모든 고루틴이 막히면 `fatal error: all goroutines are asleep - deadlock!`이다. 일부만 막히면 감지하지 못한다 → 누수.
  - **누수 프로파일(1.27 GA)** **[RN 1.27, 블로그]**
    - GC 도달성을 이용해 "절대 깨어날 수 없는" 막힌 고루틴을 찾는다. `pprof.Lookup("goroutineleak")`, `/debug/pprof/goroutineleak`.
    - 한계: 전역이나 실행 중인 고루틴에서 닿는 동기화 객체, I/O 블록은 잡지 못한다.
    - **로컬 관찰**: 채널을 *인자로 넘긴* 송신자 4개는 탐지(total 4)했다. 같은 패턴을 *클로저 캡처*로 쓰면 탐지 0이었다 **[로컬 1.27.1, 원인 미확인]**.
    - 그래서 테스트에서는 `synctest`의 버블 종료 검사를 우선 쓴다. 이것은 클로저 버전도 잡았다 **[로컬]**.
  - **WaitGroup**: `WaitGroup.Go`(1.25). vet `waitgroup`은 고루틴 안에서 `Add`를 부르는 실수를 잡는다.
  - **synctest**: 가짜 시계를 쓰는 버블이다. 시간에 의존하는 동시성 코드를 결정적으로, 즉시 테스트한다.
- ② 차이

  | Java | Go |
  |---|---|
  | ExecutorService: 스레드가 비싸서 풀링 | 그냥 만든다. 풀은 "동시성 상한"(DB 커넥션, CPU) 때문에만 |
  | Java 21 가상 스레드: 같은 M:N 아이디어 | 모든 라이브러리가 처음부터 이 모델. `synchronized` 피닝 같은 역사 없음, 스레드 로컬 없음 |
  | `BlockingQueue` | buffered 채널 |
  | `SynchronousQueue` | unbuffered 채널 |
  | `CompletableFuture` | 고루틴 + 채널, errgroup(14) |
  | `CountDownLatch` | `WaitGroup` |
  | `Thread.join` | 없음 |
  | 스레드 예외는 그 스레드만 | 고루틴 panic은 프로세스 전체(07) |

- ③ 판단 기준
  - 고루틴을 시작하기 전에 "**언제, 누가 멈추게 하나**"에 답할 수 있어야 한다.
  - 채널: 소유권 이전, 작업 분배, 완료/취소 신호. 공유 상태 보호는 뮤텍스(13).
  - 버퍼 크기:
    - 0: 동기화
    - 1: 결과 하나나 신호. 조기 반환 시 누수를 막는다.
    - N: 알려진 상한이나 세마포어
    - "성능을 위한 큰 버퍼"는 문제를 숨긴다.
  - CPU 바운드 병렬 작업에서 GOMAXPROCS보다 많은 워커는 의미가 없다.
  - **OS 시뮬레이터는 단일 스레드 이산 시뮬레이션이 정답이다**(결정성). 동시성은 실무를 위해 배운다.
- 함정
  - 결과 채널이 unbuffered인데 조기 반환해서 송신자가 영구히 막힌다.
  - `wg.Add`를 고루틴 안에서 부른다.
  - 수신자가 close한다.
  - select 안의 break.
  - `time.Sleep`으로 동기화하는 테스트.
  - 고루틴을 무제한 생성한다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `goroutineCost` | 막힌 고루틴 10만 개의 `StackInuse/NumGoroutine` < 16KiB | 부등식 |
| `preemption` | `GOMAXPROCS(1)` + 무한 루프 고루틴이 있어도 main 진행 | 출력 |
| `unbufferedHandoff` | 랑데부의 순서 보장 | 출력 |
| `closedChannel` | 수신 `(0,false)`, range 종료, 송신 panic 메시지 | 출력 / recover |
| `nilChannelMerge` | 병합 중 닫힌 입력을 nil로 꺼서 바쁜 루프 방지 | 출력 |
| `selectRandom` | 1000번 중 양쪽 case가 모두 선택됨 | 불리언 |
| `deadlockFatal` | `all goroutines are asleep - deadlock!` | `observe.Run` |
| `goroutineLeakProfile` | 누수 버전 total 4 → 버퍼 1로 수정한 버전 0 → 클로저 버전 0(도구 한계) | `pprof.Lookup("goroutineleak")` 헤더 파싱 **[로컬]** |
| `synctestTimeout` | 5초 타임아웃이 가상 시간으로 즉시, 결정적으로 | 출력 |
| (관찰만) | `GODEBUG=schedtrace=1000`, `go tool trace` | README 명령 |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `ParallelMap(in, workers, f)` | 결과 순서를 유지하는 워커 풀 | 결과 순서, **동시 실행 최대치 ≤ workers**(atomic 카운터), 누수 없음(synctest), `-race` |
| `Merge(chans...)` | fan-in, 모든 입력이 닫히면 출력 close | 모든 값(Unordered), 출력 close, 누수 없음 |
| `FirstResult(fs...)` | 가장 먼저 끝난 결과 반환, 나머지 고루틴 누수 없음 | 결과, synctest 버블 종료 검사(`zz_leak_test.go`) |
