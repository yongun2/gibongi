# 05. 컬렉션과 Sequence의 내부

> 선행: 04(람다·inline). 예상 시간 6시간.
> 실행: `./check kotlin 05` (예제 검증 + 실습 테스트) · `./check kotlin 05 examples` · `./check kotlin 05 javap <FQCN>`

## 핵심 질문
`xs.filter { … }.map { … }`는 for 루프와 무엇이 다른가? `asSequence()`를 붙이면 무엇이 바뀌고, Java Stream과는 무엇이 같고 다른가?
이 섹션이 끝나면 "이 코드에서는 무엇을 쓸까"를 **측정 근거를 들어** 말할 수 있어야 한다.

## 1. Kotlin 컬렉션의 정체: 인터페이스는 Kotlin, 객체는 java.util
- `Iterable → Collection → List` / `MutableList`는 Kotlin 타입이다. 하지만 **런타임 객체는 전부 `java.util.*` 구현체**다.
  컴파일러가 `kotlin.collections.List`를 `java.util.List`로 매핑한다(mapped type). 별도의 Kotlin 컬렉션 런타임은 없다.
- 그래서 `List`는 **읽기 전용 "보기(view)"이지 불변 컬렉션이 아니다.** (E05)
  - `listOf(1, 2, 3)`의 실체는 `java.util.Arrays$ArrayList`다. 캐스팅하면 `set`이 된다(`add`는 `UnsupportedOperationException`).
  - `val view: List<String> = mutableList`는 같은 객체를 다른 타입으로 볼 뿐이다. 원본이 바뀌면 같이 바뀐다.
  - Java 코드는 Kotlin의 읽기 전용 개념을 모른다. `java.util.List`로 받아서 `add` 한다.
  - 진짜 불변이 필요하면 경계에서 **복사**(`toList()`)하거나, 변경 불가 뷰(`Collections.unmodifiableList`)와 그 의미(원본 변경은 보임)를 이해하고 고른다.
- 06(변성)에서 다시 본다: 읽기 전용이라서 `List<out E>`가 공변일 수 있다.

## 2. Iterable 연산: inline 함수 + 단계마다 새 리스트
`filter`, `map`은 `Iterable`의 **inline 확장 함수**다. 표준 라이브러리 구현은 대략 이렇다:
```kotlin
inline fun <T> Iterable<T>.filter(p: (T) -> Boolean): List<T> = filterTo(ArrayList<T>(), p)
inline fun <T, R> Iterable<T>.map(f: (T) -> R): List<R> = mapTo(ArrayList<R>(collectionSizeOrDefault(10)), f)
```
E02의 바이트코드 요약(실제 출력)을 보면:
```
sumEvenSquaresIterable
  new          : ArrayList, ArrayList
  invokedynamic: (없음)
  calls        : …, ArrayList.<init>, Iterable.iterator, Iterator.hasNext, Iterator.next, Number.intValue,
                 Collection.add, CollectionsKt.collectionSizeOrDefault, Long.valueOf, CollectionsKt.sumOfLong
```
- **람다 객체가 없다**(`invokedynamic` 없음). 람다 본문은 호출 지점에 복사됐다(`javap`으로 보면 `irem` 같은 명령이 함수 안에 그대로 있다).
- 대신 **단계마다 `ArrayList`를 새로 만든다.** `filter` 결과는 크기를 모르니 10에서 시작해 1.5배씩 늘리며 배열을 복사하고, `map`은 입력 크기만큼 한 번에 잡는다.
- `List<Int>`의 원소는 `Integer`다. 꺼낼 때 `Number.intValue()`로 언박싱하고, `map { it.toLong() * it }`의 결과는 `Long.valueOf`로 **다시 박싱**해서 리스트에 넣는다.
- 실행 순서는 **수평(단계별)**이다. `filter`가 9개 원소를 전부 처리한 뒤에 `map`이 시작된다(E01).

## 3. Sequence: 지연 + 원소 단위 + 람다 객체
`asSequence()`는 원본을 감싸기만 한다. `filter`/`map`은 **inline이 아니고**, 새 `Sequence` 객체(`FilteringSequence`, `TransformingSequence` …)를 만들어 앞 단계를 감싼다. 아무것도 계산하지 않는다(E01: `toList()` 전 람다 호출 수 0).
```
sumEvenSquaresSequence
  new          : (없음)
  invokedynamic: LambdaMetafactory.metafactory, LambdaMetafactory.metafactory
  calls        : …, CollectionsKt.asSequence, SequencesKt.filter, SequencesKt.map, SequencesKt.sumOfLong
```
- 람다는 `invokedynamic`으로 `Function1` 객체가 되어 넘어간다(캡처가 없으니 call site마다 한 번 만들어 재사용된다. 04 참고).
- 터미널 연산(`toList`, `sum`, `first` …)이 맨 끝 iterator의 `next()`를 당기면, 원소 하나가 체인을 **수직으로** 끝까지 통과한다.
  → 중간 리스트가 없다. `take`/`first`에서 **필요한 만큼만** 계산하고 멈춘다(E01: filter 6회, map 4회. Iterable은 9회, 5회).
