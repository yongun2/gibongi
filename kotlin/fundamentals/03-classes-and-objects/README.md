# 03. 클래스·프로퍼티·data/sealed/object/value class (5h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: data class, object, companion, sealed, value class는 각각 어떤 JVM 클래스가 되고, Java의 record·static·enum·Lombok과 무엇이 다른가?

**내부 동작**
- 기본 `final`(상속하려면 `open`). 상속 설계를 강제하는 대신 프록시 기반 프레임워크와 충돌한다(Spring all-open 플러그인은 kotlin-spring에서 다룬다).
- 초기화 순서: primary 생성자 → 선언 순서대로 프로퍼티 초기화식과 `init` 블록. 상위 생성자가 open 멤버를 호출하면, 하위 클래스의 non-null 프로퍼티가 **null로 관측**된다(타입 시스템이 깨지는 대표 사례).
- data class가 생성하는 것: `equals/hashCode/toString/componentN/copy`. **primary 생성자 프로퍼티만** 포함한다. `copy`는 얕은 복사다. 배열 프로퍼티는 참조로 비교한다. 가변 프로퍼티를 HashSet 키로 쓰면 원소를 잃어버린다. `@JvmRecord`와 Java record를 비교한다.
- `object` → 클래스 + `static final INSTANCE`, `<clinit>`에서 생성. JVM 클래스 초기화가 thread-safe lazy 싱글턴을 보장한다(첫 접근 시점을 예제로 확인). `data object`.
- `companion object` → 중첩 클래스 `Companion`의 인스턴스 + 바깥 클래스의 static 필드. `@JvmStatic`은 static 브리지, `@JvmField`는 필드 직접 노출, `const val`은 사용처에 인라인되는 상수.
- sealed class/interface + `when` 망라성: 하위 타입을 추가하면 모든 `when`이 컴파일 오류가 난다(Java `default`와의 차이). 01의 `typeSwitch`와 연결.
- enum: `entries`(미리 만든 리스트, 할당 0)와 `values()`(호출마다 배열 복사, 3원소 enum이면 32B/호출 — 확인함).
- `@JvmInline value class`: 자기 타입으로 쓰이면 기반 타입(`long`), 제네릭·nullable·인터페이스로 쓰이면 박싱. `List<UserId>`는 `box-impl`을 호출한다(확인함). 함수 이름 맹글링(`findUser-_JJX7kE`, 확인함). `===` 금지.
- `nested`(기본, Java의 static nested) vs `inner`(바깥 참조 보유) — Java와 기본값이 반대.
- 가시성: `internal` = 모듈(컴파일 단위). 바이트코드에서는 public이고 이름이 맹글링된다(09에서 Java 쪽에서 확인). `private` top-level = 파일 private.

**판단 기준**: 값 객체는 data class(불변 `val`만). 상태 기계·결과 타입은 sealed. 단위가 있는 원시값(ID, 금액)은 value class(박싱 규칙 숙지). 전역 상태는 `object`에 두지 않는다.

**Java 습관 함정**: 생성자에서 open 메서드 호출, body에 선언한 프로퍼티가 equals에서 빠지는 것, `copy`로 깊은 복사를 기대하기, `inner` 남용, `companion` = static이라고 착각(Java에서 `Foo.Companion.bar()`로 보임), getter가 매번 계산된다는 것을 모르는 것.

**예제**
| 파일 | 무엇을 | 관찰 |
|---|---|---|
| E01DataClassAnatomy | data class vs `@JvmRecord` vs Java record vs 일반 class | 리플렉션으로 생성된 메서드 목록 골든 + `javap` |
| E02InitOrder | 프로퍼티 초기화식, `init`, 상위 생성자에서 open 호출 | 실행 순서 trace 골든 (non-null 프로퍼티가 null로 보이는 순간) |
| E03ObjectAndCompanion | `INSTANCE`, `Companion`, `@JvmStatic`, `const`, 클래스 초기화 시점 | `methodShapes` + 초기화 로그 골든 |
| E04ValueClassBoxing | `UserId`를 그대로, 제네릭으로, nullable로, `List`에 담을 때 | 할당 B/원소 골든 (`-XX:-DoEscapeAnalysis`) |
| E05EnumEntriesVsValues | `values()` vs `entries` | 골든: values() 32B/호출, entries 0B |
| E06DataClassHashTrap | 가변 data class를 HashSet에 넣고 변경 | 골든: `contains` false, 크기 1 |

**실습**
- X01 `Money` value class(통화 + minor unit `Long`) — 연산자 오버로딩, 불변식(`init { require }`), 통화가 다르면 예외. 테스트: 산술, 예외, **핫루프 할당 예산**(값 클래스를 제네릭에 흘리면 실패).
- X02 `OrderState` sealed 상태 기계 — `transition(event)`가 불가능한 전이는 sealed 결과로 거부. 테스트: 전이표 전체.
- X03 `Cart` — 불변 data class + `copy` 기반 갱신. HashSet/HashMap 키로 써도 안정적이어야 함. 테스트: 동등성·해시 안정성·얕은 복사 함정(리스트 프로퍼티 공유) 확인.
