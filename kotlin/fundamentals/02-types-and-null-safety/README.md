# 02. 타입 시스템과 null 안전성 (4h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `String`과 `String?`는 런타임에 같은 `java.lang.String`인데, 안전성은 어디서 생기고 어디서 깨지나? `Int`와 `Int?`는 왜 다른 JVM 타입인가?

**내부 동작**
- nullability는 컴파일 타임 타입이다. 런타임 보호는 컴파일러가 넣는 체크다: public 함수 진입부 `checkNotNullParameter`, Java 반환값을 non-null로 선언한 변수에 담을 때 `checkNotNullExpressionValue`, `!!`의 `checkNotNull`. 예제로 위치를 확인한다.
- `?.`, `?:`는 `ifnull` 분기로 컴파일된다. 객체를 만들지 않는다(Java `Optional`과의 차이).
- `Int` → `int`, `Int?`와 제네릭 인자 → `java.lang.Integer`. `Integer.valueOf` 캐시(-128..127) 때문에 `===` 결과가 값에 따라 달라진다.
- 스마트 캐스트는 컴파일러의 흐름 분석이다. `var` 프로퍼티, open/커스텀 getter, 다른 모듈의 프로퍼티에는 안 된다. 이유는 "검사와 사용 사이에 값이 바뀔 수 있다"이고, 해결은 지역 `val`로 복사.
- `lateinit`은 필드 + getter의 null 검사 → `UninitializedPropertyAccessException`. primitive에는 불가.
- `Unit`(싱글턴 객체), `Nothing`(bottom 타입: `throw`, `TODO()`, `error()`, `?: return`이 타입 검사를 통과하는 이유), `Nothing?`.
- **platform type `T!`**: Java 반환값은 nullable인지 모른다. 추론 타입으로 받으면 체크가 없어서 NPE가 **멀리서** 나고, non-null로 선언해 받으면 **경계에서** 즉시 난다. JSpecify(`org.jspecify.annotations`)는 공식 문서상 기본 `strict`(오류)다(어느 버전부터 기본값인지는 **미확인**).
- 숫자: 암시적 확장 변환이 없다(`toLong()`을 명시해야 함).

**대안 비교와 판단**: `Optional<T>`(할당 발생, 체이닝 API) vs `T?`(0비용, 언어 연산자). Kotlin 코드 안에서는 `Optional`을 쓰지 않는다. Java API 경계에서만 변환한다. `!!`는 "여기서 null이면 버그"라는 단언일 때만 쓴다. 대부분은 `?: error("이유")`가 더 낫다(메시지가 남음).

**Java 습관 함정**: Java API 결과를 추론 타입으로 받아 깊숙이 전파하기, `map[key]!!`, Jackson/Gson처럼 리플렉션으로 객체를 만드는 라이브러리가 non-null 필드에 null을 넣는 문제(Kotlin 체크를 우회함), `Int?` 컬렉션에서 `===`.

**예제**
| 파일 | 무엇을 | 관찰 |
|---|---|---|
| E01NullCheckBytecode | 파라미터 체크, platform 값 대입 체크, `!!`가 어디에 삽입되나 | `methodShapes` 골든 (`Intrinsics.*` 호출 목록) |
| E02PlatformTypeNpe | Java `LegacyDao.find()`가 null을 반환할 때 3가지 수신 방법 | 골든: 예외 종류 + 스택 최상단 프레임(어느 줄에서 터지나) |
| E03BoxingIdentity | `Int?` 127/128 `===`, `List<Int>` 원소 동일성 | 골든 |
| E04ReflectionBypass | 리플렉션으로 non-null 필드에 null 주입 → 사용 지점에서 NPE | 골든 (예외 위치) |
| E05OptionalVsNullable | 같은 체인을 Optional과 `?.`로 짤 때 할당량 | 골든 (B/호출, `-XX:-DoEscapeAnalysis`) |

**실습**
- X01 `SafeConfig.parse(map: Map<String, String?>)` — 필수와 선택 키, 기본값, 에러 메시지. 테스트: 누락/형식 오류 메시지와 성공 케이스.
- X02 `UserDirectory` — 제공된 Java `LegacyUserRepository`(null과 null 원소를 반환함)를 감싸 **경계에서 nullability를 확정**하는 Kotlin 퍼사드. 테스트: Java가 null을 줄 때 Kotlin API가 약속한 타입대로 동작하는지(nullable 반환 또는 명시적 예외).
- X03 `fail(msg): Nothing`과 `parsePort(s: String?): Int`(`?:`와 `Nothing` 활용). 테스트: 정상값, 경계값, 예외 메시지.
