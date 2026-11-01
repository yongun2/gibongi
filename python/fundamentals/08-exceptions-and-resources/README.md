# 08. 예외, 컨텍스트 매니저, 로깅 · 4h · 선행 05, 06

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: checked exception 이 없는 언어에서 실패는 어떻게 설계하나? 예외는 비싼가? `with` 는 무엇으로 펼쳐지나?

**README**
- 계층: `BaseException`(`SystemExit`, `KeyboardInterrupt`, `GeneratorExit`, asyncio `CancelledError`)과 `Exception`. bare `except:` 나 `except BaseException` 은 Ctrl-C 와 asyncio 취소까지 삼킨다. PEP 758(3.14) 부터는 `as` 를 쓰지 않을 때 `except A, B:` 를 괄호 없이 쓸 수 있다. 모양이 Python 2 의 `except A, e` 와 같지만 이제 둘 다 예외 타입이다.
- 비용: 3.11 이후 try 는 **제로 비용**이다. dis 에 `SETUP_*` 명령이 없고 `ExceptionTable` 이 있다. 정상 경로는 거의 공짜이고 raise 는 비싸다. 그래서 EAFP 는 예외가 **드물 때** 싸다(`dict.get` 과 try/KeyError 를 `timeit` 으로 비교).
- 체이닝: 암묵적 `__context__`("During handling…"), 명시적 `raise X from e`(`__cause__`), `from None`. 트레이스백은 아래가 가장 최근이다. `add_note()`(3.11).
- `except E as e` 의 `e` 는 **블록이 끝나면 삭제된다**(로컬 dis: `DELETE_FAST`). 트레이스백 → 프레임 → 지역 변수로 이어지는 순환 참조를 막기 위해서다(09장과 연결).
- `finally` 안에서 `return`/`break`/`continue` 를 쓰면 예외를 삼킨다. 3.14부터 `SyntaxWarning`(PEP 765).
- `ExceptionGroup`/`except*`(3.11): 동시 실패를 묶어 다룬다(14장 TaskGroup).
- 예외 설계: 라이브러리마다 기반 예외 하나 + 구체 예외를 둔다. 경계에서 `from` 으로 변환한다. Java 의 throws 선언은 docstring 과 경계 변환으로 대신한다.
- 컨텍스트 매니저: `__enter__`/`__exit__(type, value, tb)` 를 거쳐 실행되고, `__exit__` 가 참을 돌려주면 예외가 억제된다. `contextlib.contextmanager` 는 generator 기반이라 예외가 `yield` 지점으로 던져진다(try/finally 필수). `ExitStack`, `suppress`, `closing`. Java try-with-resources 와 목적은 같지만 Python 의 것은 프로토콜이라 락, 트랜잭션, 임시 상태 변경 등 무엇이든 감쌀 수 있다.
- 리소스 정리를 GC 에 맡기지 않는다. CPython 은 refcount 로 즉시 닫지만 PyPy, 순환 참조, free-threaded 빌드의 지연 refcount 에서는 늦어진다. `-X dev` 로 `ResourceWarning` 을 관찰한다.
- 로깅: `logging.getLogger(__name__)` 계층, 라이브러리는 핸들러를 설정하지 않는다, `logger.exception()` 은 트레이스백을 포함한다, `logger.info("x=%s", x)` 는 포매팅을 미룬다(f-string 은 즉시 포매팅), `basicConfig` 는 처음 한 번만 효과가 있다.

**Java 습관 함정**: `except Exception: pass`, 예외로 흐름 제어를 남용(또는 반대로 과도한 LBYL), throws 를 흉내 낸 거대한 예외 계층, `finally` 에서 close.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_zero_cost_try.py` | 제로 비용 try, raise 비용 | dis 에 `SETUP_` 이 없고 예외 테이블이 있다는 판정, `timeit`(`...`) |
| `ex02_chaining.py` | `__context__`/`__cause__`/`__suppress_context__` | `traceback.format_exception` 마지막 줄들 |
| `ex03_except_name_deleted.py` | 블록 밖에서 `e` 가 `NameError` | dis `DELETE_FAST` 판정 |
| `ex04_finally_swallow.py` | finally 의 return 이 예외를 삼킨다 | `compile` 시 `SyntaxWarning` 캡처 |
| `ex05_with_protocol.py` | `__enter__`/`__exit__` 호출 로그, 억제 여부, contextmanager 판도 같은 로그 | 로그 |
| `ex06_exception_group.py` | `except*` 로 나눠 처리 | 출력 |
| `ex07_logging.py` | 로거 계층과 전파, `exception()` | stdout 핸들러와 고정 포맷 |

| 실습 (`errors.py`) | 테스트가 확인하는 것 |
|---|---|
| `ConfigError` 계층 + `load_config(text)` | 파싱 오류를 `ConfigError` 로 변환(`__cause__`), 메시지에 줄 번호 |
| `Timer(clock=time.perf_counter)` 클래스형 CM | 예외가 나도 `elapsed` 를 기록하고 예외는 억제하지 않는다. 시계를 주입해 결정론적으로 테스트 |
| `temporary_attr(obj, name, value)` (`@contextmanager`) | 정상 종료와 예외 종료 모두 원복, 원래 없던 속성은 삭제 |
| `rollback_on_error(d: dict)` | 블록 안에서 바꾼 것이 예외가 나면 롤백된다 |
| `run_all(funcs)` | 모두 실행한 뒤 실패를 `ExceptionGroup` 으로 묶는다. `group.subgroup` |
