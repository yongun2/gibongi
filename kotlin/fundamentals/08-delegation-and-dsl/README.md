# 08. 위임·수신 객체 람다·DSL·연산자 관례 (3h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `by lazy`, `by map`, 클래스 위임 `by`, `buildString {}`·Gradle Kotlin DSL 같은 코드는 내부적으로 무엇인가?

**내부 동작**
- 프로퍼티 위임: `getValue`/`setValue` 관례 → 숨은 `x$delegate` 필드 + getter가 위임 객체를 호출한다. `KProperty` 메타데이터.
- `lazy`: 기본 `SYNCHRONIZED`(이중 검사 잠금 `SynchronizedLazyImpl`), `PUBLICATION`(여러 번 계산될 수 있음), `NONE`. 초기화 람다 안에서 다른 lazy를 순환 접근하면 교착 위험이 있고, 코루틴에서는 suspend할 수 없다.
- `Delegates.observable/vetoable`, `by map`.
- 클래스 위임 `class X(impl: I) : I by impl` → 모든 메서드 포워딩이 생성된다. 함정: 위임 대상 내부의 self-call은 래퍼를 거치지 않는다(상속과 다름).
- 수신 객체 지정 람다 `T.() -> R`: `apply`/`with`/`buildList`/`buildString`의 정체. `@DslMarker`로 바깥 receiver가 암묵적으로 새어 들어오는 것을 막는다. 이 지식으로 Gradle Kotlin DSL(`plugins {}`, `dependencies {}`)을 읽는다.
- 연산자 관례 표(`get/set/invoke/contains/plus/compareTo/rangeTo/componentN/iterator/plusAssign`): 관용 코드를 읽기 위한 것.

**예제**: E01 LazyModes(`by lazy` 바이트코드 모양은 골든, 멀티스레드 초기화 횟수는 SYNCHRONIZED=1만 골든이고 PUBLICATION은 stderr) / E02 DelegationSelfCall(위임과 상속에서 self-call trace, 골든) / E03 DslScopeLeak(`@DslMarker` 없을 때 바깥 receiver 호출 trace, 골든) / E04 OperatorConventions(골든).

**실습**: X01 Markdown 문서 DSL(`@DslMarker`) — 테스트: 렌더링 결과 문자열. / X02 `CountingMap`(클래스 위임으로 `get` 횟수 세기) — 테스트. / X03 `by setting("PORT") { it.toInt() }` 커스텀 위임 — 프로퍼티 이름을 키로, 캐싱, 오류 메시지에 프로퍼티 이름 포함. 테스트.
