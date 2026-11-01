# 04. 함수·람다·inline·확장 함수 (5h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: 람다는 JVM에서 무엇이 되고 언제 객체가 생기나? inline은 무엇을 없애고 무엇을 대가로 치르나? 확장 함수는 왜 오버라이드되지 않나?

**내부 동작**
- 함수 타입 `(A) -> B`는 `kotlin.jvm.functions.Function1<A, B>`이다. `invoke(Object): Object`라서 **primitive 인자와 반환값이 박싱**된다. 128 이상 값으로 `f(f(x))`(두 번 호출)를 한 번 하면 48B가 생긴다(확인함).
- 람다 컴파일: **Kotlin 2.0부터 기본이 `invokedynamic` + `LambdaMetafactory`**(KT-65681. 이전 방식은 `-Xlambdas=class`). 캡처하지 않는 람다는 call site마다 캐시된 1개, 캡처 람다는 평가할 때마다 새 객체(16B, 확인함), 캡처한 `var`는 `kotlin.jvm.internal.Ref$IntRef`로 힙에 올라간다(확인함). Java는 effectively final만 캡처하게 막고, Kotlin은 허용하는 대신 박스를 만든다.
- SAM 변환: `fun interface`, Java SAM. **Java 제네릭 SAM 파라미터에 Kotlin 람다를 넘기면 `Function1` + 어댑터 이중 래핑과 반환값 박싱이 생긴다.** SAM 생성자를 쓰면 직접 생성된다(Kotlin 2.4.21에서 관찰함. 원인 **미확인**). 05 E03과 연결.
- `inline`: 함수 본문과 람다 본문이 호출 지점에 복사된다 → 람다 객체도, `invoke`도, 박싱도 없다. non-local `return`이 가능하다. `noinline`(람다를 값으로 저장할 때), `crossinline`(다른 실행 문맥에서 호출되어 non-local return을 막아야 할 때), `reified`(타입 인자를 호출 지점에서 구체화 → `is T`, `T::class`).
  - 대가: 코드 크기 증가, public inline 함수는 private 멤버에 접근 불가(`@PublishedApi internal`), **inline 함수 본문을 바꾸면 호출 측을 재컴파일해야 반영됨**(바이너리 호환성), 재귀 불가.
- 확장 함수 = 첫 파라미터가 receiver인 static 메서드 → **정적 디스패치**. 선언 타입으로 고르고, 같은 시그니처면 멤버가 이긴다.
- scope 함수(`let/run/with/apply/also`)는 전부 inline이다. 차이는 receiver(`this` vs `it`)와 반환값(람다 결과 vs 객체)뿐이다 → 표 하나로 정리하고 남용(중첩 `it` 섀도잉)을 경계한다.
- `vararg`와 spread `*arr`는 **배열을 복사**한다. `tailrec`은 루프로 바뀐다. 지역 함수와 클로저.

**판단 기준**: 람다를 받는 작은 유틸 함수는 inline(컬렉션 연산이 그렇다). 람다를 저장하거나 다른 스레드·나중으로 넘기면 일반 함수(또는 `noinline`). 핫패스에서 `(Int) -> Int` 같은 함수 타입을 값으로 돌리면 박싱을 의심한다.

**Java 습관 함정**: 확장 함수를 오버라이드처럼 기대하기, `forEach { return }`이 바깥 함수를 빠져나가는 것(Java `forEach`의 return과 다름), Java Stream을 Kotlin 람다로 쓰면서 박싱을 모르는 것, scope 함수 체인 과용.

**예제**
| 파일 | 무엇을 | 관찰 |
|---|---|---|
| E01LambdaShapes | 비캡처/캡처/`var` 캡처/inline 호출의 바이트코드 | `methodShapes` 골든 (`Ref$IntRef` new, `invokedynamic` 개수) |
| E02LambdaAllocation | 100만 번: 비캡처 0B, 캡처 16B, inline 0B, `Function1` 박싱 48B | 골든 (`-XX:-DoEscapeAnalysis`) |
| E03NonLocalReturn | `forEach { return }` vs `return@forEach` vs `crossinline`(컴파일 오류는 주석) | 실행 trace 골든 |
| E04ExtensionStaticDispatch | `fun Animal.name()` vs `fun Dog.name()`, 멤버 우선 | 골든 |
| E05JavaSamFromKotlin | Kotlin 람다 / SAM 생성자 / Java 코드로 `Stream<Integer>.mapToLong` | `methodShapes` + 할당 골든 |
| E06Reified | erased 버전과 reified 버전의 `filterIsInstance` | 골든 + `javap`(호출 지점에 `instanceof`가 복사된 것) |

**실습**
- X01 `retry(times, backoff, block)` — 일반 함수와 inline 버전. 테스트: 시도 횟수, 마지막 예외 전파, inline 버전에서 non-local return 동작.
- X02 `((A) -> R).memoize()` — 클로저 상태. 테스트: 호출 횟수, 키 동등성.
- X03 `EventBus.on<T> { }` (`inline reified`) — 테스트: 타입별 디스패치, 하위 타입 처리.
- X04 `sumOfPairs(xs: IntArray, f: (Int, Int) -> Int)` — **할당 예산 테스트**(inline이 아니면 박싱 때문에 실패).
