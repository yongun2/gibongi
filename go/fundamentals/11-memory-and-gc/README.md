# 11. 메모리 관리·GC·프로파일링

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 03 06 10 · 4.5h · 관련 변화: `AddCleanup`·`weak`(1.24), 컨테이너 인식 GOMAXPROCS(1.25), **Green Tea GC(1.25 실험 → 1.26 기본)**, 가변 make·append 스택 할당(1.25/1.26), size-specialized malloc(1.27)

Kotlin 쪽의 "Java 17→23 GC 변화" 섹션에 대응한다. 실무 기준으로 깊이를 정했다: 스택/힙 결정, GC 방식과 그것이 JVM과 다른 이유, 두 노브, 관찰과 할당 줄이기, 최근 버전 변화.

**핵심 질문**

1. 어떤 값이 스택에, 어떤 값이 힙에 가나? 지역 변수 주소를 반환해도 되는 이유는?
2. Go GC는 어떤 알고리즘이고, JVM GC(G1/ZGC)와 설계가 왜 다른가?
3. GOGC와 GOMEMLIMIT는 정확히 무엇을 조절하나? 컨테이너에서는 무엇을 설정하나?
4. 할당을 줄이는 실전 기법은 무엇이고 어떻게 측정하나?
5. 1.25~1.27에서 무엇이 바뀌었나?

**README**

- ① 내부 동작
  - **고루틴 스택**
    - 고루틴마다 연속 스택을 가진다. 최소 2KB이고, 1.19부터 시작 크기는 평균 사용량에 맞춰진다.
    - 모자라면 2배 크기의 새 스택으로 **복사**하고 포인터를 조정한다. 그래서 스택 변수의 주소가 바뀔 수 있다.
    - 스택 해제는 함수 반환이므로 공짜다.
  - **탈출 분석 규칙 총정리**(03·06·10에서 본 것)
    - 힙으로 가는 경우: 반환, 전역에 저장, 힙 객체에 저장, 인터페이스 박싱(대부분), 탈출하는 클로저의 캡처.
    - 크기에 따른 경우: 크기 불명 또는 상한 초과. 상한은 암묵 64KB/명시 128KB **[로컬 소스: `ir/cfg.go`]**.
    - `-m=2`로 이유 체인을 본다.
  - **할당기**
    - TCMalloc 계열이다. size class별로 나누고, P별 `mcache`로 락 없는 빠른 경로를 쓴다. span은 8KB 페이지 단위다.
    - 포인터 없는(noscan) 객체는 별도 span에 두어 GC가 스캔하지 않는다. 16B 미만 noscan 객체는 tiny allocator로 묶는다.
    - 1.27: 80B 미만 할당 일부에 크기 특화 할당 함수 → 최대 30% 저렴, 전체 약 1% **[RN 1.27]**.
  - **GC**
    - 동시(concurrent) 삼색 mark-sweep, **비이동·비세대**다.
    - STW는 짧은 두 구간뿐이다. 마킹 중에는 쓰기 장벽이 돌고, GC가 CPU 25%를 쓴다. 할당이 빠르면 mutator assist가 걸린다.
    - 비이동이라 내부 포인터와 cgo에 유리하다. 단편화는 size class로 완화한다(압축 없음).
  - **Green Tea** **[블로그, RN 1.25/1.26]**
    - 기존 방식은 객체 단위로 그래프를 쫓는다(포인터 추적 → 캐시 미스).
    - Green Tea는 **8KiB 페이지 단위**로 작업 큐에 넣고 페이지 안 객체를 묶어 스캔한다(지역성).
    - 1.25에서 실험, 1.26에서 기본. GC 오버헤드 10~40% 감소를 기대한다.
    - 1.26 노트는 opt-out(`GOEXPERIMENT=nogreenteagc`)을 1.27에서 없앨 예정이라고 했지만, **1.27.1에서 여전히 빌드된다** **[로컬]**.
  - **페이싱**
    - 목표 힙 = 라이브 힙 + (라이브 힙 + GC 루트) × GOGC/100. 최소 4MiB.
    - GOMEMLIMIT(1.19)는 **소프트 한도**다. 가까워지면 GOGC와 무관하게 GC를 자주 돈다. GC CPU 약 50% 상한으로 thrashing을 완화한다.
    - `GOGC=off` + GOMEMLIMIT 조합도 쓸 수 있다 **[GC 가이드]**.
  - **GOMAXPROCS**: 1.25부터 Linux cgroup CPU 한도를 반영하고 주기적으로 갱신한다 **[RN 1.25]**.
  - **종결자·약한 참조**: `SetFinalizer` 대신 `runtime.AddCleanup`(1.24)을 쓴다. `weak.Pointer`(1.24), `unique.Make`(1.23 인터닝).
  - **sync.Pool**: P별 캐시다. GC 때 victim 캐시로 옮겨졌다가 다음 GC에 해제된다. "임시 버퍼 재사용"용이지 커넥션 풀이 아니다.
  - **관찰**
    - `gctrace` 한 줄 읽는 법: `gc 3 @0.001s 7%: 0.007+0.056+0.002 ms clock, ... 18->18->18 MB, 18 MB goal, ... 10 P` **[로컬 출력]**.
    - `runtime/metrics`: `/gc/cycles/total:gc-cycles`, `/sched/goroutines`(1.26).
    - pprof heap: `alloc_space`와 `inuse_space` 구분.
    - CPU 프로파일에서 `mallocgc`가 15% 이상이거나 `gcAssistAlloc`이 5% 이상이면 신호다 **[GC 가이드]**.
