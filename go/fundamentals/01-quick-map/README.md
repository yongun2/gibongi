# 01. Java 개발자를 위한 빠른 지도

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 없음 · 3h · 관련 변화: `range n`(1.22), `min/max/clear`(1.21), `new(expr)`(1.26)

입문 강좌처럼 문법을 하나씩 늘어놓지 않는다. Java→Go 대응표 한 장과, 대응표만으로는 오해하기 쉬운 네 가지(빌드 산출물, 가시성 단위, zero value, `:=` 스코프)만 깊게 다룬다.

**핵심 질문**

1. Go 프로그램은 무엇으로 빌드되어 어떻게 실행되나? JVM 없이 바이너리에 무엇이 들어가나?
2. 클래스·접근 제어자·생성자·예외·null은 Go에서 각각 무엇이 되나?
3. zero value는 왜 "초기화 안 된 값"이 아니라 "바로 쓸 수 있는 값"인가?
4. `:=`는 언제 새 변수를 만들고 언제 기존 변수에 대입하나?

**README**

- ① 내부 동작
  - **빌드 산출물**
    - `go build`는 정적 링크된 네이티브 바이너리를 만든다. 런타임(스케줄러·GC·할당기)이 그 안에 들어 있다.
    - JIT도 워밍업도 없다. 시작 시간은 ms 단위다.
    - `GOOS/GOARCH`만 바꾸면 크로스 컴파일된다.
  - **컴파일 단위와 강제 규칙**
    - 컴파일 단위는 패키지(디렉터리 하나)다.
    - 쓰지 않는 import와 변수는 경고가 아니라 **컴파일 에러**다.
    - 서식은 `gofmt` 하나뿐이다.
  - **가시성**
    - 이름이 대문자로 시작하면 패키지 밖에 공개된다.
    - 단위가 **클래스가 아니라 패키지**다. 같은 패키지라면 다른 타입의 비공개 필드도 읽을 수 있다.
  - **zero value**
    - 할당기가 0으로 채운 메모리가 곧 각 타입의 유효한 값이다(0, "", false, nil).
    - 관례상 타입은 zero value로 바로 동작하게 설계된다: `var mu sync.Mutex`, `var b bytes.Buffer`, `var s []int`.
    - nil은 포인터·슬라이스·맵·채널·함수·인터페이스 **6종에만** 있다. 문자열은 nil이 될 수 없다.
  - **선언과 스코프**
    - 형태: `var x T`, `x := e`(함수 안에서만), 다중 대입, 다중 반환.
    - `:=`는 왼쪽에 새 이름이 하나라도 있으면 성립하고, 나머지 이름에는 대입한다.
    - 안쪽 블록(`if`, `for`, `switch`의 초기화문 포함)의 `:=`는 바깥 변수를 **가린다**(섀도잉).
  - **제어문**
    - 반복문은 `for` 하나다(3절, 조건만, 무한, `range`, 1.22의 `range n`).
    - `if`와 `switch`에는 초기화문을 쓸 수 있다.
    - `switch`는 기본이 break이고, 이어서 실행하려면 `fallthrough`를 명시한다. 식 없는 `switch`는 if-else 체인과 같다.
    - 삼항 연산자는 없다.
  - **함수**
    - `(T, error)` 다중 반환이 기본 형태다.
    - comma-ok 관용구(`v, ok`)는 맵 조회, 타입 단언, 채널 수신에서 쓴다.
    - 이름 있는 반환값과 가변 인자(`...T`)가 있다. 오버로딩과 기본 인자는 없다.
- ② Java와의 차이 — 대응표 한 장(README 본문):

  | Java | Go |
  |---|---|
  | class + 필드 | struct + 메서드 |
  | 생성자 | zero value, 또는 `NewT` 함수 |
  | static 메서드/필드 | 패키지 수준 함수/변수 |
  | `static final` 상수 | `const` |
  | enum | `const` + `iota` (05) |
  | implements | 암묵적 구현 (06) |
  | extends | 임베딩 (02, 상속 아님) |
  | throws / try-catch | `error` 반환 (07) |
  | null / Optional | zero value, `(T, bool)`, `*T` |
  | `List` / `Map` / `Set` | `[]T` / `map[K]V` / `map[K]struct{}` |
  | for-each | `for range` |
  | try-with-resources | `defer` (07) |
  | `synchronized` | `sync.Mutex` (13) |
  | `Thread` | `go` (12) |
  | 애너테이션 | 없음 (struct tag, `//go:` 지시어) |
  | Maven / Gradle | `go mod` |
  | JUnit | `go test` |
  | Javadoc | doc 주석 + `go doc` |

- ③ 판단 기준
  - 조기 반환으로 happy path를 왼쪽에 정렬하고, 에러는 그 자리에서 처리한다. 짧은 변수명이 관용이다.
  - 타입은 zero value가 쓸모 있게 설계한다. 불변식이 있을 때만 `NewT`를 둔다.
  - getter/setter 대신 공개 필드를 쓴다. 불변식을 지켜야 하는 필드만 비공개 + 메서드로 감싼다.
- 함정
  - `:=` 섀도잉으로 바깥 `err`가 갱신되지 않는다.
  - 쓰지 않는 변수 에러를 `_ = x`로 덮어 버린다.
  - `for`나 `select` 안의 `switch`에서 `break`는 `switch`만 빠져나간다. 루프를 빠져나가려면 레이블이 필요하다.
  - `range n`과 루프 변수 의미론은 go.mod의 `go` 줄에 따라 달라진다(10장).

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `zeroValues` | 모든 기본 타입의 zero value(`%#v`), nil 6종, `var b bytes.Buffer` 바로 사용 | 출력 |
| `shadowing` | `if v, err := f(); ...` 블록 밖 err 미갱신 버그 재현 | 출력 |
| `commaOk` | 맵·타입 단언·채널의 `ok` | 출력 |
| `switchSemantics` | 기본 break, `fallthrough`, 식 없는 switch, for 안 switch의 레이블 break | 출력 |
| `namedResults` | 이름 있는 반환값 + `defer`가 반환값을 바꾸는 미리보기 | 출력 |
| (관찰 명령) | `go build -o app . && ls -lh app && otool -L app && time ./app` vs `time java Hello` | README 안내만 |

**실습** (툴 사용에 익숙해지는 워밍업)

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `ParseKV(line) (k, v string, ok bool)` | `strings.Cut`, 공백 trim, `#` 주석 줄 | 테이블 테스트(형식 익히기), 경계(빈 값, `=` 없음) |
| `TopWords(text, n) []string` | 빈도 내림차순, 동률이면 사전순 | 결과, **20회 반복해도 같은 순서**(맵 순회 무작위성 예고, `slices.SortFunc`+`cmp.Compare`) |
| `IntStack` | `Push/Pop/Len`, zero value로 사용 | `var s IntStack`로 바로 사용, 빈 스택 `Pop`은 `(0,false)`, 포인터 리시버 필요성(02 예고) |