- 대가:
  - 원소마다 iterator 체인을 따라 가상 호출이 여러 번 일어나고, `Function1.invoke(Object): Object`를 거친다 → **`Sequence<Int>`는 primitive 특화가 없어서 박싱한다.** E03에서 Sequence는 원소당 12.0B를 만든다(짝수 50만 개 × `Long` 24B).
  - 작은 컬렉션이나 단순 연산에서는 이 고정 비용이 이득보다 크다(공식 문서도 같은 경고).
- stateful 연산(`sorted`, 그리고 `distinct`는 원소별 처리지만 본 원소 집합을 들고 있음)은 지연을 끊거나 메모리를 쓴다. E04: `sorted()` 앞 단계는 모든 원소에 대해 먼저 실행된다.
- 한 번만 순회할 수 있는 Sequence가 있다(`iterator().asSequence()`, `constrainOnce()`). 두 번째 순회에서 `IllegalStateException`(E04).

## 4. Java Stream: Sequence와 같은 수직 처리, 다른 설계
- 수직 처리와 short-circuit은 Sequence와 같다(E01에서 trace가 똑같다).
- 차이:
  - Stream은 `Spliterator`와 `Sink` 체인으로 **push** 방식, Sequence는 iterator **pull** 방식이다.
  - Stream은 `IntStream`/`LongStream` 같은 **primitive 특화**가 있고(E03에서 `Java IntStream` 0B), `parallel()`을 지원하며, 한 번만 소비할 수 있다.
- **Kotlin에서 Stream을 쓸 때의 함정(이 환경에서 관찰)**: `list.stream().mapToLong { it.toLong() * it }`처럼 Kotlin 람다를 Java의 제네릭 SAM(`ToLongFunction<? super T>`)에 넘기면, 컴파일러가 `Function1`을 만들고 그것을 SAM으로 감싸는 어댑터를 하나 더 만든다. 어댑터는 `Function1.invoke`가 돌려준 `Long` 객체를 언박싱한다 → **원소마다 `Long`이 박싱**된다(E03: Kotlin 람다 12.0B vs SAM 생성자 0.0B vs Java 코드 0.0B. 바이트코드는 E02의 `sumEvenSquaresStreamFromKotlin`). 원인을 설명하는 공식 문서는 찾지 못했다. Kotlin 버전을 올리면 E02/E03 골든이 바뀌는지 확인해 볼 것.

## 5. for 루프: 가장 단순하고, 가장 적게 만든다
- `for (x in intArray)`는 인덱스 루프로 컴파일된다(할당 0). `for (x in list)`는 `Iterator` 하나 + `Number.intValue()` 언박싱(할당 0. 이미 있는 `Integer`를 읽기만 함).
- `for (i in 0 until n)`은 `IntRange`를 만들지 않는 순수 int 루프이고, `for ((i, v) in list.withIndex())`도 컴파일러가 `IndexedValue` 할당 없이 최적화한다(04에서 확인).
- `IntArray.sumOf { … }`는 inline이라 루프와 같다(0B). 그런데 **`IntArray.filter { … }`는 `List<Int>`를 돌려준다**. 원시 배열에서 출발해도 컬렉션 연산을 거치면 박싱 세계로 들어간다(E03에서 24.5~28.5B로 가장 나쁨).

## 6. 한눈에 비교

| | for 루프 | Iterable 연산 | Sequence | Java Stream |
|---|---|---|---|---|
| 평가 | 즉시 | 즉시(단계별, 수평) | 지연(원소별, 수직) | 지연(원소별, 수직) |
| 중간 컬렉션 | 없음 | 단계마다 `ArrayList` | 없음 | 없음 |
| 람다 | 본문 그대로 | **inline**(객체 없음) | `Function1` 객체 + `invoke` | SAM 객체(Kotlin 람다면 어댑터가 추가될 수 있음) |
| primitive 특화 | `IntArray` 등 | 없음(`List<Int>` 박싱) | 없음 | `IntStream` 등 |
| short-circuit | `break` | `first` 등은 앞 단계를 다 끝낸 뒤 | 있음 | 있음 |
| 무한 소스 | while | 불가 | `generateSequence`, `sequence {}` | `Stream.iterate` |
| 재사용 | – | 결과가 List | 대부분 재순회 가능(예외 있음) | 1회성 |
| 병렬 | 직접 | 없음 | 없음 | `parallel()` |
| E03 원소당 할당 | **0.0B** | 20.5B | 12.0B | 0.0B(Java 코드) / 12.0B(Kotlin 람다) |

