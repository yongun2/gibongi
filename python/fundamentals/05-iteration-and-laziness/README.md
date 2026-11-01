# 05. 이터레이션 프로토콜과 지연 평가

> 선행: 03(내장 자료구조), 04(함수·클로저) · 예상 5시간 · 검증: `./check python 05`
> 기준 버전: CPython 3.15.2. 바이트코드 이름은 버전마다 바뀌므로 예제는 opcode의 **존재 여부**만 판정한다.

## 이 섹션이 답하는 질문

1. `for x in xs:` 는 무엇으로 펼쳐지나? Java의 `Iterator` 와 무엇이 다른가?
2. for+append, list comprehension, generator expression, map/filter 는 같은 결과를 내는데 **원소 하나당 무엇이 실행되나?** 그래서 언제 무엇을 쓰나?
3. 지연 평가는 어떤 버그를 만드나? (일회용 소진, 평가 시점, 닫힌 파일)

---

## 1. 반복 프로토콜은 메서드 두 개다

| 역할 | 메서드 | 의미 |
|---|---|---|
| iterable | `__iter__()` | 반복자를 돌려준다. `iter(x)` 가 부른다 |
| iterator | `__next__()` | 다음 값을 돌려주고, 끝나면 `StopIteration` 을 던진다. 그리고 `iter(it) is it` |

`for` 문은 시작할 때 `GET_ITER` 를 한 번 실행하고, 원소마다 `FOR_ITER` 를 실행한다(ex01). `FOR_ITER` 는 `next()` 를 파이썬 함수처럼 **호출하지 않는다**. 반복자 타입의 C 슬롯(`tp_iternext`)을 직접 부른다. C로 만든 반복자는 끝을 알릴 때 예외 객체를 만들지도 않는다. 그래서 "예외로 루프를 끝낸다"는 설계가 실제로는 비싸지 않다. 3.11 이후의 특화(specializing) 인터프리터는 실행 중에 `FOR_ITER` 를 `FOR_ITER_LIST`, `FOR_ITER_RANGE`, `FOR_ITER_GEN` 같은 타입별 명령으로 바꿔 끼운다(15장에서 `dis.dis(f, adaptive=True)` 로 본다).

| | Java | Python |
|---|---|---|
| 프로토콜 | `iterator()`, `hasNext()`, `next()` | `__iter__`, `__next__` + `StopIteration` |
| 끝 신호 | `hasNext()` 가 false | `StopIteration` (C 반복자는 예외 객체도 만들지 않음) |
| 향상된 for 의 바이트코드 | 원소마다 `invokeinterface` 두 번 | 원소마다 `FOR_ITER` 명령 하나 |
| 일회용 객체를 다시 소비 | `Stream`: `IllegalStateException` | generator·map·zip·파일: **아무 일 없이 빈 결과** |

## 2. iterable 과 iterator 를 구분해야 하는 이유: 일회용

- 여러 번 돌 수 있는 것(iterable): `list`, `tuple`, `str`, `dict`, `set`, `range`, dict view(`d.items()`)
- 한 번만 돌 수 있는 것(iterator): generator, `map`, `filter`, `zip`, `enumerate`, `reversed`, 파일 객체, `csv.reader`, 대부분의 `itertools`

**실무 버그**: 인자를 두 번 순회하는 함수(합계를 구한 뒤 정규화)에 generator 를 넘기면 두 번째 순회가 비어서 결과가 **조용히** 틀린다(ex02의 `normalize`). Java Stream 은 재사용하면 예외가 나서 바로 드러나지만, Python 은 아무것도 알려 주지 않는다. Python 2나 Java 습관으로 `map(...)` 결과를 리스트처럼 다루는 것도 같은 함정이다.

방어법은 세 가지다.
1. 한 번만 순회하는 알고리즘으로 바꾼다(실습 `mean_and_stdev`).
2. 함수 입구에서 `values = list(values)` 로 구체화하고, 그 사실을 문서에 적는다.
3. 타입 힌트로 의도를 드러낸다. `Iterable[T]` 를 받으면 "한 번만 돈다"는 약속이고, 여러 번 돌아야 하면 `Sequence[T]`·`Collection[T]` 를 요구한다(11장).

## 3. 네 가지 방법의 내부: 원소 하나당 무엇이 실행되나 (ex03, ex06)

