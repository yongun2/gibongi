# 07. 예외와 에러 모델링 (3h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: checked exception이 없는 언어에서 실패를 어떻게 모델링하나? `Result`/`runCatching`은 언제 위험한가?

**내부 동작**
- Kotlin 함수의 바이트코드에는 `throws` 절이 없다 → Java 호출자가 checked 예외를 `catch`하면 "never thrown" 컴파일 오류 → `@Throws` 필요(09와 연결). 반대로 Kotlin에서 Java checked 메서드는 그냥 호출된다.
- `try`는 식이다. `use {}`는 inline try-with-resources이고, 본문과 `close`가 둘 다 실패하면 `addSuppressed`(예제로 확인).
- `require`/`check`/`error`/`requireNotNull` → `IllegalArgumentException`/`IllegalStateException`. contract 덕분에 `require(x != null)` 뒤에 스마트 캐스트가 된다.
- `Result<T>`는 value class다(성공 값은 그대로, 실패는 `Failure` 래퍼). **`runCatching`은 `Throwable`을 전부 잡는다** → `OutOfMemoryError`, `StackOverflowError`, `InterruptedException`(인터럽트 플래그 유실), **코루틴의 `CancellationException`**(12에서 좀비 코루틴), `TODO()`의 `NotImplementedError`까지 삼킨다. detekt 같은 도구가 경고하는 실무 함정.
- 도메인 에러는 sealed 계층으로 값으로 표현한다. 예외를 쓸지 값을 쓸지의 기준: 호출자가 **복구할 수 있고 흔한 실패**면 값, 버그나 환경 문제면 예외.
- 예외 생성 비용: `fillInStackTrace`가 스택 깊이에 비례한다. 제어 흐름에 예외를 쓰지 않는다.

**Java 습관 함정**: `catch (e: Exception)`을 습관적으로 넓게 쓰기(코루틴에서 취소를 삼킴), checked exception 감각으로 `@Throws` 없이 Java에 API 노출, `Optional`처럼 `Result`를 아무 데나 반환 타입으로 쓰기.

**예제**: E01 ThrowsForJava(리플렉션 `exceptionTypes`, 골든) / E02 UseAndSuppressed(`use` vs 수동 try/finally에서 원래 예외가 덮이는 것, 골든) / E03 RunCatchingSwallows(`Error`, 인터럽트 플래그, `TODO()`, 골든) / E04 ExceptionCost(스택 깊이별 생성 비용, stderr) / E05 ResultRepresentation(성공·실패의 할당, 골든).

**실습**: X01 설정 파서 — 오류를 **모두 누적**해 `sealed ParseResult`로 반환. 테스트: 오류 목록과 위치. / X02 `runCatchingSafe` — 치명적 오류(`VirtualMachineError`), `InterruptedException`(재인터럽트), `CancellationException`은 다시 던진다. 테스트: 각 경우. / X03 중첩 리소스 파이프라인 — 테스트: `close` 순서와 suppressed 예외.
