# 11. 타입 힌트와 정적 검사 · 4h · 선행 07, 10

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: 런타임이 무시하는 타입 힌트는 무엇을 보장하나? Java 제네릭과 어디까지 같고 어디서 다른가? 실무에서는 어디까지 타입을 다나?

**범위 판단**
- **넣는다**: 함수 시그니처, 컬렉션 제네릭, `X | None`, 좁히기(isinstance, `is None`, `TypeIs`), PEP 695 제네릭 문법(`def f[T]`, `class Box[T]`, `type Alias = …`), `Protocol`, `TypedDict`, `Callable`, `Self`, `Final`, `@overload`(읽을 수 있을 정도로), 데코레이터 타이핑에 `ParamSpec` 한 번, 변성은 "왜 `list[int]` 를 `list[float]` 자리에 넘길 수 없나"까지.
- **뺀다**: 변성 설계, `TypeVarTuple` 심화, 체커 플러그인, 3.15 `TypeForm`(PEP 747)·TypedDict `closed`/`extra_items`(PEP 728)·`disjoint_base`(PEP 800)는 "읽을 수 있음" 수준으로 언급만.

**README**
- 힌트는 런타임에 **강제되지 않는다**. `def f(x: int)` 에 str 을 넘겨도 실행된다. `typing.cast` 는 아무것도 하지 않는다. 런타임 검증이 필요하면 경계(입력 파싱)에서 직접 하거나 pydantic 같은 라이브러리를 쓴다.
- 어노테이션 평가 시점: 3.14 부터 지연 평가(PEP 649/749)된다. 어노테이션은 `__annotate__` 함수에 저장되고 접근할 때 평가된다. 그래서 전방 참조에 따옴표나 `from __future__ import annotations` 가 필요 없다. 런타임에 읽을 때는 `annotationlib.get_annotations(obj, format=…)` 를 쓴다.
- Java 대비:
  - Java 는 명목적 타입만 있지만 Python 은 명목적(클래스)과 구조적(`Protocol`)을 둘 다 쓴다.
  - Java 제네릭은 소거되어도 컴파일러가 강제한다. Python 은 체커를 돌리지 않으면 아무 검사도 없다.
  - `list[int]` 는 불변(invariant)이다. Java `List<? extends Number>` 에 해당하는 관용구는 읽기 전용 추상 타입 `Sequence[float]`/`Mapping` 으로 받는 것이다.
  - `Any` 는 Java `Object` 가 아니라 양방향 탈출구다. Java `Object` 에 해당하는 것은 `object` 다.
- 체커마다 다른 점(실무에서 체감하는 것): mypy 는 기본 설정에서 **어노테이션이 없는 함수의 본문을 검사하지 않는다**(`check_untyped_defs`, `--strict`). pyright 는 추론해서 검사한다. 그래서 같은 코드에 다른 결론이 나온다.
- 2026년 도구 지형(3자 비교 자료 기준):
  - mypy 2.x: 병렬 검사(실험적)
  - pyright: VS Code Pylance 엔진, 타이핑 스펙 적합성이 가장 높다
  - pyrefly(Meta): 2026년 5월 1.0
  - ty(Astral): 베타

  저장소 표준은 **mypy --strict** 로 한다. pip 만으로 설치할 수 있고, mypy 2.4.1 이 3.15 문법(`lazy import`, comprehension 언패킹, `frozendict`)을 오류 없이 처리하는 것을 로컬에서 확인했다. 에디터는 Pylance 를 써도 된다.
- 점진적으로 도입한다: 공개 API 경계부터, `reveal_type`, 오류 코드를 지정한 `# type: ignore[code]`.

**Java 습관 함정**: 모든 지역 변수에 타입을 단다(추론에 맡긴다). 힌트가 런타임 검사를 해 주리라 믿는다. 추상 클래스 계층으로 타입을 표현한다(Protocol 이 더 가볍다). `Optional` 을 반환하고 호출부에서 처리하지 않는다.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_hints_not_enforced.py` | 잘못된 타입으로도 실행된다, 지연 어노테이션(정의할 때 없던 이름 참조 가능, `annotationlib` FORWARDREF 형식) | 출력 |
| `ex02_runtime_vs_checker.py` | 같은 파일을 실행하면 성공하고 mypy 는 오류를 낸다 | 서브프로세스로 `mypy` 실행, 오류 코드 줄 판정 |
| `ex03_variance.py` | `list[int]` 를 `list[float]` 자리에 넘기면 오류, `Sequence[float]` 는 통과 | mypy 출력 판정 |
| `ex04_protocol_structural.py` | 상속 없이 Protocol 을 만족한다, `runtime_checkable` 은 시그니처를 보지 않는다 | mypy, `isinstance` 결과 |
| `ex05_untyped_defs.py` | 어노테이션 없는 함수 안의 오류를 기본 mypy 는 놓치고 `--strict` 는 잡는다 | mypy 두 번 실행 비교 |

| 실습 | 테스트가 확인하는 것 |
|---|---|
| `typed_api.py`: 본문은 제공, **타입만 추가** | `mypy.api.run` 으로 `--strict` 통과를 확인하고, 제공된 `misuse_*.py` 각각에서 **기대한 오류 코드가 검출되는지**도 확인한다(너무 느슨한 `Any` 타입을 걸러낸다) |
| `LRUCache[K, V]` (03 재사용, PEP 695 문법) | mypy 통과, 키 타입이 틀린 사용이 검출된다 |
| `max_by[T](items, key: Callable[[T], SupportsLessThan])` (Protocol 정의 포함) | 런타임 결과와 mypy 통과 |
| `parse_config(raw: object) -> Config` (`TypedDict` + 좁히기) | 잘못된 입력은 `ValueError`(경로 포함 메시지), mypy 에서 `cast` 사용 금지(소스 검사) |
