# 10. 함수·클로저·이터레이터: Kotlin `map/filter`를 Go로

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 03 09 · 4h · 관련 변화: **루프 변수 per-iteration(1.22)**, **range-over-func·`iter`(1.23)**, `strings.Lines/SplitSeq`(1.24), `reflect` 이터레이터(1.26)

사용자가 기준으로 든 Kotlin 예시에 대응하는 섹션이다. Iterable(eager) / Sequence(lazy) / for 루프 / inline 람다 대 Go의 eager 헬퍼 / `iter.Seq` / for 루프 / 컴파일러 인라이너를 같은 실험으로 비교한다.

**핵심 질문**

1. 클로저는 무엇을 캡처하나(값? 변수?), 캡처된 변수는 어디에 사나?
2. 1.22의 루프 변수 변경은 정확히 무엇을 바꿨고, 왜 go.mod의 `go` 줄에 따라 동작이 달라지나?
3. `for x := range seq`는 컴파일러가 무엇으로 바꾸나? break하면 이터레이터 쪽에서는 무슨 일이 일어나나?
4. for 루프 / eager 헬퍼(단계마다 슬라이스) / lazy `iter.Seq` 파이프라인 중 무엇을 언제 쓰나?

**README**

- ① 내부 동작
  - **함수 값과 캡처**
    - 함수 값 = 코드 포인터 + (클로저면) 캡처 환경 포인터다.
    - 캡처하는 것은 **변수 자체**다(복사 아님).
    - 클로저가 함수보다 오래 살 수 있으면 캡처된 변수는 힙으로 간다(`moved to heap: x`). 아니면 스택에 남는다(`func literal does not escape`).
  - **루프 변수(1.22)**
    - for 문의 변수가 **반복마다 새로** 만들어진다.
    - 적용 범위는 해당 모듈의 go.mod `go` 줄이 1.22 이상인 파일뿐이다 **[로컬: `go 1.21` → `3 3 3`, `go 1.22` → `0 1 2`]**.
    - 캡처되지 않으면 비용 차이가 없다. 캡처되면 반복마다 힙 할당이 생길 수 있다.
  - **메서드 값** `f := v.M`은 그 시점에 리시버를 평가한다(값 리시버면 복사본이 고정된다).
  - **인라이닝**
    - 비용 예산 80 이하인 함수를 인라인한다(`cmd/compile/internal/inline/inl.go` **[로컬 소스]**). `-m=2`가 `cost N exceeds budget 80`처럼 이유를 알려 준다.
    - Kotlin `inline fun`은 프로그래머가 강제한다. Go에는 키워드가 없고 컴파일러가 판단한다(막는 `//go:noinline`만 있다). PGO는 핫 함수의 예산을 올린다.
  - **range-over-func**
    - 컴파일러가 루프 본문을 `yield func(V) bool` 클로저로 바꾸고 `seq(yield)`를 호출한다.
    - 본문에서 break나 return이 일어나면 yield가 false를 돌려준다. 이터레이터는 그때 반환해야 한다.
    - 이터레이터가 이를 무시하고 다시 yield를 부르면 런타임 panic이다: `range function continued iteration after function for loop body returned false` **[로컬]**.
    - 본문 안의 defer, return, labeled break가 올바르게 동작하도록 컴파일러가 상태 변수를 넣는다.
  - **push와 pull**: `iter.Seq`(push) vs `iter.Pull`(코루틴 기반, 스택 전환 비용이 있고 `stop` 호출이 필수).
  - **인라인되면 체인이 루프로 펼쳐진다** **[로컬, Apple M4]**
    - `Map(Filter(slices.Values(xs)))` 체인 전체가 인라인되고, 모든 클로저가 `does not escape`다.
    - 측정 예(1000개, 짝수 제곱합):

      | 방식 | 시간 | 할당 |
      |---|---|---|
      | lazy (`iter.Seq` 체인) | 254ns | 0 |
      | 손 루프 | 480ns | 0 |
      | eager (단계마다 슬라이스) | 1483ns | 8회 |

    - 반대로 인라인이 막히는 경계에서는 원소마다 간접 호출이 생긴다: 함수 값으로 전달, 인터페이스, 재귀, 큰 본문.
  - **표준 라이브러리 이터레이터**: `slices.Values/All/Backward/Collect/Sorted/Chunk`, `maps.Keys/Values/All`, `strings.Lines/SplitSeq/FieldsSeq`. 표준에는 Map/Filter 어댑터가 **없다**(의도된 설계).
