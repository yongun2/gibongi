# 07. 데이터 모델과 덕 타이핑 · 5h · 선행 05, 06

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `len(x)`, `x + y`, `x in y`, `for`, `==` 는 어떤 메서드로 번역되나? 인터페이스 선언 없이 "타입"은 어떻게 약속되나?

**README**
- 문법과 내장 함수는 특수 메서드로 번역된다:
  - `len` → `__len__`
  - `+` → `__add__`, 안 되면 `__radd__`
  - `==` → `__eq__`
  - `in` → `__contains__`. 없으면 `__iter__` 로 순회하고, 그것도 없으면 `__getitem__`
  - `bool` → `__bool__`, 없으면 `__len__`
  - `hash` → `__hash__`, `repr` → `__repr__`

  특수 메서드는 **타입에서** 찾는다. 인스턴스에 붙여 봐야 무시된다.
- 이항 연산 디스패치: `a.__add__(b)` 가 `NotImplemented` 를 돌려주면 `b.__radd__(a)` 를 시도한다. 오른쪽이 왼쪽의 서브클래스면 오른쪽이 먼저다. `NotImplemented`(값)와 `NotImplementedError`(예외)를 혼동하지 않는다.
- `__eq__`/`__hash__` 계약: 같으면 해시도 같아야 한다. `__eq__` 만 정의하면 `__hash__ = None` 이 자동으로 붙어 set/dict 에 넣을 수 없다. Java 의 equals/hashCode 와 같은 계약이지만 Python 은 깨진 해시 대신 해시 불가로 만들어 버린다. 가변 필드로 해시를 만들면 dict 안에서 실종된다.
- 덕 타이핑과 EAFP: isinstance 검사보다 "쓰고, 예외를 처리"한다. Java 의 명목적 인터페이스와 비교해 보라.
- `collections.abc`: `Mapping` 을 상속하면 `__getitem__`/`__iter__`/`__len__` 세 개만 구현해도 `get`/`keys`/`items`/`__contains__`/`__eq__` 가 공짜로 생긴다. `isinstance(x, Iterable)` 은 `__subclasshook__` 로 구조적으로 판정한다. `abc.ABC` + `@abstractmethod` 는 인스턴스를 만들 때 오류를 낸다.
- `typing.Protocol` 은 정적 구조적 타이핑이다(11장에서 심화). `@runtime_checkable` 은 메서드가 **있는지만** 보고 시그니처는 보지 않는다.
- `dataclass` 가 생성하는 것(`__init__`/`__repr__`/`__eq__`, `order`, `frozen` → `__hash__`, `slots`, `kw_only`). `eq=True` 이면서 frozen 이 아니면 `__hash__ = None` 이다. **가변 기본값(list/dict/set)은 `ValueError` 로 막는다** → `field(default_factory=list)`. 04장 기본값 함정과 같은 원리다. frozen dataclass 는 Java `record` 와 비슷하다. `NamedTuple`, `enum.Enum`(멤버가 싱글턴이라 `is` 비교 가능, `StrEnum`), `match` 의 클래스 패턴과 `__match_args__`.
- 언제 무엇을: 값 객체는 frozen dataclass, 튜플 호환이 필요하면 NamedTuple, 행동 계약은 Protocol(정적) 또는 ABC(런타임 강제와 mixin).

**Java 습관 함정**: 인터페이스부터 만든다. `equals` 만 고친다. `compareTo` 대신 `__lt__` 만 정의하고 정렬은 되리라 기대한다(`functools.total_ordering`). `instanceof` 분기.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_syntax_to_dunder.py` | `len`/`in`/`for`/`bool`/`+`/`==` 가 부르는 메서드와 대체 순서 | 모든 특수 메서드 호출을 로깅하는 클래스 |
| `ex02_binary_dispatch.py` | `__add__` → `NotImplemented` → `__radd__`, `sum()` 이 0 부터 시작하는 이유 | 호출 로그 |
| `ex03_eq_hash.py` | `__eq__` 만 정의하면 unhashable, 가변 키 실종 | set 에 넣은 뒤 필드를 바꾸고 `in` 결과 확인 |
| `ex04_abc_mixins.py` | Mapping 메서드 3개로 얻는 메서드 목록, 추상 메서드 누락, 구조적 isinstance | `dir` 차집합, `TypeError` |
| `ex05_dataclass_generated.py` | eq/frozen 조합별 `__hash__`, 가변 기본값 `ValueError` | `dataclasses.fields`, 조합표 |
| `ex06_match_dataclass.py` | `match` 로 dataclass 분해, `__match_args__` | 출력 |

| 실습 (`model.py`) | 테스트가 확인하는 것 |
|---|---|
| `Money(amount: Decimal, currency: str)` frozen dataclass | 통화가 다르면 `+` 가 `TypeError`, `sum([...])`(`__radd__` 0 처리), `Money + 1` 이 `NotImplemented` 경로로 `TypeError`, set 중복 제거, 같은 통화끼리 정렬 |
| `CaseInsensitiveDict(MutableMapping)` | `d["A"] is d["a"]`, 순회할 때 원래 키 표기 보존, `update`/`pop`/`setdefault` 를 공짜로 얻는다, 일반 dict 와 `==` |
| `SparseVector(Mapping)` | 0 값은 저장하지 않는다, `get`/`in`/`items`, `+`, `__eq__` |
| `area(shape)`: Circle/Rect/Triangle 을 `match` 로 처리 | 결과값, 모르는 타입이면 `TypeError` |
