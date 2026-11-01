# 03. 슬라이스와 배열: 헤더, append, aliasing

> 선행: 01, 02 · 예상 시간: 4시간 (README 1h, 예제 1h, 실습 2h)
> 실행: `./check go 03` · 벤치마크: `./check go 03 examples --bench`

## 핵심 질문

1. `b := a[1:3]` 다음에 `b = append(b, x)`를 하면 `a`가 바뀌는가? 언제 바뀌고 언제 안 바뀌는가?
2. 슬라이스를 함수에 넘기는 것은 "참조 전달"인가 "값 전달"인가? `ArrayList`를 넘기는 것과 무엇이 다른가?
3. `append`는 언제 새 배열을 할당하고, 얼마나 키우는가? 미리 `cap`을 잡으면 실제로 얼마나 이득인가?
4. 큰 슬라이스의 일부만 들고 있으면 메모리는 어떻게 되는가? `s = s[1:]`로 만든 큐는 왜 위험한가?

## ① 내부 동작

### 배열은 값이다

`[6]int`는 "int 6개가 연속으로 붙은 값"이다. 길이가 타입의 일부이므로 `[6]int`와 `[5]int`는 다른 타입이고,
대입하거나 함수에 넘기면 **48바이트 전체가 복사**된다. Java 배열은 힙 객체이고 변수에는 참조만 들어가지만,
Go 배열은 변수 자리에 원소가 직접 들어간다. 그래서 배열은 잘 쓰지 않고, 쓰는 곳은 정해져 있다:
크기가 고정이고 값 의미론이 필요한 곳(해시 결과 `[32]byte`, 디스크 구조체의 `[60]byte` 파일 이름, 맵 키).

### 슬라이스는 헤더다

슬라이스 값은 배열을 가리키는 3워드짜리 구조체다(런타임의 `runtime.slice`).

```
s := arr[1:3]

  ┌─────┬─────┬─────┐
  │ ptr │ len │ cap │   s: 헤더 24바이트 (arm64)
  │  ●  │  2  │  5  │
  └──┼──┴─────┴─────┘
     └─────┐
           ▼
  ┌─────┬─────┬─────┬─────┬─────┬─────┐
  │  0  │  1  │  2  │  3  │  4  │  5  │   arr: backing array
  └─────┴─────┴─────┴─────┴─────┴─────┘
        ├── len=2 ──┤
        ├─────────── cap=5 ───────────┤
```

`s`가 "보는" 범위는 `[1 2]`, append로 "쓸 수 있는" 범위는 `arr[1:6]`이다.

- `len`: 지금 보이는 원소 수. `s[i]`는 `i < len`일 때만 허용(범위 검사 → 위반 시 panic).
- `cap`: `ptr`부터 backing array 끝까지 남은 칸 수. `s[:n]`으로 `n ≤ cap`까지 다시 늘릴 수 있다.
- 슬라이싱 `s[i:j]`는 **새 헤더를 만들 뿐 원소를 복사하지 않는다**. `cap`은 `원래 cap - i`.
- `s[i:j:k]`(full slice expression)는 `cap`을 `k-i`로 잘라 낸 헤더를 만든다.

`unsafe.Sizeof(s)`가 원소 수와 상관없이 24(arm64)인 이유가 이것이다. 슬라이스를 함수에 넘기면 이 24바이트가 **값으로 복사**된다.
그래서 호출된 함수가 원소를 고치면 호출자에게 보이지만(같은 배열), `append`로 `len`을 늘린 것은 보이지 않는다(헤더 복사본의 `len`만 바뀜).
`append`가 결과를 **반환**하고 `s = append(s, x)`로 받아야 하는 이유도 같다.

### append의 두 갈래

```
append(s, x):
  len < cap  → s의 backing array의 s[len] 자리에 x를 쓰고 len+1인 헤더를 반환 (할당 없음)
  len == cap → runtime.growslice: 더 큰 배열을 새로 할당 → 기존 원소 복사 → 새 헤더 반환
```

첫 번째 갈래가 aliasing 버그의 근원이다. **같은 backing array를 공유하는 다른 슬라이스가 그 칸을 보고 있으면, 조용히 덮어쓴다.**
두 번째 갈래는 반대로 "어느 순간 갑자기 공유가 끊긴다". 같은 코드가 `cap`에 따라 다르게 동작한다는 점이 핵심이다.

