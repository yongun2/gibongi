# 10. 프로젝트 구성·모듈·테스트 (3h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: Kotlin 코드베이스의 빌드 단위, 가시성 단위, 테스트 단위는 무엇이고 Java 프로젝트와 무엇이 다른가?

**내부 동작**
- Gradle Kotlin DSL 읽기(08 활용): `plugins`, `kotlin { jvmToolchain }`, `compilerOptions`(jvmTarget, `-Xjsr305`, `allWarningsAsErrors`, `explicitApi()`), source set, version catalog, convention plugin. **이 저장소의 `build-logic/`이 실제 교재**다.
- K2 컴파일러, 증분 컴파일, Gradle·Kotlin 데몬, configuration cache. kapt vs KSP(언급만).
- 패키지 vs 디렉터리(강제 없음, 컨벤션), 파일 하나에 여러 클래스.
- `internal` = 컴파일 모듈. 테스트 source set은 main의 internal에 접근할 수 있다(associated compilation).
- 바이너리 호환성: inline 함수, 기본 인자, data class(`copy`/`componentN` 시그니처) 변경의 영향. binary-compatibility-validator(언급).
- 테스트: JUnit 6(Kotlin suspend 테스트 지원), `kotlin.test` 단언(`assertFailsWith<T>`, contract로 `assertNotNull` 뒤 스마트 캐스트), 백틱 테스트 이름, `@Nested`, `@ParameterizedTest`, kotest-property, kotlinx-coroutines-test. 모킹: 기본 final → 인터페이스와 fake를 우선(MockK/Mockito는 언급만).

**예제**: E01 InternalMangling(리플렉션으로 internal 멤버의 JVM 이름, 골든) / E02 ClassFileVersion(ClassFile API로 major version 69 = Java 25, 섹션 14는 61 = Java 17, 골든) / E03 BuildScriptAnatomy(`build-logic`의 컨벤션 플러그인을 읽으며 DSL 문법 대응표, 실행 없음).

**실습**: X01 `LruCache<K, V>` — 테스트가 `@Nested`, `@ParameterizedTest` 사용. / X02 `RunLength.encode/decode` — 테스트가 kotest-property `checkAll`로 왕복 성질 검사. / X03 이 저장소에 섹션을 하나 추가하는 절차를 문서로 따라 하기(빌드 스크립트 한 줄 + 디렉터리 규약).
