# 09. Java 상호운용 심화 (4h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: 내 Kotlin API를 Java 동료가 호출하면 무엇이 보이나? Java 라이브러리를 Kotlin에서 쓸 때 타입 안전성은 어디서 새나?

**내부 동작**
- Kotlin → Java: 프로퍼티는 `getX/setX/isX`, top-level은 `FileKt`(`@file:JvmName`), companion은 `Foo.Companion.bar()`(`@JvmStatic`), `@JvmField`, `@JvmOverloads`(기본 인자 오버로드 생성), `@Throws`, `internal`은 이름이 맹글링된 public, value class는 맹글링(`@JvmName`), Kotlin이 생성하는 `@NotNull`/`@Nullable` 어노테이션(Java IDE 경고의 근거).
- Java → Kotlin: platform type 재정리(02), SAM 변환(04), getter/setter의 프로퍼티 문법, `is` 접두 boolean, 키워드 충돌은 백틱, Java 컬렉션이 mutable과 read-only 양쪽으로 보이는 것, varargs, `wait/notify`.
- 혼합 컴파일 순서: Kotlin 컴파일러가 Java 소스를 읽고 Kotlin을 먼저 컴파일한 뒤 javac가 Kotlin 클래스를 classpath로 컴파일한다(이 저장소의 `examples/`가 실제로 이렇게 섞여 있음).
- JSpecify `@NullMarked`: Java 쪽 주석 전략, Kotlin의 strict 기본값.

**Java 습관 함정**: Kotlin `List`를 Java 코드가 수정함(05 E05), 기본 인자가 Java에서 보이지 않음, `object` 접근은 `INSTANCE`, Java에서 null을 넘기면 `NullPointerException: Parameter specified as non-null is null`(오히려 좋은 실패), `internal`이 Java에서 보임.

**예제**: E01 WhatJavaSees(같은 API의 어노테이션 전후를 리플렉션으로 덤프, 골든) / E02 JavaCallsKotlin(`examples/`의 Java 파일이 Kotlin API 호출, 골든) / E03 NullFromJava(진입 체크 예외 메시지, 골든) / E04 JSpecifyBoundary(`org.jspecify:jspecify` 의존성, Java `@NullMarked` 클래스와 Kotlin 타입, 골든 + 컴파일 오류 예시는 주석).

**실습**
- X01 Java 친화 API 만들기: `RateLimiter` Kotlin API에 정적 팩토리, 오버로드, checked 예외 선언을 추가. **main 브랜치가 컴파일되도록** 테스트는 Java 소스가 아니라 리플렉션으로 JVM 시그니처를 검사한다(`Modifier.isStatic`, 오버로드 개수, `exceptionTypes`).
- X02 Platform-type 방화벽: Java `LegacyInventory`(null, 가변 리스트 반환)를 감싸는 Kotlin 퍼사드. 테스트: Java 쪽 null/변경이 Kotlin API로 새지 않는지.