성장 정책(`runtime.nextslicecap`, Go 1.27.1 소스 기준):

- 필요한 길이가 현재 cap의 2배를 넘으면 그 길이로.
- cap < 256이면 2배.
- 그 이후엔 `newcap += (newcap + 3*256) / 4`를 반복: 2배에서 1.25배로 부드럽게 수렴.
- 마지막으로 메모리 할당기의 **size class**로 올림 → 그래서 실제 비율은 1.66, 1.51처럼 "깔끔하지 않다".

그리고 컴파일러가 개입한다(버전별 변화):

| 버전 | 변화 | 관찰 |
|---|---|---|
| 1.25 | 크기가 변수인 `make`도 32바이트 이하면 스택 버퍼 사용 | `Example_allocsVariableMake` (int 4개 0회, 5개 1회) |
| 1.26 | nil에서 시작하는 `append` 루프에 32바이트 스택 버퍼를 투기적으로 사용, 반환 시점에만 힙으로 이동(`runtime.move2heap`) | `Example_growthFromNil` (첫 cap이 1이 아니라 4) |
| 1.26 | 위 최적화는 `-race` 빌드에서 꺼짐 | 같은 예제가 `-race`에서는 `1 2 4 8 ...` |

결론: **`cap`의 정확한 값은 구현 디테일이다.** 버전, 빌드 모드, 원소 크기에 따라 바뀐다. 코드는 "append 후에도 공유가 유지되는가"에
절대 의존하면 안 된다. 의존해야 한다면 그 사실을 `s[i:j:j]`나 `slices.Clone`으로 **명시적으로 강제**한다.

### 탈출 분석과 슬라이스

backing array가 스택에 놓일지 힙에 놓일지는 컴파일러가 정한다(`go build -gcflags=-m`).

- 크기가 상수이고 함수 밖으로 안 나가면 스택(암묵적 할당 상한 64KiB, `cmd/compile/internal/ir/cfg.go`).
- 반환하거나, 전역/힙 객체에 저장하거나, 원소 주소를 밖에 넘기면 힙.
- **"does not escape"라고 나와도 크기가 변수면 힙 할당이 일어날 수 있다.** 진단 메시지와 실제 할당 횟수는 따로 확인한다(`testing.AllocsPerRun`).

GC 비용은 11장에서 다루지만 여기서 미리 기억할 것: 힙에 놓인 backing array는 **한 덩어리**로 살거나 죽는다.

### 메모리 보유(retention)

GC는 "이 배열의 3번째 칸부터만 살아 있다"를 모른다. 내부 포인터(interior pointer) 하나라도 살아 있으면 배열 전체가 산다.

- `head := big[:16]`만 남겨도 `big` 전체(예제에선 64MiB)가 유지된다 → 필요한 만큼 `slices.Clone`.
- 포인터 원소 슬라이스에서 `s = s[1:]`로 앞을 버리면, 버려진 칸이 여전히 객체를 가리킨다 → 그 객체도 회수되지 않는다.
- 그래서 Go 1.22부터 `slices.Delete`, `DeleteFunc`, `Compact` 등은 줄어든 꼬리 칸을 zero value로 지운다.

### 그 밖의 규칙

- nil 슬라이스(`var s []int`)는 `ptr=nil, len=0, cap=0`. `len`, `range`, `append` 모두 정상 동작한다. 빈 슬라이스(`[]int{}`)와 다른 점은
  `== nil` 비교와 직렬화뿐이다: `encoding/json`(v1 API)은 nil을 `null`, 빈 것을 `[]`로 쓰고, Go 1.27의 `encoding/json/v2`는 nil도 `[]`로 쓴다.
- 슬라이스끼리는 `==`로 비교할 수 없다(nil과만 가능). `slices.Equal`을 쓴다. 그래서 맵 키가 될 수 없다(배열은 가능).
- `for i, v := range s`에서 `v`는 원소의 **복사본**이고, `s`는 루프 시작 시 한 번만 평가된다(루프 중 append해도 반복 횟수 불변).

## ② 대안·Java와의 차이

