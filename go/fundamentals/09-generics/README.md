# 09. 제네릭: 타입 집합, GC shape, 자료구조

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 06 · 3.5h · 관련 변화: 제네릭 타입 별칭(1.24), 자기 참조 제약(1.26), **제네릭 메서드(1.27)**, 함수 타입 문맥의 타입 추론 확장(1.27)

**핵심 질문**

1. 타입 파라미터의 제약은 인터페이스와 무엇이 같고 다른가(타입 집합, `~int`, `comparable`)?
2. 컴파일러는 제네릭 함수를 어떻게 기계어로 만드나(GC shape stenciling + dictionary)? 그게 성능에 무슨 뜻인가?
3. 제네릭 / 인터페이스 / 구체 타입 중 언제 무엇을 쓰나?
4. Go 1.27 제네릭 메서드는 무엇을 가능하게 하고, 무엇은 여전히 안 되나?

**README**

- ① 내부 동작
  - **제약 = 타입 집합을 정의하는 인터페이스**
    - `~int`는 기저 타입이 int인 모든 타입, `int | float64`는 합집합이다.
    - 미리 정의된 제약: `comparable`, `cmp.Ordered`, `any`.
    - 연산자(`+`, `<`)는 집합의 모든 타입이 지원할 때만 쓸 수 있다.
  - **구현: GC shape stenciling + dictionary**
    - 메모리 표현(shape)이 같은 타입끼리 코드 하나를 공유한다. **모든 포인터 타입은 하나의 shape**다.
    - 로컬 확인 **[로컬]**: `*A`, `*B`는 `Join[go.shape.*uint8]` 하나를 공유한다. `Max`는 int, int64, float64마다 따로 생긴다.
    - 타입별 정보(메서드 포인터, 타입 디스크립터)는 숨은 dictionary 인자로 넘어간다. 그래서 포인터 shape에서 **제약의 메서드를 호출하면 dictionary를 통한 간접 호출**이 된다. 인터페이스 호출과 비슷하고 인라인되지 않는다.
  - **제네릭 메서드(1.27)** **[RN 1.27, 블로그, 로컬]**
    - 메서드가 자기 타입 파라미터를 선언할 수 있다: `func (l List[E]) Map[R any](f func(E) R) List[R]`.
    - 인터페이스 메서드는 타입 파라미터를 가질 수 없다. 제네릭 메서드는 인터페이스를 구현하지 못한다. 이유: 따로 컴파일되는 패키지가 모든 인스턴스를 알 수 없다.
  - 그 밖의 변화: 제네릭 타입 별칭(1.24), 자기 참조 제약 `type Adder[A Adder[A]]`(1.26).
- ② 차이

  | Java | Go |
  |---|---|
  | 타입 소거: 인스턴스 하나, 원시 타입 불가(`List<Integer>` 박싱) | shape별 코드, `[]int`가 박싱 없이 그대로 |
  | 와일드카드·변성(`? extends`) | 없음. 불변(invariant): `[]Dog`는 `[]Animal`이 아니다 |
  | `<T extends Comparable<T>>` | `cmp.Ordered` 또는 자기 참조 제약 |
  | (C++ 템플릿은 완전 특수화) | Go는 그 사이의 절충 |

- ③ 판단 기준 ("When To Use Generics" 기준)
  - 컨테이너와 자료구조, 슬라이스·맵·채널에 대한 범용 알고리즘 → 제네릭.
  - 동작(메서드)을 추상화하는 것 → 인터페이스. 메서드만 호출하는 제네릭 함수는 인터페이스 인자보다 빠르지 않고, 오히려 느릴 수 있다.
  - 타입마다 구현이 다르면 인터페이스, 코드가 같으면 제네릭.
- 함정
  - 제약 메서드 호출이 공짜라고 오해한다.
  - 타입 파라미터를 과하게 쓴다.
  - `any` 제약 + 타입 스위치(제네릭인 척하는 코드).
  - 포인터 메서드 제약 패턴 `[T any, PT interface{*T; M()}]`의 복잡도.
  - 제네릭 메서드로 인터페이스를 구현하려 한다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `typeSets` | `~int` 사용자 정의 타입 허용, `int \| float64` 합집합 연산 | 출력 |
| `shapes` | `Join[go.shape.*uint8]` 1개, `Max[go.shape.int/int64/float64]` 3개 | `observe.Symbols` (`go tool nm`) |
| `genericMethod` | `List[E].Map[R]` 체이닝 (1.27) | 출력 |
| `invariance` | `[]Dog`를 `[]Animal`로 못 넘김 | `observe.CompileErrors` |
| `genericMethodNoIface` | 제네릭 메서드로 인터페이스 만족 불가 | `observe.CompileErrors` |
| `Benchmark*` | `Sum`: 구체 vs 제네릭(int shape) vs 인터페이스 / 제약 메서드 호출(`*A`): 구체 vs 제네릭 vs 인터페이스 | `--bench` |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `Heap[T]` | `less` 함수 주입, 슬라이스 기반, **안정적**(동률이면 삽입 순서 = FCFS) | 정렬 순서, 무작위 우선순위 1000개에서 동률 FCFS, Peek/Len, zero value (SJF·SRTN·Guaranteed ready queue) |
| `LRU[K comparable, V any]` | 맵 + **인덱스 기반** 이중 연결 리스트(`[]node`, `int` 링크), 축출 콜백 | 축출 순서, Get이 최근성 갱신, 용량 1, 콜백, 10만 연산 시간 상한(O(n) 구현 걸러냄) (VM LRU) |
| `Set[T]` + 제네릭 메서드 | `Add/Has/Len` + `Map[U comparable](f) Set[U]` (1.27) | 결과, 빈 집합, 중복 합쳐짐 |