| 방법 | 만들어지는 객체 | 원소마다 실행되는 것 | 추가 메모리 |
|---|---|---|---|
| for + `out.append(...)` | 결과 list | `FOR_ITER`, `append` 속성 조회(`LOAD_ATTR`), 메서드 호출(`CALL`) | 결과 전체 |
| list comprehension | 결과 list. 3.12부터 함수 객체를 만들지 않음 | `FOR_ITER`, 전용 명령 `LIST_APPEND`. 속성 조회와 호출이 없다 | 결과 전체 |
| generator expression | code 객체, 함수, generator 객체(프레임 포함) | 소비자의 `next` → 프레임 재개(`RESUME`) → 계산 → `YIELD_VALUE` → 프레임 정지 | 프레임 하나 |
| `map(f, xs)` / `filter` | C 반복자 객체 | `f` 가 C 함수(`str`, `int`, `operator.*`)면 프레임이 생기지 않는다. `lambda` 면 원소마다 파이썬 함수 호출(프레임 생성) | 상수 |

- **PEP 709 (3.12)**: 그 전까지는 list comprehension 도 숨은 함수였다(`MAKE_FUNCTION` + `CALL`). 3.12부터는 바깥 함수 안에 인라인되고, ex03에서 "중첩 code 없음"으로 확인할 수 있다. generator expression 은 "멈췄다가 다시 시작하는 것"이 본질이라 인라인될 수 없다.
- **3.15.2에서 관찰한 것**: `list(genexpr)`·`any(genexpr)` 처럼 내장 함수에 genexpr 를 바로 넘기면, 컴파일러는 그 이름이 진짜 내장 함수인지 실행 중에 확인(`LOAD_COMMON_CONSTANT` + `IS_OP`)한다. 맞으면 내장 함수를 호출하는 대신 바깥 함수에서 직접 `FOR_ITER`/`LIST_APPEND` 로 소비하는 코드를 만든다. 그래서 ex03의 genexpr 줄은 `LIST_APPEND` 를 `...` 로 비워 두었다. 이 동작을 설명하는 What's New 항목은 찾지 못했다(**미확인**). 어느 쪽이든 generator 객체 생성과 원소마다의 `YIELD_VALUE`/`RESUME` 왕복은 그대로 남는다.
- 작성자 환경(Apple Silicon, 3.15.2)에서 ex06으로 잰 상대 시간(listcomp = 1.0)은 다음과 같았다. 식 `x * 2` 기준으로 for+append 1.3, `list(genexpr)` 1.45, `map(lambda)` 2.2. `str(x)` 기준으로 `map(str, xs)` 0.95. 직접 돌려서 위 표의 "원소마다 실행되는 것"과 순서가 맞는지 확인하라.

### Kotlin 과 견주기

| Kotlin | Python | 차이 |
|---|---|---|
| `xs.filter{}.map{}` (eager, 단계마다 중간 `List`) | comprehension 두 개를 이어 쓴 것 | 같다 |
| `xs.asSequence().filter{}.map{}` (lazy, 원소 단위) | generator 파이프라인, `map`/`filter` 체인 | 같다 (ex04: 실행 순서가 원소별로 번갈아 나온다) |
| `inline` 람다(호출 비용 0) | comprehension 안의 **식** | Python 의 `lambda` 는 진짜 함수 호출이다. 그래서 `map(lambda)` 가 가장 느리다 |
| eager 체인은 단계 수만큼 할당 | `[f(x) for x in xs if p(x)]` 는 **루프 한 번에 할당 한 번** | Python comprehension 은 filter 와 map 을 한 루프로 처리한다(ex04 `1-pass`) |

## 4. 메모리 (ex05)

- list 결과는 포인터 배열(원소당 8B, 성장에 대비한 여분 할당 포함)과 원소 객체로 이루어진다. 제곱한 int 100만 개면 peak 가 약 40MB다(int 객체 약 32B + 포인터 8B).
- generator 는 프레임 하나만 들고 있다. `sys.getsizeof(genexpr)` 는 N 과 상관없이 같다(작성자 환경에서 216B).
- `sys.getsizeof(list)` 는 **얕은 크기**다. 원소 객체는 세지 않는다(03·09장).
- `sum([x*x for x in xs])` 처럼 결과 리스트를 바로 집계에 넘기면 리스트를 만들 이유가 없다. `sum(x*x for x in xs)` 로 쓴다. genexpr 가 함수 호출의 유일한 인자일 때는 괄호를 생략할 수 있다.
- 다만 N 이 작으면 원소당 오버헤드 때문에 listcomp 가 더 빠를 수 있다. 판단 기준은 세 가지다. 결과가 메모리에 부담이 되는가? 여러 번 쓰는가? 일부만 쓰는가?

## 5. 지연 평가의 함정 (ex07)