| | Java `ArrayList<T>` / 배열 | Go 슬라이스 |
|---|---|---|
| 정체 | 힙 객체 하나. 변수는 참조 | 24바이트 헤더 값 + 공유 가능한 배열 |
| 함수에 넘기면 | 같은 객체 공유. `add`가 호출자에게 보임 | 헤더 복사. 원소 쓰기는 보이고 `append`의 len 변화는 안 보임 |
| 부분 뷰 | `subList`: 원본 구조가 바뀌면 `ConcurrentModificationException` | `s[i:j]`: 아무 검사 없이 같은 배열을 공유. 덮어써도 조용함 |
| 원소 저장 | 객체 배열은 참조의 배열(원소마다 별도 객체 + 헤더) | `[]T`는 T 값이 연속 저장. `[]*T`를 써야 Java와 비슷해짐 |
| 성장 | 1.5배, `ensureCapacity` | 2배 → 1.25배 수렴 + size class, 일부는 컴파일러가 스택에 둠 |
| for-each 변수 | 객체 참조 → `p.x++`가 원본 수정 | 원소 복사본 → `p.x++`는 사라짐 |
| `substring`/부분 뷰 메모리 | Java 7u6 이후 `substring`은 복사 | 문자열·슬라이스 모두 공유 → 보유 문제 |

`[]T` 대 `[]*T`:

- `[]T`: 원소가 연속 → 캐시 지역성 좋고 할당 1회, GC가 포인터 없는 T는 스캔조차 안 함. 대신 `&s[i]`는 append 후 낡을 수 있다.
- `[]*T`: 원소마다 별도 할당, 포인터 추적 비용. 대신 원소의 "정체성"이 안정적이라 여러 곳에서 같은 객체를 가리켜도 안전.
- OS 프로젝트 예: 프로세스 테이블은 `[]Process`(전부 미리 생성, 이후 append 없음)도 `[]*Process`도 가능하지만,
  ready queue가 프로세스를 가리켜야 하면 인덱스(`int`)나 `*Process`를 저장한다. `&procs[i]`를 저장하고 `procs`에 append하는 조합이 가장 위험하다.

## ③ 언제 무엇을 쓰나

- 최종 크기를 알거나 대략 알면: `make([]T, 0, n)` 후 append, 또는 `make([]T, n)` 후 인덱스 대입(가장 빠름: 벤치마크 참고).
- 남에게 부분 슬라이스를 건넬 때, 상대가 append할 수 있으면: `s[i:j:j]`로 cap을 자른다. 상대가 오래 들고 있으면: `slices.Clone`.
- 큰 데이터에서 작은 조각만 오래 보관: `slices.Clone`/`strings.Clone`으로 끊는다.
- 큐: 원형 버퍼(실습 3). 짧게 쓰고 버리는 큐라면 `s = s[1:]`도 괜찮지만 포인터 원소면 꺼낸 칸을 nil로.
- 원소를 수정하며 순회: `for i := range s { s[i].x++ }`. 큰 struct를 값으로 순회하면 매번 복사 비용도 든다.
- 2차원: 행 크기가 같으면 `[]T` 하나 + `r*cols+c`(실습 4). 행마다 길이가 다를 때만 `[][]T`.
- 고정 크기 + 비교/맵 키/값 복사가 필요: 배열.

## 흔한 함정 (Java 습관 포함)

1. `append(s, x)`의 결과를 버리거나 다른 변수에 받는다 → 원본과 결과가 cap에 따라 공유되기도, 끊기기도 한다.
2. 함수 안에서 `s = append(s, x)` 하고 끝 → 호출자는 못 본다(Java `list.add` 습관). 반환하거나 `*[]T`를 받는다.
3. `for _, p := range procs { p.served++ }` → 아무것도 안 바뀐다(Java for-each 습관).
4. `running := &procs[i]` 저장 후 `procs = append(procs, ...)` → `running`은 옛 배열을 가리킨다.
5. `make([]int, n)` 후 `append` → 앞에 0이 n개 있다(`len`과 `cap` 혼동).
6. `s = s[1:]` 큐로 `*Job`을 다루면 꺼낸 Job이 GC되지 않는다.
7. JSON 응답에서 nil 슬라이스가 `null`로 나간다(v1 API). 클라이언트가 배열을 기대하면 `[]T{}`로 초기화하거나 v2 사용.
8. 테스트에서 `cap` 값을 단언한다 → 버전·`-race`에 따라 깨진다(예제로 확인).

