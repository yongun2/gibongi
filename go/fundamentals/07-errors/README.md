# 07. 에러·defer·panic: 값으로서의 에러

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 06 · 3h · 관련 변화: `errors.Join`·다중 `%w`(1.20), `panic(nil)` → `*runtime.PanicNilError`(1.21), **nil 검사 순서 컴파일러 버그 수정(1.25)**, `errors.AsType`(1.26)

**핵심 질문**

1. Go는 왜 예외 대신 값을 반환하며, 에러 값은 내부적으로 무엇인가?
2. 에러에 문맥을 덧붙이면서도 호출자가 원인을 판별하게 하려면(`%w`, `errors.Is/As/AsType`)?
3. defer는 인자를 언제 평가하고 언제 실행하며, 비용은 얼마인가? 반환값을 바꿀 수 있나?
4. panic은 언제 정당하고, recover는 어디까지 잡나?

**README**

- ① 내부 동작
  - **에러의 정체**: `error`는 `Error() string` 하나짜리 인터페이스다. 06장의 규칙(typed nil 포함)이 그대로 적용된다.
  - **에러의 종류**
    - 센티널 값(`var ErrNotFound = errors.New(..)`): 포인터 동일성으로 비교한다.
    - 에러 타입: 필드를 가진 구조체(`*fs.PathError`).
    - 불투명 에러 + 동작 인터페이스(`interface{ Timeout() bool }`).
  - **래핑**
    - `fmt.Errorf("..: %w", err)`는 `Unwrap() error`를 가진 값을 만든다 → 체인이 생긴다.
    - `errors.Is`는 체인을 따라가며 `==` 또는 `Is` 메서드로 찾는다.
    - `errors.As`와 `errors.AsType[T]`(1.26, 제네릭이라 리플렉션 없이 빠름)는 타입으로 찾는다.
    - `errors.Join`과 다중 `%w`는 `Unwrap() []error`를 가진 트리를 만든다.
  - **스택 트레이스가 없다**: 에러는 값일 뿐이다. 문맥은 래핑 메시지 체인으로 남긴다(`open config: read header: unexpected EOF`).
  - **defer**
    - 함수가 끝날 때 LIFO로 실행된다.
    - **인자는 defer 문을 실행하는 시점에 평가**된다.
    - 이름 있는 반환값을 수정할 수 있다(Close 에러 합치기, recover 후 에러 반환).
    - 대부분 open-coded(1.14+)라 비용이 거의 없다.
    - 루프 안 defer는 **함수 끝**에 실행된다. 루프 반복마다 실행되지 않는다.
  - **panic과 recover**
    - panic은 스택을 풀면서 defer를 실행한다.
    - recover는 **같은 고루틴의 defer 안**에서만 효과가 있다.
    - **다른 고루틴의 panic은 프로세스 전체를 끝낸다.**
    - 런타임 fatal error(맵 동시 쓰기, 전체 데드락)는 recover할 수 없다.
  - **1.25 nil 검사 수정** **[RN 1.25]**
    - `f, err := os.Open(..); name := f.Name(); if err != nil {..}` 같은 코드가 1.21~1.24에서는 컴파일러 버그로 우연히 동작했다.
    - 1.25부터는 nil 포인터 panic이다. "에러부터 확인" 규칙의 근거로 쓴다.
- ② 차이

  | Java | Go |
  |---|---|
  | checked exception: 시그니처의 `throws` | 반환형의 `error`가 계약 |
  | unchecked exception | panic (= 버그) |
  | try/catch/finally: 비지역 점프 | `if err != nil` + defer: 실패 가능한 줄이 코드에 그대로 보임 |
  | catch 상위 타입 | `errors.As/AsType`(타입), `errors.Is`(값), 동작 인터페이스 |
  | 자동 스택 트레이스 | 래핑 메시지 체인 |
  | try-with-resources의 suppressed exception | defer에서 `errors.Join` |
  | 스레드의 uncaught exception은 그 스레드만 죽임 | **고루틴 panic은 프로세스 종료** |

- ③ 판단 기준
  - 호출자가 분기해야 하는 조건만 센티널이나 타입으로 공개한다. 나머지는 `%w`로 문맥만 붙인다.
  - `%w`로 감싼 하위 에러 타입은 API의 일부가 된다. 감출 것이면 `%v`.
  - 에러는 한 번만 처리한다(로그를 남기고 반환까지 하지 않는다).
  - panic은 불변식 위반, 도달 불가 지점, 초기화 실패(`MustX`)에만 쓴다. 라이브러리 API 밖으로 panic을 흘리지 않는다.
  - recover는 경계(요청 핸들러, 고루틴 진입점)에서만 쓴다.
  - 쓰기용 파일의 `Close` 에러는 데이터 손실 신호이므로 무시하지 않는다.
- 함정
  - typed nil error.
  - `%v`로 감싸 체인이 끊긴다.
  - 래핑된 에러를 `==`로 비교한다.
  - 루프 안 `defer f.Close()`.
  - `defer fmt.Println(i)`에서 인자가 조기 평가된다.
  - 고루틴 안 panic 미처리로 서버가 내려간다.
  - recover를 defer 밖에서 호출한다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `wrapChain` | 3단 래핑 메시지, `errors.Is` true, `%v`로 감싸면 false | 출력 |
| `asType` | `errors.AsType[*fs.PathError]` (1.26) vs `errors.As` **[로컬]** | 출력 |
| `join` | `errors.Join` 메시지(줄바꿈 구분) + `Is` | 출력 |
| `deferOrder` | LIFO, 인자 평가 시점, 이름 있는 반환값 수정 | 출력 |
| `deferInLoop` | 가짜 리소스 카운터: 루프 안 defer는 함수 끝까지 열린 채 → 최대 동시 열림 N vs 1 | 출력 |
| `recoverScope` | 같은 고루틴 recover, `PanicNilError` 메시지 | 출력 |
| `goroutinePanicKillsProcess` | main의 recover가 다른 고루틴 panic을 못 잡고 종료 코드 2 | `observe.Run` |
| `nilCheckOrder` | err 확인 전 `f.Name()` → nil pointer panic (1.25+) | `observe.Run` |
| `Benchmark*` | defer vs 직접 호출(차이 작음), `errors.New` vs `fmt.Errorf` 생성 비용 | `--bench` |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| SFS 에러 체계 | `ErrNotFound/ErrExists/ErrNotDir/ErrNotEmpty/ErrNoSpace`, `*PathError{Op,Name,Err}` + `Unwrap`, `Message(err)`가 셸 출력 문구로 변환 | 두 겹 래핑 후에도 `Is/AsType`, 메시지 `ls: nodir: No such file or directory` |
| `CopyFile(dst, src) (err error)` | defer Close, 쓰기 Close 에러를 `errors.Join`, 실패 시 부분 파일 삭제 | 정상 복사, 원본 없음 → `errors.Is(err, fs.ErrNotExist)`, Close 실패 주입(인터페이스 대역) 시 에러 반환 + 파일 없음 |
| `SafeGo(fn) <-chan error` | 고루틴 panic을 에러로 변환하는 recover 경계 | panic 값이 메시지에 포함, 정상 종료면 nil, 채널 close |
