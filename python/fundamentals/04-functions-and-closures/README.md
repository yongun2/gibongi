# 04. 함수는 런타임 객체다 · 5h · 선행 02

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `def` 는 선언인가 실행인가? 클로저는 값을 잡나, 변수를 잡나? 데코레이터는 언제 실행되나?

**README**
- `def` 는 **실행되는 문장**이다. 실행될 때 코드 객체, globals, 기본값, 클로저를 묶어 함수 객체를 만든다. 기본값은 이 순간 한 번 평가되어 `__defaults__` 에 저장된다. 이것이 가변 기본값 버그의 정체다. 관용구는 `None` sentinel 이고, `None` 도 유효한 값이라면 전용 sentinel 을 쓴다(3.15 내장 `sentinel`, PEP 661. 로컬 확인).
- 스코프는 LEGB 다. 이름이 지역인지는 **컴파일할 때** 정해진다(함수 어디서든 대입하면 지역). 그래서 `UnboundLocalError` 가 난다. dis 로 보면 `LOAD_FAST`(지역), `LOAD_GLOBAL`, `LOAD_DEREF`(클로저)로 갈린다. `global`, `nonlocal`.
- 클로저는 cell 객체(`__closure__`, `cell_contents`)로 **변수를 공유**한다(late binding). Java 람다는 effectively final 인 **값**을 캡처하므로 이 버그가 원천 차단된다. Java 개발자가 가장 놀라는 지점이다. 해결책은 기본값 바인딩(`i=i`), `functools.partial`, 팩토리 함수다.
- 인자 모델: 위치/키워드, `*args`/`**kwargs`, 키워드 전용(`*`), 위치 전용(`/`). 오버로딩이 없으므로 기본값, 키워드, `functools.singledispatch` 로 대신한다.
- 데코레이터는 `f = deco(f)` 의 문법 설탕이다. **정의할 때(import 할 때) 실행**된다. `functools.wraps` 가 없으면 `__name__`/`__doc__`/`__wrapped__`/시그니처를 잃어서 로깅, pytest, inspect 가 헷갈린다. 인자를 받는 데코레이터는 3단 중첩이다.
- `functools.cache`/`lru_cache`: 인자가 hashable 이어야 한다. 메서드에 쓰면 캐시가 `self` 를 붙잡아 인스턴스가 해제되지 않는다(09장과 연결). `cache_info()`.
- 언제 무엇을: 상태 하나면 클로저, 상태 여럿과 메서드면 클래스, 횡단 관심사면 데코레이터. static 유틸 클래스 대신 모듈 함수.

**Java 습관 함정**: 오버로딩을 흉내 내려고 타입 검사 분기를 쓴다. 람다 캡처를 값 캡처로 가정한다. 모든 것을 클래스로 감싼다.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_def_is_runtime.py` | 조건문 안의 def, 함수 속성, 가변 기본값 재현과 수정 | `f.__defaults__` 변화 |
| `ex02_scope_bytecode.py` | `UnboundLocalError` 재현과 원인 | dis 의 `LOAD_FAST`/`LOAD_GLOBAL` 판정 |
| `ex03_closure_cells.py` | 두 클로저가 같은 cell 을 공유, 루프 late binding 과 세 가지 수정 | `__closure__[i].cell_contents`, `id(cell)` |
| `ex04_parameters.py` | `/`, `*`, 언패킹 호출 | `inspect.signature`, `TypeError` 메시지 |
| `ex05_decorators.py` | wraps 유무 비교, 데코레이터 실행 시점 | `__name__`, `__wrapped__`, import 시점 로그 |
| `ex06_lru_cache.py` | `cache_info`, unhashable 인자, 메서드 캐시가 인스턴스를 붙잡음 | `weakref` 생존 판정 |

| 실습 (`funcs.py`) | 테스트가 확인하는 것 |
|---|---|
| `make_counter()` (nonlocal) | 카운터끼리 독립적이다 |
| `retry(times, exceptions=(Exception,))`: 인자 있는 데코레이터 | 호출 횟수, 지정한 예외만 재시도, 마지막 예외 재발생, `__name__`/`__doc__`/`__wrapped__` 보존, `times<1` 이면 **데코레이트하는 시점**에 `ValueError` |
| `memoize(fn)` | kwargs 순서와 무관한 키, unhashable 인자는 캐시를 우회, `cache_clear` 제공, 원 함수 호출 횟수 |
| `make_multipliers(n)` | `[m(10) for m in make_multipliers(3)] == [0, 10, 20]` |
| `append_to(item, target=<sentinel>)` | 호출 간 공유가 없다. `None` 을 명시적으로 넘기면 원소로 추가된다(그래서 sentinel 이 필요하다) |
