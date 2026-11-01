# 13. 동시성 I: GIL·free-threading·스레드·프로세스 · 5h · 선행 08, 09

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: Python 스레드는 왜 CPU 작업을 빠르게 하지 못했나? free-threaded 빌드에서는 무엇이 달라지고 무엇이 그대로인가? 스레드, 프로세스, 인터프리터, asyncio 가운데 언제 무엇을 고르나?

**README**
- **GIL**: 한 번에 한 스레드만 바이트코드를 실행한다. I/O, `time.sleep`, 그리고 많은 C 확장(zlib, hashlib 등)은 GIL 을 놓으므로 I/O 병렬성은 있다. 스위치 간격은 5ms(`sys.getswitchinterval()`).
- **GIL 은 사용자 코드의 원자성을 보장하지 않는다.** `x += 1` 은 여러 opcode(로드, `BINARY_OP`, 저장)로 이루어진다(dis). 그런데 GIL 빌드에서는 실행이 끊기는 지점의 특성상 경쟁이 거의 재현되지 않는다(로컬 3.15.2, 스레드 4개 × `+=` 50만 회에서 손실 0회). "안 보인다"는 "안전하다"가 아니다. free-threaded 빌드에서는 재현된다.
- **Java 대비**: Java 스레드는 진짜 병렬이고 JMM(happens-before, `volatile`, `synchronized`)이 정교하게 문서화돼 있다. Python 은 GIL 빌드에선 병렬이 아니고 free-threaded 빌드에선 병렬이다. 공유 상태는 `Lock`/`Queue` 로만 다룬다는 원칙이 더 단순하고 안전하다. `concurrent.futures` 는 Java `ExecutorService`/`Future` 와 거의 같은 모양이다.
- **free-threaded 빌드의 현재 상태(3.15)**:
  - 3.13 PEP 703(실험)을 거쳐 3.14 PEP 779 에서 공식 지원(phase II)이 됐다. 3.15 에서도 기본 빌드는 GIL 이고 free-threaded 는 별도 빌드다. 기본 빌드로 바꾸는 phase III 는 미정이다.
  - 3.15 의 python.org macOS 설치본은 free-threading 을 기본으로 함께 설치한다. PEP 803 으로 free-threaded 용 stable ABI(`abi3t`)가 생겼다.
  - 단일 스레드 오버헤드는 macOS aarch64 약 1%, x86-64 Linux 약 8% 다(3.15 HOWTO).
  - 식별은 `sysconfig.get_config_var("Py_GIL_DISABLED")`(빌드)와 `sys._is_gil_enabled()`(실행 중)로 한다. free-threading 을 선언하지 않은 C 확장을 import 하면 경고와 함께 GIL 이 다시 켜진다. `PYTHON_GIL=0|1`, `-X gil`.
  - 설치는 `pyenv install 3.15.2t`(pyenv 2.8.14 목록에서 확인) 또는 `brew install python-freethreading`(3.15.2 bottle 확인).
- free-threaded 에서도 그대로인 것: 내장 컨테이너는 내부 락으로 **개별 연산**만 보호한다. check-then-act 같은 복합 연산과 같은 iterator 의 동시 소비는 보호하지 않는다(3.15 `threading.serialize_iterator`/`synchronized_iterator`/`concurrent_tee`).
- `concurrent.futures`: `ThreadPoolExecutor`/`ProcessPoolExecutor`/`InterpreterPoolExecutor`(3.14). 예외는 `result()` 를 부를 때 다시 던져진다. 결과를 꺼내지 않으면 **조용히 묻힌다**. `as_completed`.
- **multiprocessing**: 별도 프로세스다. 인자와 결과는 pickle 로 직렬화되므로 비용이 들고, 람다나 지역 함수는 넘길 수 없다. 시작 방식은 macOS/Windows 가 `spawn` 이다. spawn 은 main 모듈을 다시 import 하므로 **`if __name__ == "__main__":` 가드가 필수**다. 로컬에서 가드 없는 모듈 최상위 코드가 워커 수만큼 다시 실행되는 것을 확인했다. Linux 는 3.14 부터 `forkserver` 가 기본이다(그 전은 `fork`). 그래서 플랫폼마다 동작이 다를 수 있다.
- 서브인터프리터: `concurrent.interpreters`(PEP 734, 3.14)는 인터프리터마다 GIL 이 따로 있고 객체를 공유하지 않는다(큐로 전달). 실무 채택은 초기 단계라 개념만 다룬다.
- **선택 가이드**

| 작업 | GIL 빌드 | free-threaded 빌드 |
|---|---|---|
| 동시 I/O 가 많고 라이브러리가 async | asyncio(14) | 같음 |
| 동시 I/O, 라이브러리가 동기 | `ThreadPoolExecutor` | 같음 |
| CPU 병렬(순수 Python) | `ProcessPoolExecutor`(직렬화 비용 고려) | 스레드(의존 C 확장이 FT 를 지원할 때) |
| CPU 병렬(numpy 등 GIL 을 놓는 라이브러리) | 스레드 | 스레드 |

**Java 습관 함정**: 스레드면 CPU 병렬이라고 생각한다, `synchronized` 처럼 메서드 단위 락을 기대한다, `volatile` 을 찾는다, `Future` 결과를 확인하지 않는다, `__main__` 가드를 빠뜨린다.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_plus_equals_not_atomic.py` | `+=` 는 여러 opcode 다. `v = c.n; time.sleep(0); c.n = v + 1` 로 강제 인터리빙해 lost update 를 결정론적으로 재현하고 Lock 으로 해결 | dis 판정, `결과 < 기대: True`, Lock 판의 정확성 |
| `ex02_gil_cpu_vs_io.py` | CPU 작업은 스레드 4개로 해도 직렬과 비슷하고, sleep(I/O) 작업은 약 1/4 로 준다 | `sys._is_gil_enabled()` 출력, I/O 판정(넉넉한 경계), CPU 시간은 `...` |
| `ex03_free_threading_probe.py` | 빌드 식별 세 가지. `.venv-ft` 로 ex02 를 다시 돌려 비교하는 과제 | 출력은 `...`, README 에 실행 방법 |
| `ex04_spawn_reimport.py` | 가드 없는 모듈 최상위 코드가 워커 수만큼 다시 실행된다 | spawn 컨텍스트를 명시해 서브프로세스 실행, 로그 횟수 판정 |
| `ex05_executor_exceptions.py` | Future 의 예외는 `result()` 에서 재발생하고, 꺼내지 않으면 조용하다 | 출력 |
| `ex06_pickle_boundary.py` | ProcessPool 에 람다를 넘기면 PicklingError, 큰 인자 직렬화 비용 | 예외 타입 판정, 시간은 `...` |

| 실습 (`concurrency.py`) | 테스트가 확인하는 것 |
|---|---|
| `Bank.transfer(a, b, amount)` (계좌별 락, 락 순서로 데드락 회피) | 스레드 수천 개가 교차 이체해도 총액 보존, 타임아웃 안에 완료(데드락 없음) |
| `parallel_map(fn, items, workers, kind="thread"\|"process")` | 순서 보존, 첫 예외 전파(원인 포함), process 모드에서 모듈 수준 함수 |
| `run_pipeline(source, worker, n_workers)` (`queue.Queue` + 종료 sentinel) | 모든 항목을 정확히 한 번 처리, 워커 예외가 나면 전체 중단과 전파, join 데드락 없음 |
| (선택, 테스트 아님) FT 빌드 벤치마크 | `.venv-ft` 에서 CPU 작업을 스레드로 돌린 속도 향상을 기록 |