- ② 차이

  | JVM | Go |
  |---|---|
  | 세대별(G1 기본, 21+ Generational ZGC) | 비세대 |
  | 이동·압축 | 비이동, 압축 없음 |
  | TLAB bump-pointer 할당(매우 빠름), 짧게 사는 객체는 young GC로 싸게 처리 | 값 타입과 탈출 분석으로 **힙 객체 수 자체를 줄인다**는 전제, 지연 시간 우선 |
  | `-Xmx`는 하드 한도(OOM) | GOMEMLIMIT는 소프트(넘으면 GC 폭증) |
  | 튜닝 플래그 수십 개 | GOGC, GOMEMLIMIT 두 개 |
  | finalizer 폐기 흐름, `WeakReference` | `AddCleanup`, `weak.Pointer` |

  그래서 Java식으로 객체 그래프를 잘게 쪼개면 Go에서 더 비싸다. Go의 1차 튜닝은 "할당 줄이기"다.

- ③ 판단 기준
  - 먼저 측정한다(`-benchmem`, pprof `alloc_space`). 핫 경로만 최적화한다.
  - 기법:
    - 사전 할당
    - append 스타일 API(`AppendX(dst, ...) []byte`)
    - 값 반환으로 탈출 방지
    - 박싱 회피
    - **포인터 없는 자료구조**(인덱스로 연결 → GC가 스캔 생략)
    - `Builder`
    - 측정 후의 `sync.Pool`
  - 컨테이너에서는 GOMEMLIMIT를 메모리 한도의 90~95%로 두고 GOGC는 기본값을 쓴다. 메모리를 다른 프로세스와 공유하는 환경이면 `GOGC=off`를 쓰지 않는다.
  - CLI와 배치는 대개 기본값으로 충분하다.
  - "GC 문제"를 의심하기 전에 누수부터 확인한다: 고루틴, 미축소 맵, 부분 슬라이스 보유.
- 함정
  - 생성자마다 `&T{}`.
  - `[]*T` 남용.
  - GOMEMLIMIT를 실제 한도에 바짝 붙여 thrashing.
  - `b.N` 루프에서 결과가 버려져 0ns가 나온다(`b.Loop` 사용).
  - 크기가 제각각인 버퍼를 Pool에 넣는다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `escapeCatalog` | 포인터 반환, 박싱, 클로저, 크기 불명, 64KB 초과 각각 한 줄 | `observe.Escapes` |
| `stackGrowth` | 깊은 재귀 전후 지역 변수 `uintptr` 주소 변화 → `moved: true` | 출력 |
| `gogcEffect` | 같은 할당 패턴의 GC 횟수: GOGC=400 < GOGC=100 < (GOMEMLIMIT=30MiB, GOGC=off) — **[로컬: 2 < 6 < 86]** | `observe.Run` + `gctrace` 줄 수, 부등식 출력 |
| `gctraceFields` | 한 줄을 파싱해 필드 의미 출력 | 출력 |
| `metrics` | `runtime/metrics`로 GC 횟수와 할당 바이트 | 출력 |
| `pointerFreeScan` | 1천만 원소 `[]int64` vs `[]*int64`의 `runtime.GC()` 시간 비율 > 5 | 부등식 (페이지 테이블 설계 근거) |
| `cleanupWeak` | `AddCleanup` 실행, `weak.Pointer`가 nil이 됨 | 출력 |
| `syncPoolGC` | Put 후 GC 두 번이면 비워짐 | 출력 |
| (관찰만) | `GOEXPERIMENT=nogreenteagc` 빌드와 GC CPU 비교(이진 트리 벤치), `go test -memprofile` → `pprof -sample_index=alloc_space -top` | README 명령 |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `AppendRecord(dst, r)` | append 스타일 인코더 | 바이트 결과, dst를 재사용하면 **할당 0** |
| `FastParse` | 주어진 느린 참조 구현(`reference.go`: `strings.Split+Sscanf`)과 같은 결과를 내는 무할당 파서 | 무작위 입력 1만 개에서 참조 구현과 일치, `AllocsPerRun == 0` |
| `FlatPageTable` | 1단계 페이지 테이블 2^20 엔트리를 `[]uint32`(frame+1, 0=invalid)로. 포인터 없음 | Map/Lookup/Unmap, `NewFlatPageTable` 할당 1회·4MiB 이하 (VM 시뮬레이터 직결) |