## 예제 (`examples/`) — 먼저 출력을 예측하고 실행하라

| 예제 | 보여 주는 것 | 관찰 방법 |
|---|---|---|
| `Example_header` | 배열 값 복사, 헤더 24바이트, 슬라이싱은 같은 배열 | `unsafe.Sizeof`, `unsafe.SliceData` 비교 |
| `Example_appendAliasing` | cap 안 append끼리 덮어쓰기, cap 초과 시 공유 끊김 | 출력 |
| `Example_fullSliceExpr` | `s[i:j:j]`로 덮어쓰기 차단 | 출력 |
| `Example_passHeaderByValue` | 함수 안 append가 "숨겨진 쓰기"가 됨 | `s[:2]`로 숨은 원소 노출 |
| `Example_rangeCopies` | range 변수는 복사본 | 출력 |
| `Example_stalePointer` | append 후 원소 포인터가 낡음 | 출력 |
| `Example_nilVersusEmpty` | nil/빈 슬라이스, json v1/v2 차이 | 출력 |
| `Example_retention` | 16바이트 부분 슬라이스가 64MiB를 붙잡음 | `runtime.ReadMemStats` |
| `Example_growthPolicy` | 256 이후 성장 비율과 size class | 출력 |
| `Example_growthFromNil` | 1.26 스택 버퍼로 첫 cap이 4 (`-race`에선 제외) | 출력, `go test -race`와 비교 |
| `Example_allocsVariableMake` | "does not escape"여도 40바이트는 힙 할당 | `testing.AllocsPerRun` |
| `Example_escape` | 어떤 make가 힙으로 가는가 | `-gcflags=-m` 출력을 `internal/observe`로 검증 |
| `Benchmark*` | 성장 vs 사전 할당 vs 인덱스 대입 | `--bench`: allocs/op, ns/op |

더 해 볼 것(자동 검증 없음):

```sh
cd go/fundamentals/03-slices/examples
go build -gcflags='-m=2' . 2>&1 | grep escape.go      # 왜 탈출하는지 이유까지
go test -run '^$' -bench . -benchmem -cpuprofile cpu.out && go tool pprof -top cpu.out | grep growslice
go test -race -run Example_growth ./...                # 스택 버퍼 최적화가 꺼진 결과와 비교
```

## 실습 (`exercises/`)

| 실습 | 구현할 것 | 테스트가 확인하는 것 |
|---|---|---|
| 1. `Window` | 복사 없이 `s[i:j]`를 보여 주되 append가 원본을 못 덮게 | 같은 배열 공유(`unsafe.SliceData`), append 후 원본 불변 |
| 2. `FilterInPlace` | 할당 없이 제자리 필터 + 꼬리 zero 처리 | 결과, 배열 재사용, 꼬리가 0, `AllocsPerRun == 0` |
| 3. `JobQueue` | zero value로 쓰는 `*Job` FIFO 큐 | FIFO, 길이 일정 시 cap 유계, 정상 상태 할당 0회, Pop한 Job이 GC됨(`weak.Pointer`) |
| 4. `Matrix` | `[]float64` 하나로 행렬 + 행 뷰 | At/Set, Row가 뷰, Row에 append해도 다음 행 불변, 할당 ≤ 2회 |

실습 3 참고: `q.items = q.items[1:]`는 꺼낸 칸을 지우지 않으면 Job을 붙잡고(`PopReleasesJob` 실패),
지우더라도 창이 앞으로 밀리며 주기적으로 재할당한다(`SteadyStateNoAlloc` 실패). "head 인덱스만 증가" 방식은
버퍼가 끝없이 커진다(`BoundedWhenInterleaved` 실패). Java `ArrayDeque`가 쓰는 방식을 떠올려 보라.

## 다음 장과의 연결

- 04 맵: 맵은 슬라이스와 달리 **포인터 하나**(8바이트)다. 그래서 함수에 넘기면 삽입도 보인다.
- 05 문자열: `string`은 `{ptr, len}` 헤더다. 부분 문자열도 메모리를 공유한다.
- 10 이터레이터: `slices.Values`, `slices.Collect`, 그리고 Kotlin의 `map/filter`에 대응하는 eager/lazy 비교.
- 11 메모리: 포인터 없는 `[]int32` 페이지 테이블과 `[]*PTE`의 GC 비용 차이.