- ② 차이

  | Kotlin / Java | Go |
  |---|---|
  | `Iterable.map/filter`: 단계마다 리스트 할당 (eager) | 직접 쓴 eager 헬퍼: 같은 구조 |
  | `Sequence`: Iterator 객체 체인, 원소 단위 lazy **pull** | `iter.Seq`: 콜백 체인, 원소 단위 lazy **push**. 중간 리스트 없음, 조기 종료 가능 |
  | `inline` 람다: 바이트코드에 펼쳐져 람다 객체 0 | 인라이너가 성공하면 같은 효과. `-m`으로 확인 |
  | Java Stream: Spliterator, 박싱(IntStream 제외), JIT 의존 | AOT, 박싱 없음 |
  | 람다는 effectively final **값**을 캡처 | **변수**를 캡처. 수정이 공유된다 |

- ③ 판단 기준
  - 기본은 for 루프다. 단계가 1~2개면 루프가 가장 명확하다.
  - 컨테이너에 순회 API를 노출할 때는 `All() iter.Seq[...]` 메서드를 쓴다(트리, 큐, LRU).
  - 입력이 크거나 무한이거나, 조기 종료가 필요하거나, 중간 할당을 피해야 하면 lazy Seq를 쓴다(trace 줄 스트림).
  - 결과를 여러 번 쓰거나 정렬해야 하면 한 번 `Collect`한다.
  - 두 시퀀스를 나란히 진행해야 하면 `iter.Pull`을 쓰고 비용을 측정한다.
  - 상태가 여러 개이고 수명이 길면 클로저보다 구조체 + 메서드가 낫다.
- 함정
  - go.mod의 `go` 줄이 낮으면 옛 루프 변수 의미론이 적용된다.
  - yield가 false를 돌려줬는데 무시한다.
  - 일회용 이터레이터(파일 기반)를 두 번 range한다. 일회용인지 문서에 적는다.
  - 메서드 값에서 리시버가 그 시점으로 고정된다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `closureSharesVariable` | 두 클로저가 같은 변수 공유(카운터 생성기) | 출력 |
| `loopVarSemantics` | 이 모듈(go 1.27) `0 1 2` vs `testdata/loopvar121`(별도 go.mod `go 1.21`) `3 3 3` | 출력 + `observe.Run` |
| `escapeClosure` | `func literal escapes to heap`, `moved to heap: count` | `observe.Escapes` |
| `evaluationOrder` | eager: `map 1, map 2, map 3, filter 1, ...` / lazy: `map 1, filter 1, map 2, filter 2, ...` (Kotlin Sequence 데모와 같은 형태) | 출력 |
| `shortCircuit` | `Take(2)`에서 lazy는 소스를 3번 이하, eager는 전부 호출 | 호출 수 출력 |
| `yieldContract` | false를 무시하는 이터레이터 → 위 panic 메시지 | recover |
| `inlineReport` | `inlining call to Filter[go.shape.int]` 등 체인 전체 인라인 | `observe.Diag(-m)` |
| `methodValueBinding` | `f := c.Value` 후 c 변경 → f는 옛 값 | 출력 |
| `Benchmark*` | Loop / Eager / Lazy / Lazy(인라인 막힌 경계) | `--bench`, `go build -gcflags=-S`로 Lazy 본문에 `CALL`이 남는지 |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| 어댑터 `Filter/Map/Take/Zip` | `iter.Seq` 어댑터. `Zip`은 `iter.Pull` 사용 | 결과, **소스 호출 횟수**(lazy 여부), break 시 panic 없음(yield 계약), 길이가 다른 `Zip`에서 `stop` 호출 |
| `Tree[K].All()` | 재귀 중위 순회 push 이터레이터 | 순서, **조기 종료가 재귀 전체로 전파**(방문 노드 수 상한), 빈 트리 |
| `Records(r io.Reader) iter.Seq2[Record, error]` | trace를 lazy하게 파싱 | 결과, break 후 더 읽지 않음(바이트를 세는 Reader), 에러를 yield한 뒤 종료 |
| `EvenSquares` 3종 | 같은 계산을 loop / eager / lazy로 | 세 결과 일치 (성능은 벤치로 관찰) |
