# 12. pytest 실무 · 3h · 선행 10 (11 권장)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: pytest 는 무엇을 어떻게 찾아 실행하나? mock 은 어디를 patch 해야 하나? 무엇을 mock 하지 말아야 하나?

**README**
- 수집 규칙: rootdir/configfile/testpaths, `test_*.py`, `Test*` 클래스, `test_*` 함수, conftest.py 계층. **import mode**: 기본 prepend 는 `__init__.py` 가 없는 테스트 디렉터리를 `sys.path` 앞에 넣는다. 그래서 테스트 파일 basename 이 유일해야 한다(pytest 문서). 이 저장소의 명명 규칙이 여기서 나온다(§5.3).
- assert 재작성: import hook 이 테스트 모듈의 AST 를 다시 써서 실패한 값을 보여 준다. JUnit 의 `assertEquals` 계열이 필요 없다.
- fixture 는 인자 이름으로 받는 의존성 주입이다. scope, yield fixture(teardown), `tmp_path`, `monkeypatch`, `capsys`, `caplog`. JUnit 의 `@BeforeEach`/`@TempDir` 와 대응시켜 본다.
- `parametrize`, `pytest.raises(match=)`, `pytest.approx`, marker, `-k`/`-x`/`--lf`.
- mock: `unittest.mock.patch` 는 **정의된 곳이 아니라 조회되는 곳**을 patch 한다(10장 `from import` 바인딩). `autospec=True` 로 시그니처를 검사한다. `Mock` 은 속성을 자동으로 만들어서 오타를 숨긴다(`assert_*` 계열 일부 오타만 막아 준다). mock 을 늘리기보다 의존성 주입과 가짜 객체를 쓴다(05 실습 `read_ints` 의 `opener` 주입처럼).
- doctest 는 문서 예제를 검증하는 용도다. 이 저장소가 예제 검증에 doctest 를 쓰지 않은 이유와 연결한다(§5.4).
- 속성 기반 테스트(hypothesis)는 소개만 한다(외부 의존 추가는 §7 결정 사항).

**Java 습관 함정**: 테스트 클래스와 setUp 상속 계층, private 메서드 테스트, 모든 협력 객체를 mock, `from x import y` 해 놓고 `x.y` 를 patch.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_assert_rewrite.py` | 실패한 assert 가 값을 보여 준다 | 임시 테스트 파일을 서브프로세스 pytest 로 돌려 출력 판정 |
| `ex02_fixture_order.py` | fixture setup/teardown 순서, scope | `pytest -s` 서브프로세스 로그 |
| `ex03_patch_where.py` | 잘못된 위치 patch(효과 없음)와 올바른 위치 | 결과 비교 |
| `ex04_mock_pitfalls.py` | 오타 속성 자동 생성, autospec 으로 차단 | 출력 |

| 실습 | 테스트가 확인하는 것 |
|---|---|
| 제공된 `weather_service.py`(HTTP 클라이언트, 시계, 캐시 의존)에 대한 **테스트를 사용자가 작성** (`test_weather_service.py` 의 빈 테스트 함수들) | 메타 테스트 `test_mutants.py` 가 사용자 테스트를 ① 정답 구현에서 실행해 전부 통과하는지 ② 미리 준비한 변이 구현 5개(캐시 만료 off-by-one, 예외 삼킴, 단위 변환 오류 등)에서 각각 **최소 하나는 실패하는지** 확인한다. 테스트 작성 능력을 자동으로 검증하는 방식 |