1. **평가 시점**: genexpr 는 맨 앞 `for` 의 iterable 만 만드는 순간 평가하고, 조건과 식은 소비할 때 평가한다. 조건에 쓴 바깥 변수를 나중에 바꾸면 결과가 바뀐다. 04장의 late binding 과 같은 원리다(이름을 나중에 조회한다).
2. **닫힌 파일**: `with open(...) as f: return (… for line in f)` 를 쓰면, 소비하는 시점에는 파일이 이미 닫혀 있다. 해결책은 generator **함수** 안에 `with` 를 두는 것이다. 그러면 파일은 소비가 끝나거나 `close()` 가 불릴 때 닫힌다. 일부만 소비하고 버리면 어떻게 될까? CPython 은 generator 가 해제되는 순간(대개 즉시, 참조 카운팅 덕분) `close()` 를 불러 `finally` 를 실행한다. 다른 구현이나 순환 참조에서는 늦어질 수 있으니, 확실히 하려면 `contextlib.closing(gen)` 을 쓰거나 끝까지 소비한다.
3. **예외 위치**: 예외는 소비하는 지점에서 터진다. 트레이스백이 generator 를 만든 곳이 아니라 소비한 곳을 가리켜서 디버깅이 헷갈린다.
4. **인자 검증 지연**: generator 함수 본문은 첫 `next()` 전까지 한 줄도 실행되지 않는다. 그래서 본문 첫 줄의 `if size < 1: raise ValueError` 는 호출할 때가 아니라 소비할 때 터진다(실습 `chunked`).
5. **동시 소비**: 같은 iterator 를 여러 스레드가 동시에 소비하면 안 된다(13장, 3.15의 `threading.serialize_iterator`).

## 6. generator 는 "멈춰 있는 프레임"이다 (ex08)

- generator 함수를 호출하면 본문은 실행되지 않고 generator 객체(프레임 + 실행 위치)만 생긴다. 바이트코드 첫머리에 `RETURN_GENERATOR` 가 있다.
- 상태는 `GEN_CREATED` → `GEN_RUNNING` → `GEN_SUSPENDED` → `GEN_CLOSED` 로 바뀐다(`inspect.getgeneratorstate`).
- `return v` 는 `StopIteration(v)` 가 되고, `yield from` 이 그 값을 받는다.
- `close()` 는 멈춘 `yield` 자리에 `GeneratorExit` 를 던진다. 그래서 `finally`/`with` 정리 코드가 실행된다.
- Java 로 같은 일을 하려면 상태 필드를 가진 `Iterator` 클래스를 직접 짜야 한다. 실습에서 `Countdown` 클래스와 generator 함수를 비교해 보라.
- `send()`/`throw()` 를 직접 쓸 일은 드물다. 14장 asyncio 코루틴이 바로 이 메커니즘(`yield` → `await`)의 확장이라는 점만 기억한다.

## 7. itertools: 바퀴를 다시 만들지 말 것

`islice`(무한 반복자 자르기), `chain.from_iterable`(3.15부터는 `[*xs for xs in lists]` 도 가능, PEP 798), `groupby`(**연속 구간만** 묶으므로 먼저 정렬한다), `tee`(두 소비자의 속도 차이만큼 버퍼에 쌓이므로 그럴 바엔 list), `batched`(3.12, `strict=` 는 3.13), `pairwise`(3.10), `accumulate`, 그리고 `zip(..., strict=True)`(3.10, 길이가 달라 조용히 잘리는 것을 막는다).

## 8. 언제 무엇을 쓰나

| 상황 | 선택 | 이유 |
|---|---|---|
| 결과 리스트가 필요하고, 변환과 필터가 식 하나 | list comprehension | 가장 빠르고 루프가 한 번 |
| 집계(`sum`/`max`/`any`/`all`/`"".join`/`dict()`)로 바로 소비 | generator expression | 결과 리스트가 필요 없다. `any`/`all` 은 단락 평가 |
| 크거나 무한하거나 앞부분만 쓰거나 스트림(파일·소켓)일 때 | generator 파이프라인, generator 함수 | 메모리 상수, 조기 종료 |
| 이미 있는 C 함수를 적용 | `map(str, xs)` | 원소마다 프레임을 만들지 않는다 |
| `lambda` 가 필요해지면 | comprehension 으로 바꾼다 | `map(lambda)` 는 가장 느리고 읽기도 어렵다 |
| 문장이 여럿이거나 `try`·`break`·부수효과가 있을 때 | `for` 문(또는 generator 함수) | comprehension 안에 부수효과를 넣지 않는다 |
| 여러 번 순회하거나 `len`/인덱스가 필요할 때 | `list`/`tuple` 로 구체화 | 일회용 함정을 피한다 |
| 상태가 있는 반복 로직 | generator 함수 | Iterator 클래스보다 짧고, 정리(`finally`)가 보장된다 |

