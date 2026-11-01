# 13. 공유 메모리 동기화와 메모리 모델

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 12 · 4h · `.race` · 관련 변화: 메모리 모델 개정·atomic 타입(1.19), `OnceFunc/OnceValue`(1.21), `sync.Map` 해시 트라이(1.24), `WaitGroup.Go`(1.25)

**핵심 질문**

1. 데이터 레이스는 Go에서 왜 "틀린 값" 수준을 넘어 메모리 안전성까지 깨나? (Java와의 결정적 차이)
2. Go 메모리 모델의 happens-before는 어떤 연산들로 만들어지나?
3. Mutex는 왜 재진입이 안 되며, RWMutex·`sync.Map`·atomic은 언제 이득인가?
4. sync 타입을 복사하면 무엇이 깨지나?

**README**

- ① 내부 동작
  - **메모리 모델(2022 개정)**
    - DRF-SC: 레이스 없는 프로그램은 순차 일관적으로 동작한다.
    - happens-before를 만드는 연산: 채널 송수신·close, Mutex Unlock→Lock, `Once`, `WaitGroup`, atomic(모두 순차 일관, Java `volatile`급), 고루틴 시작.
  - **레이스가 나면**
    - 한 워드짜리 값은 "어느 쓰기의 값 하나"를 본다.
    - **여러 워드짜리 값**(인터페이스, 슬라이스, 문자열)은 찢어진 값을 볼 수 있다. 타입 워드와 데이터 워드가 어긋나면 임의 메모리 접근이 가능해진다.
    - Java는 레이스가 있어도 타입 안전을 보장한다.
  - **레이스 디텍터**
    - ThreadSanitizer 기반이고, **실행된 경로만** 탐지한다.
    - 메모리 5~10배, 속도 2~20배 비용이 든다 **[race detector 문서]**. 테스트와 스테이징에서는 상시 켠다.
  - **Mutex**
    - 빠른 경로는 CAS, 경합하면 스핀 후 세마포어 대기다.
    - 1ms 넘게 기다린 대기자가 있으면 기아 모드(FIFO 핸드오프)로 바뀐다 **[로컬 소스: `internal/sync/mutex.go` `starvationThresholdNs = 1e6`]**.
    - **재진입이 안 된다**: 소유자 개념이 없어 다른 고루틴이 Unlock할 수도 있다. `TryLock`이 있다.
  - **RWMutex**: 쓰기 대기자가 있으면 새 읽기를 막는다(쓰기 우선). 그래서 RLock을 재귀로 잡으면 데드락이 날 수 있다.
  - **그 밖의 기본형**: `Once/OnceFunc/OnceValue(s)`, `WaitGroup`, `Cond`(드묾).
  - **atomic 타입**(`atomic.Int64/Bool/Pointer[T]`, 1.19): 정렬을 보장하고 값 복사를 막는다. arm64 원자 명령으로 컴파일된다(`-S`로 확인, 정확한 명령은 구현 시 확정 **[미확인]**).
  - **sync.Map**
    - 1.24부터 해시 트라이 구현이다 **[RN 1.24]**.
    - 키를 한 번 쓰고 많이 읽거나, 고루틴마다 키 집합이 분리된 경우에 쓴다. 일반적인 경우는 Mutex + map이 낫다.
  - **복사 금지**: 첫 사용 후 복사하면 상태가 둘로 갈라진다. vet `copylocks`가 잡는다. 값 리시버가 Mutex를 복사하는 함정(02)도 여기 해당한다.
  - **false sharing**: 서로 다른 고루틴이 같은 캐시 라인의 다른 변수를 갱신하면 느려진다. 패딩으로 해결하고 벤치로 확인한다.
- ② 차이

  | Java | Go |
  |---|---|
  | `synchronized`: 재진입, 객체 헤더의 모니터 | `sync.Mutex`: 별도 값, 재진입 불가 → 재귀 구조를 그대로 옮기면 데드락 |
  | `volatile` | 키워드 없음 → atomic 타입. 일반 변수로 플래그를 폴링하면 레이스이고, 컴파일러가 루프 밖으로 읽기를 끌어낼 수 있다 |
  | `ConcurrentHashMap` | Mutex + map, 특정 패턴에서 `sync.Map` |
  | `AtomicLong` | `atomic.Int64` |
  | `ReentrantReadWriteLock` | `RWMutex` (재진입 불가, 쓰기 우선) |
  | `java.util.concurrent`의 풍부한 도구 | 채널 + 몇 개의 sync 기본형을 조합 |

- ③ 판단 기준
  - 공유 상태 보호는 Mutex(짧은 임계 구역 + `defer Unlock`). 상태 기계가 복잡하면 상태를 한 고루틴이 소유하고 채널로 통신한다.
  - RWMutex는 읽기가 압도적이고 임계 구역이 길 때만, 측정 후에 쓴다.
  - 카운터, 플래그, 포인터 교체는 atomic.
  - 지연 초기화는 `sync.OnceValue`.
  - Mutex가 든 구조체는 포인터 리시버만 쓰고 복사하지 않는다. Mutex는 보호하는 필드 묶음 위에 둔다(관례).
- 함정
  - Mutex 재진입 데드락, RLock 재귀.
  - Unlock이 빠지는 경로(`defer`로 막는다).
  - Mutex를 값으로 복사한다.
  - atomic 접근과 일반 접근을 섞는다.
  - `sync.Map`을 남용한다.
  - "레이스 디텍터가 조용하다 = 레이스가 없다"라고 착각한다(실행되지 않은 경로는 모른다).

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `raceDetected` | `testdata/racy`를 `-race`로 실행 → `WARNING: DATA RACE` 포함, 종료 코드 66 | `observe.Run` |
| `lostUpdates` | 비원자 증가 1000 고루틴 → 기대값보다 작음 vs atomic/Mutex는 정확 | 부등식 |
| `mutexNotReentrant` | Lock 후 `TryLock()` → false | 출력 |
| `copylocksVet` | `passes lock by value` | `observe.Vet` |
| `onceValue` | 동시 100호출에 초기화 1회 | 출력 |
| `atomicAsm` | atomic 연산이 원자 명령으로 컴파일됨 | `observe.Diag(-S)` 필터 |
| `Benchmark*` | Mutex / RWMutex(읽기 99%) / atomic / sync.Map, false sharing 패딩 유무 | `--bench` |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `SafeCounter[K]` | `Inc/Get/Snapshot`(복사본 반환) | 100 고루틴 × 1000 증가가 정확, Snapshot 수정이 내부에 영향 없음, `-race` |
| `LazyConfig` | `OnceValues`로 (값, 에러) 1회 로딩 | 동시 100호출에 로더 1회, 에러도 캐시 |
| `Bank.Transfer` | 계좌별 Mutex + **락 순서**로 데드락 회피 | 양방향 동시 이체 1만 회가 시간 제한 안에 끝남, 총액 보존, `-race` |