## 7. 그래서 언제 무엇을 쓰나
1. **기본값은 Iterable 연산.** 작거나 중간 크기 컬렉션, 1~2단계, 결과를 List로 써야 할 때. inline이라 오버헤드가 낮고 읽기 쉽다.
2. **Sequence**: (a) 큰 입력에 3단계 이상, 또는 (b) `first`/`take`/`any`처럼 일찍 끝낼 수 있는 연산, 또는 (c) 입력이 크거나 끝이 없는 스트림(파일 라인, `generateSequence`)일 때. 단, 원소가 primitive면 박싱 비용을 같이 계산한다.
3. **primitive 대량 수치 계산이나 할당 예산이 있는 핫패스**: `IntArray`/`LongArray` + for 루프, 또는 inline `sumOf`/`count`/`forEach`. Stream이 필요하면 `IntStream`.
4. **Java Stream**: Java API가 Stream을 주고받을 때, 또는 `parallel()`이 정말 필요할 때. Kotlin 람다를 넘기면 박싱 어댑터가 생길 수 있으니 핫패스면 측정한다.
5. 애매하면 **측정**한다. E03의 방법(할당량)과 시간(stderr)이 기준이다. 감으로 Sequence를 붙이지 않는다.

## 8. 흔한 함정
1. `IntArray.filter`/`map`이 `List<Int>`를 돌려준다(박싱).
2. 짧은 리스트에 습관적으로 `asSequence()`(오버헤드만 늘어남).
3. `asSequence() … toList()` 뒤에 다시 `.map`(그 순간부터 eager로 돌아감).
4. Sequence 람다 안의 부수효과(로그, 검증)는 터미널 연산 전에는 실행되지 않는다(E01). 반대로 터미널 연산을 두 번 하면 두 번 실행된다.
5. 한 번만 순회 가능한 Sequence를 재사용(E04).
6. `List`를 불변으로 착각: 캐스팅, 원본 공유, Java 코드(E05).
7. `listOf(...)` 결과에 `set`이 된다(`Arrays$ArrayList`).
8. 개수 세기에 `groupBy { it }.mapValues { it.value.size }`(키마다 리스트 생성)나 `map[k] = map[k]!! + 1`(128 이상이면 `Integer` 박싱). → `groupingBy { it }.eachCount()`(X04).
9. Kotlin 람다로 Java `Stream<Integer>`의 `mapToLong`/`mapToInt` 호출(박싱 어댑터).
10. Sequence 중간의 `sorted()`가 지연의 이점을 끊는다(E04).

## 9. 예제 — 이 순서로 실행하며 관찰한다
| 예제 | 볼 것 | 해 볼 것 |
|---|---|---|
| E01EvaluationOrder | 같은 파이프라인의 람다 호출 순서와 횟수 | `take(4)`를 `take(1)`로 바꾸고 골든 diff 보기 |
| E02BytecodeShape | inline/람다 객체/Stream의 바이트코드 모양 | `./check kotlin 05 javap s05.examples.E02BytecodeShapeKt`로 원본 보기 |
| E03AllocationPerElement | 전략별 원소당 할당(기본, compact headers) | `-XX:-DoEscapeAnalysis`를 지우고 JIT이 무엇을 지우는지 보기 |
| E04ShortCircuitAndState | short-circuit, `sorted`, 무한, 1회성 | `sorted()` 위치를 옮겨 보기 |
| E05ReadOnlyIsNotImmutable | 팩토리별 실제 클래스, 읽기 전용의 한계 | `toList()`/`unmodifiableList` 차이 확인 |

골든을 일부러 깨뜨려 보는 것이 학습이다. 예제를 고치면 `./check kotlin 05 examples`가 기대값과의 diff를 보여 준다.

## 10. 실습
| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| X01LazyPipe | `kotlin.sequences` 없이 `Pipe<T>`의 filter/map/take/toList/generate | 결과, **터미널 전 0회 실행**, **수직 순서 trace**, take가 상류를 **딱 3개만** 당김, 무한 소스, 재순회, `hasNext()` 멱등성 |
| X02SlowRequests | 로그 파싱 + 느린 요청 path 상위 n개 | 파싱 경계값, 순서와 중복 제거, **답이 확정되면 더 읽지 않음**(그 뒤 줄은 읽으면 예외) |
| X03AllocationBudget | 짝수 제곱합, 히스토그램, 고유값 개수 | 정답 + **할당 예산**(1M 원소에서 1KiB 등). `filter.sumOf`나 `HashSet<Int>`는 실패 |
| X04ReadOnlyBoundary | `Playlist.tracks` 노출, `wordCounts` | 캐스팅·Java 컬렉션 API로 수정해도 내부 불변, 등장 순서, **할당이 단어 종류 수에 비례** |

## 더 읽을거리
- Kotlin 공식 문서 "Sequences", "Collections overview"
- Effective Kotlin, Item "Prefer Sequence for big collections with more than one processing step"
- 14장(GC): 여기서 측정한 바이트가 GC 로그에서 어떻게 보이는지
