# 06. 클래스와 속성 조회 · 5h · 선행 04

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `obj.x` 한 줄은 어디를 어떤 순서로 찾나? `self` 는 어디서 오나? Java 의 필드와 메서드는 Python 에서 무엇이 되나?

**README**
- 클래스도 객체(`type` 의 인스턴스)이고 클래스 본문은 실행되는 코드다. **본문에서 대입한 것은 클래스 속성이다.** Java 의 필드 선언 습관대로 `class A: items = []` 라고 쓰면 모든 인스턴스가 그 리스트를 공유한다(로컬 확인). 인스턴스 속성은 `__init__` 에서 `self.x = …` 로 만든다.
- 속성 조회 알고리즘(`object.__getattribute__`):
  1. `type(obj).__mro__` 에서 data descriptor 를 찾는다
  2. `obj.__dict__`
  3. 클래스의 non-data descriptor 나 일반 속성
  4. `__getattr__`

  대입은 인스턴스 `__dict__` 로 가서 클래스 속성을 가린다.
- **디스크립터**: 함수는 non-data descriptor 다. `obj.method` 는 `__get__` 이 만든 bound method 이고 `A.method` 는 그냥 함수다. `property` 는 data descriptor, `classmethod`/`staticmethod` 도 디스크립터다. 즉 `self` 는 마법이 아니라 디스크립터 프로토콜의 결과다. Java vtable 디스패치와 달리 매 호출이 런타임 조회다. 3.11+ 특화 명령 덕분에 비용은 작아졌다(15장에서 측정).
- 접근 제어가 없다: `_x` 는 관례이고, `__x` 는 name mangling(`_Class__x`)이다. 비공개가 아니라 상속할 때 이름 충돌을 막는 장치다. getter/setter 대신 공개 속성으로 시작하고, 검증이 필요해지면 `property` 로 **호출부를 바꾸지 않고** 전환한다. Java 에서 getter 를 미리 만드는 이유가 Python 에는 없다.
- 생성은 `__new__`(할당)와 `__init__`(초기화)로 나뉜다. 생성자 오버로딩 대신 `@classmethod` 대체 생성자(`from_json`)를 쓴다.
- 다중 상속과 C3 MRO. `super()` 는 "부모"가 아니라 **"MRO 에서 다음"** 이다(협력적 다중 상속, mixin). 상속보다 합성.
- `__slots__`: 인스턴스 `__dict__` 를 없애 메모리를 줄이고 속성 오타를 막는다. 대가는 동적 속성 불가와 다중 상속 제약이다. `dataclass(slots=True)`. 객체가 수백만 개일 때만 쓴다.
- `__init_subclass__`: 메타클래스 없이 하위 클래스 등록과 검증을 한다(플러그인 패턴). 메타클래스는 읽을 수 있을 정도로만 다룬다.

**Java 습관 함정**: 클래스 본문 필드 선언, getter/setter, `private` 대용으로 `__x` 남용, static 메서드만 모은 유틸 클래스, 깊은 상속 계층.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_class_vs_instance_attr.py` | 공유 리스트 버그, 대입이 클래스 속성을 가리는 과정 | `vars(obj)`, `vars(cls)` |
| `ex02_lookup_order.py` | data descriptor > 인스턴스 dict > non-data descriptor | 직접 만든 디스크립터 3종으로 판정 |
| `ex03_bound_methods.py` | `a.f is a.f` 가 False(매번 생성), `A.f.__get__(a)` 수동 바인딩 | `type()`, `hasattr(func, "__get__")` |
| `ex04_property_migration.py` | 공개 속성을 property 로 바꿔도 호출부는 그대로 | 출력 |
| `ex05_mro_super.py` | 다이아몬드 상속의 `__mro__` 와 super 호출 순서 | 호출 로그 |
| `ex06_slots_memory.py` | 인스턴스 10만 개의 메모리, 동적 속성 `AttributeError` | `tracemalloc` peak 판정 |

| 실습 (`attrs.py`) | 테스트가 확인하는 것 |
|---|---|
| `Typed(type_)` 디스크립터(`__set_name__`) | 타입이 틀리면 `TypeError`, 메시지에 속성 이름이 들어간다. 인스턴스별 값 분리, `Cls.field` 는 디스크립터 자신 |
| `lazy_property` (non-data) | 계산은 한 번이고 이후에는 인스턴스 `__dict__` 가 디스크립터를 우회한다(`vars(obj)` 로 확인) |
| `Temperature`: `celsius` 와 `fahrenheit` property(setter) | 상호 변환, 절대영도 미만이면 `ValueError` |
| `PluginBase.__init_subclass__(name=...)` | 하위 클래스를 정의만 해도 등록된다. 이름이 중복되면 `TypeError` |
| 다이아몬드 협력 `__init__` | 각 클래스의 `__init__` 이 정확히 한 번씩 MRO 순서로 불린다 |