## 9. 흔한 함정 체크리스트

- [ ] generator 를 두 번 순회해서 두 번째가 비었다(예외 없음)
- [ ] `map`/`filter`/`zip` 결과를 리스트로 착각했다
- [ ] `with` 블록 안에서 genexpr 를 return 했다
- [ ] generator 함수의 인자 검증이 첫 `next()` 까지 미뤄졌다
- [ ] comprehension 을 부수효과용으로 썼다(`[print(x) for x in xs]`). 이럴 땐 for 문을 쓴다
- [ ] 순회하는 도중에 컬렉션을 바꿨다(dict 는 `RuntimeError`, list 는 원소를 조용히 건너뜀. 03장)
- [ ] `zip` 의 길이가 달라 조용히 잘렸다. `strict=True` 를 쓴다
- [ ] 정렬하지 않고 `groupby` 를 썼다

## 예제 (`examples/`, 각 파일을 `python exNN_*.py` 로 실행)

| 파일 | 보여주는 것 | 관찰 방법 |
|---|---|---|
| `ex01_for_protocol.py` | for 문 = iter/next/StopIteration, `FOR_ITER` 는 호출이 아니다 | `dis.get_instructions`, 손으로 펼친 while 문과 비교 |
| `ex02_one_shot.py` | iterable 과 iterator, 두 번째 순회가 조용히 빈 결과가 되는 버그 | `iter(x) is x`, 두 번 소비 |
| `ex03_bytecode.py` | 네 가지 방법의 원소당 명령 차이, PEP 709 인라인 | `dis`, `co_consts` 의 중첩 code 객체. `-v` 로 전체 dis 출력 |
| `ex04_pipeline_order.py` | eager 와 lazy 의 실행 순서, 조기 종료 시 당긴 원소 수 (Kotlin List/Sequence 대비) | 실행 로그, 카운터 |
| `ex05_memory.py` | listcomp 와 genexpr, 루프의 peak 메모리, getsizeof 의 얕은 크기 | `tracemalloc`, `sys.getsizeof` (숫자는 stderr) |
| `ex06_speed.py` | 원소당 오버헤드가 시간으로 나타나는 모습 | `timeit` (숫자는 검증하지 않음) |
| `ex07_lazy_traps.py` | 평가 시점, 닫힌 파일, 예외 위치 | 실행 결과 |
| `ex08_generator_lifecycle.py` | generator 상태, `finally`, `StopIteration.value`, `yield from` | `inspect.getgeneratorstate` |

## 실습 (`exercises/lazy_tools.py`)

| 구현할 것 | 테스트가 확인하는 것 |
|---|---|
| `Countdown` (iterator 클래스) | `iter(c) is c`, 한 번 소진되면 끝, `zip(c, c)` 가 상태를 공유한다 |
| `CountdownRange` (iterable 클래스) | 여러 번 순회할 수 있고, `zip(r, r)` 의 두 반복자가 서로 독립적이다. `Iterable` 이지만 `Iterator` 는 아니다 |
| `chunked(iterable, size)` | 묶음 결과, 호출하는 즉시 `ValueError`, 필요한 만큼만 당긴다, 무한 입력 |
| `flatten(items)` | 임의 깊이 평탄화, str/bytes 는 쪼개지 않는다, 지연 |
| `mean_and_stdev(numbers)` | `__iter__` 를 한 번만 부른다, 큰 값 주변의 수치 안정성(Welford), peak 메모리 256KB 미만 |
| `read_ints(path, opener)` | 호출 시점엔 파일을 열지 않는다, 끝까지 소비하거나 `close()` 하면 파일이 닫힌다 |

## 버전 노트

- 3.12: PEP 709 comprehension 인라인, `itertools.batched`
- 3.13: `batched(strict=)`
- 3.14: `LOAD_FAST_BORROW`, `LOAD_SMALL_INT`, `POP_ITER` 같은 opcode 변경. 그래서 예제는 opcode 이름 전체를 비교하지 않는다
- 3.15: PEP 798 comprehension 언패킹(`[*xs for xs in lists]`), `threading.serialize_iterator`/`synchronized_iterator`/`concurrent_tee`. `list(genexpr)` 인라인 소비는 관찰만 했다(문서 미확인)
