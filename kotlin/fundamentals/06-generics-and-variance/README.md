# 06. 제네릭·변성·타입 소거 (3h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `List<String>`은 `List<Any>`가 되는데 `MutableList<String>`은 왜 안 되나? Java 와일드카드(`? extends`, `? super`)는 Kotlin에서 어디로 갔나?

**내부 동작**
- 타입 소거는 Java와 같다(`is List<String>` 불가). 04의 `reified`로 우회한다.
- 선언 지점 변성 `out`(생산자)/`in`(소비자) vs Java 사용 지점 와일드카드(PECS). Kotlin도 사용 지점 변성(type projection, `Array<out T>`)을 지원한다.
- `List<out E>`(공변, 읽기 전용) vs `MutableList<E>`(무공변) → 05의 "읽기 전용 ≠ 불변"과 같은 뿌리.
- 바이트코드 시그니처: Kotlin `out T` 파라미터는 Java에서 `? extends T`로 보인다(타입 인자가 final이면 생략). `@JvmSuppressWildcards`/`@JvmWildcard`. 예제에서 리플렉션 `genericParameterTypes`로 확인.
- star projection `*`, `Nothing`/`Any?` 경계, `where` 다중 상한, definitely non-nullable `T & Any`, `@UnsafeVariance`.
- 배열: Java 배열은 공변이라 `ArrayStoreException`이 나지만, Kotlin `Array<T>`는 무공변이다.

**판단 기준**: 읽기만 하는 파라미터는 `out`, 쓰기만 하면 `in`. 라이브러리 시그니처(`Flow<out T>`, `Comparator<in T>`)를 읽을 수 있으면 충분하다. 고급 타입 트릭은 범위 밖.

**예제**: E01 VarianceInAction(Java 배열 공변 런타임 오류 vs Kotlin 컴파일 오류는 주석, 골든) / E02 SignatureWildcards(리플렉션으로 Java가 보는 시그니처, 골든) / E03 ErasureAndReified(`typeOf<T>()`, 골든).

**실습**: X01 `TypedKey<T>` 기반 이종 컨테이너(타입 안전 get/put). 테스트: 타입별 조회, 잘못된 키. / X02 `Source<out T>`·`Sink<in T>`와 `copy(from, to)` + `Box<out T>.map`. 테스트가 `Source<Int>` → `Sink<Number>` 같은 조합이 **컴파일되고** 동작하는지 확인(시그니처는 뼈대로 제공).
