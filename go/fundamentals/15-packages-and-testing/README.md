# 15. 패키지·모듈·프로젝트 구성과 테스트 실전

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 08 12 (사실상 전부) · 4h · 관련 변화: toolchain 줄(1.21), `tool` 지시어·vet `tests`·`B.Loop`(1.24), go.mod `ignore`(1.25), `go mod init` 기본 go 줄 변경·`go fix` modernizers·`T.ArtifactDir`(1.26), `go test`가 `stdversion` vet 실행(1.27)

프로젝트 착수 직전 단계다. "실제 코드베이스에서 바로 필요한 것"과 OS 시뮬레이터에 필요한 테스트 구조(testable main, golden, 결정적 난수)를 묶었다.

**핵심 질문**

1. 모듈·패키지·디렉터리·import 경로는 어떤 관계이고, go.mod의 `go` 줄은 무엇을 결정하나?
2. 의존성 버전은 어떻게 고르나(MVS)? Maven과 무엇이 다른가?
3. 실무 Go 저장소의 디렉터리 관례는? (`cmd/`, `internal/`, 패키지 이름)
4. 테이블 테스트, 서브테스트, golden, fuzz, bench, synctest, coverage를 각각 언제 쓰나?
5. 시뮬레이터 같은 CLI를 테스트 가능하게 만드는 구조는?

**README**

- ① 내부 동작
  - **모듈·패키지·import 경로**
    - 모듈은 go.mod가 있는 디렉터리 트리이고 버전의 단위다.
    - 패키지는 디렉터리 하나이고 컴파일 단위다.
    - import 경로 = 모듈 경로 + 상대 디렉터리.
  - **`go` 줄**
    - 언어 기능을 켜고 끄는 게이트다: 루프 변수(1.22), 타이머(1.23), 제네릭 메서드(1.27).
    - 최소 툴체인 요구이기도 하다(`toolchain` 줄, `GOTOOLCHAIN` 자동 전환).
    - GODEBUG 기본값도 `go` 줄에 따라 정해진다. 1.27에서 일부 GODEBUG가 제거되어 옛 값을 쓰면 빌드가 실패한다 **[RN 1.27]**.
  - **MVS**
    - 요구된 최소 버전들 중 최댓값을 결정적으로 고른다. 최신 버전으로 자동 업그레이드하지 않는다.
    - `go.sum`은 해시 검증용이다. 메이저 v2+는 경로에 `/v2`를 붙인다.
  - **가시성**
    - 대문자 이름과 `internal/`(부모 트리만 import 가능).
    - **순환 import 금지** → 패키지 경계가 곧 의존 방향이다.
  - **초기화**: 패키지 변수(의존 순서) → `init()`(파일 이름 순) → `main`.
  - **빌드 규칙**
    - `//go:build` 태그, `_test.go`.
    - `testdata/`와 `_`·`.`으로 시작하는 디렉터리는 무시된다.
    - `go:embed`.
    - `tool` 지시어(`go get -tool .../stringer` → `go tool stringer`).
  - **테스트 러너**
    - 패키지마다 테스트 바이너리를 만들고, 패키지 단위로 병렬 실행한다. `t.Parallel`은 패키지 안에서의 병렬이다.
    - 결과를 캐시한다(`-count=1`로 무효화).
    - 옵션: `-run/-skip/-shuffle/-cover/-json`.
    - vet 일부가 자동으로 돈다(`printf`, `tests`, 1.27의 `stdversion` 등).
  - **벤치**: `b.Loop()`(1.24)는 셋업을 1회만 하고 결과를 살려 둬 데드 코드 제거를 막는다. 비교는 `benchstat`으로 한다.
  - **퍼징**: `go test -fuzz`, 시드 코퍼스 `f.Add`. 실패한 입력은 `testdata/fuzz/`에 저장되어 회귀 테스트가 된다.
  - **테스트 헬퍼**: golden 파일 + `-update` 플래그 관례, `t.TempDir/Setenv/Cleanup/Helper/Context`, `T.ArtifactDir`(1.26).
  - **결정적 난수**: `rand.New(rand.NewPCG(s1, s2))`(math/rand/v2). 전역 함수는 자동 시드라 재현할 수 없다.
  - **CLI 구조**
    - `func run(args []string, stdin io.Reader, stdout, stderr io.Writer) int`를 두고 `main`은 `os.Exit(run(...))`만 한다.
    - **`os.Exit`는 defer를 실행하지 않는다** → bufio 출력이 사라진다.
  - **로깅**: `log/slog`(1.21), `slog.NewMultiHandler`(1.26).
