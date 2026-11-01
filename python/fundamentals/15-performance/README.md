# 15. 성능 판단 · 3h · 선행 03, 05, 13

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: "Python 은 느리다"를 숫자로 어떻게 판단하나? 어디부터 고치나?

**README**
- 측정 원칙: `timeit` 은 repeat 의 최솟값을 쓰고 측정 중에는 gc 를 끈다. `perf_counter`. 마이크로벤치마크 함정으로는 상수 폴딩, 워밍업(3.11+ 특화 인터프리터가 실행 중에 명령을 바꿔 끼운다. `dis.dis(f, adaptive=True)` 로 `BINARY_OP_ADD_INT` 같은 특화 명령을 관찰)이 있다.
- 프로파일러: 결정론적 `cProfile`(3.15 에서 `profiling.tracing` 으로 이동, `cProfile` 은 별칭으로 남음)은 오버헤드가 커서 호출이 많은 코드를 왜곡한다. 3.15 의 Tachyon 은 `python -m profiling.sampling run script.py`, `attach PID`, `--live` 를 지원하는 저오버헤드 샘플링 프로파일러로 flamegraph 를 출력한다(로컬 CLI 확인). `-X importtime` 은 시작 시간, `tracemalloc` 은 메모리를 본다.
- 비용 모델 요약: 함수 호출(프레임 생성), 속성 조회, 전역과 지역 이름 조회(3.11+ 캐시로 차이가 줄었다. "전역을 지역 변수에 캐싱"하는 옛 관용구의 효과를 직접 재 본다), 루프를 C 로 밀어 넣기(`sum`, `join`, `sorted`, set 연산), 그리고 가장 큰 변수인 알고리즘과 자료구조 선택(03).
- JIT(3.13+ 실험적): 3.15 에서 크게 개선됐다(What's New: AArch64 macOS 에서 11~12%). 빌드 옵션이고, 로컬 Homebrew 3.15.2 빌드에는 들어 있지 않다(`sys._jit.is_available()` False). 커리큘럼에서는 존재만 알린다.
- 탈출구: numpy 벡터화, C 확장, Cython, Rust(PyO3), PyPy 는 이름만 소개한다.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_timeit_pitfalls.py` | 상수 폴딩 때문에 0에 가까운 측정, setup 분리 | dis `LOAD_CONST` 판정, 시간은 `...` |
| `ex02_specialization.py` | 워밍업 전후의 특화 opcode | `dis.get_instructions(f, adaptive=True)` 에 `_INT` 계열이 나타나는지 판정 |
| `ex03_profile_hotspot.py` | 의도한 핫스팟이 cProfile cumulative 상위에 오는지 | `pstats` 판정, Tachyon 사용법은 README |
| `ex04_push_loops_to_c.py` | 파이썬 루프와 `sum`/`map`/`join`/set 연산 비교 | `timeit`(`...`) |

| 실습 (`perf.py`) | 테스트가 확인하는 것 |
|---|---|
| 제공된 느린 `slow_report(records)` (O(n²) 리스트 탐색, 문자열 누적, 반복 정규식 컴파일, 불필요한 중간 리스트)를 **같은 결과로** 빠르게 만든 `fast_report` | 고정 시드 데이터에서 결과 동일, `slow` 대비 **5배 이상** 빠름(상대 비교라 기계와 무관). README 체크리스트에 프로파일러로 찾은 핫스팟을 기록 |
