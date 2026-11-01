# 01. Java 개발자를 위한 빠른 지도 + 관찰 도구 (4h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: Kotlin 소스 한 파일은 어떤 JVM 클래스들이 되나? Java 문법 지식 중 그대로 가져올 것과 버릴 것은?

**README: 압축된 대응표 (입문 강좌처럼 길게 쓰지 않는다)**
- 선언: `val`/`var`, 타입 추론, `fun`, 식 본문(`= expr`), 기본 인자와 이름 인자, `Unit`(= void), `Any`(= Object), 문자열 템플릿, 범위(`..`, `until`, `..<`, `downTo`, `step`), `for (x in xs)`, `when`, `if`/`try`도 식(삼항 연산자 없음), `is`(instanceof와 스마트 캐스트), `as`/`as?`.
- 구조: 파일 하나에 여러 top-level 선언, 패키지와 디렉터리 불일치 허용(컨벤션은 공통 루트 생략), `static` 없음(top-level, `object`, `companion`), `new` 없음, 기본 `public`·`final`.
- 동등성: `==`는 `equals`(null 안전), `===`는 참조 비교. 문자열 `==` 버그(Java의 고전 버그)가 반대로 사라진다.

**내부 동작 (전부 예제로 확인)**
- top-level 함수와 프로퍼티는 `<파일명>Kt` 클래스의 static 멤버가 된다(`@file:JvmName`으로 이름 변경).
- 프로퍼티는 private 필드 + getter/setter다. `val x get() = …`(매번 계산)와 `val x = …`(한 번 저장)는 다르다.
- 문자열 템플릿은 `invokedynamic StringConcatFactory.makeConcatWithConstants`가 된다(확인함, JVM 9+ 타깃).
- 기본 인자는 synthetic `foo$default(…, int mask, Object)` 메서드와 비트마스크가 된다(확인함). 이름 인자는 컴파일 타임에만 존재한다.
- `when`은 조건 모양에 따라 다르게 컴파일된다(확인함).
  - 모든 분기가 `is`/`null` 검사이고, 2개 이상이며, guard가 없으면 → `invokedynamic SwitchBootstraps.typeSwitch` + `tableswitch` (Kotlin 2.4.20부터 JVM 21+ 타깃에서 Stable/기본값).
  - `is`와 동등 비교(`Empty ->`)가 섞이면 → `instanceof` 체인.
  - enum → `<File>Kt$WhenMappings` 배열 + `ordinal()`.
  - 분기가 적은 String → `Intrinsics.areEqual` 체인.
- public 함수의 non-null 파라미터마다 `Intrinsics.checkNotNullParameter`가 들어간다(확인함). 02의 예고편.
- 관찰 도구 사용법: `./check kotlin NN javap FQCN`, IntelliJ "Show Kotlin Bytecode → Decompile", `methodShapes()`가 무엇을 요약하는지.

**Java 습관 함정**: `Integer`처럼 `===` 쓰기, `val`을 불변 객체로 착각(불변 참조일 뿐), 정수 오버플로는 Java와 똑같이 조용함, `Char`는 숫자가 아님(`c.code`), `Long`에 `Int` 대입 불가(암시적 확장 변환 없음), IDE의 Java→Kotlin 자동 변환 결과(`!!`와 `var` 남발)를 그대로 두기.

**예제**
| 파일 | 무엇을 보여주나 | 관찰 |
|---|---|---|
| E01HelloBytecode | top-level 함수, 프로퍼티, 템플릿, 기본 인자가 무엇이 되나 | `methodShapes` 골든 + `javap`으로 `FooKt`, `foo$default` 확인 |
| E02WhenCompilation | 같은 의미의 `when` 4가지(is만 / is+동등 / enum / String) | 골든에 `typeSwitch` 유무, `$WhenMappings`, `instanceof` 체인 |
| E03EqualityAndIdentity | `==`/`===`, `Int?` 127과 128의 `===`, 문자열 `==` | 골든 출력 |
| E04ExpressionsEverywhere | if/when/try 식, `?: return`, 블록의 마지막 식이 값이 되는 것 | 골든 출력 |
| E05JavaSideBySide | 같은 로직의 `Calc.java`와 `Calc.kt` | `javap` 두 개를 나란히. 골든은 `methodShapes` 비교 |

**실습**
- X01 `classify(n: Int): String` — `when`과 범위로 분류. 테스트: 경계값.
- X02 Java→Kotlin 이식: `exercises/src`에 `LegacyTemperatureConverter.java`(제공)와 같은 인터페이스를 구현하는 Kotlin 클래스(빈 구현). 테스트는 `@ParameterizedTest`로 **두 구현에 같은 입력을 넣어 결과가 같은지** 확인한다(반올림, 예외 메시지 포함).
- X03 `buildReport(items)` — `buildString`과 템플릿, `joinToString`. 테스트: 정확한 문자열.