- ② 차이

  | Java | Go |
  |---|---|
  | Maven/Gradle: 빌드 스크립트, 플러그인, 프로필, 의존성 scope | `go` 명령 하나, 설정이 거의 없음, 모듈 프록시 + 체크섬 DB |
  | Maven nearest-wins / Gradle 최신 우선 | MVS |
  | 패키지 = 네임스페이스 계층, package-private | 패키지 = 디렉터리 하나. **중첩은 의미가 없다**(서브패키지에 특권 없음, internal 제외) |
  | JUnit 애너테이션/라이프사이클, Mockito | 함수 + 테이블 + 작은 인터페이스 대역(06) |
  | JMH | `go test -bench` + `benchstat` |
  | static 초기화 블록 | `init` (남용 주의) |

- ③ 판단 기준
  - **레이아웃**
    - go.dev의 공식 "Organizing a Go module" 문서를 따른다.
    - 작은 프로젝트는 루트 패키지 + `cmd/<name>/main.go`, 공유하지 않을 코드는 `internal/`.
    - `util`, `common`, `models`, `pkg` 같은 이름을 피한다. `sched.Policy`처럼 "패키지명.타입명"이 읽히게 짓는다.
  - **테스트 도구 선택**

    | 대상 | 도구 |
    |---|---|
    | 동작 | 테이블 테스트 |
    | 출력이 큼 | golden |
    | 파서 | fuzz |
    | 시간 | synctest |
    | 동시성 | `-race` |
    | 성능 주장 | 벤치 + benchstat |

  - 외부 의존성은 표준 라이브러리로 부족할 때만 들인다.
- 함정
  - `go` 줄이 낮아 새 의미론이 꺼져 있다.
  - `init`에서 I/O를 한다.
  - 변수 이름이 패키지 이름을 가린다.
  - 순환 import를 "interfaces 패키지"로 땜질한다.
  - 테스트 캐시 때문에 통과했다고 착각한다.
  - `os.Exit` 때문에 출력이 사라진다.
  - 전역 rand로 시뮬레이션을 돌려 재현이 안 된다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `initOrder` | 같은 패키지 여러 파일의 변수 초기화 → `init` 순서 | 출력 |
| `osExitSkipsDefer` | defer 출력 없음, bufio 미플러시 손실 | `observe.Run` |
| `seededRand` | 같은 시드 → 같은 수열(PCG), 전역 함수는 매번 다름 | 출력 |
| `goLineGates` | 10장 loopvar 예제를 go 줄만 바꿔 재실행 | `observe.Run` |
| (관찰만) | `go list -deps`, `go mod graph`, `go version -m app`, `go test -cover`, `./check go 15 --fuzz` | README 명령 |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `tracestat` CLI | `Run(args, stdin, stdout, stderr) int`: `-top N`, `-pages`, 잘못된 플래그는 2, 입력 에러는 1 | 테이블 + **golden 파일**(`testdata/*.golden`, `-update`), stderr 메시지, 종료 코드 |
| `ParseProcSpec` + fuzz | CPU 스케줄러 trace 한 줄 파서 + `FuzzParseProcSpec` | 테이블 테스트, fuzz 불변식(어떤 입력에도 panic 없음, 성공하면 직렬화 왕복) |
| `Workload(seed, cfg)` | CPU 스케줄링 프로젝트의 워크로드 생성 규칙(프로세스 도착 간격·수행 시간·IO 요청 간격·IO 처리 시간을 MIN~MAX 균등 분포로)을 PCG로 | 같은 시드 → golden과 동일, 모든 값이 범위 안, 평균 근사 |
| (선택) `internal` 분리 | `exercises/sched`(공개 API) + `exercises/sched/internal/queue` | 테스트는 공개 API만 사용, 외부 import 불가는 컴파일 에러 예제로 확인 |
