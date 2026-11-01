# 10. 모듈·import·프로젝트 구성·가상환경 · 4h · 선행 04, 08

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `import x` 는 정확히 무엇을 하나? "내 컴퓨터에선 되는데"를 만드는 경로와 환경 문제는 어디서 오나?

**README**
- import 알고리즘:
  1. `sys.modules` 캐시를 확인한다
  2. `sys.meta_path` 의 finder(builtin, frozen, PathFinder)가 찾는다
  3. `sys.path` 를 순회한다
  4. loader 가 모듈 코드를 **실행**한다
  5. `sys.modules` 에 등록한다
  6. 이름을 바인딩한다

  모듈은 프로세스당 한 번 실행되므로 싱글턴 효과가 있다. Java 가 클래스 단위로 지연 로딩하는 것과 달리 모듈은 통째로 실행된다.
- `import a` 와 `from a import f`: 후자는 **그 순간의 객체를 내 이름공간에 바인딩**한다. 그 뒤에 `a.f` 를 바꿔도(monkeypatch) 보이지 않는다. 12장 "사용하는 곳을 patch 하라"의 근거다.
- `sys.path[0]` 은 스크립트 디렉터리(`-m` 이면 cwd)다. 그래서 `random.py`·`json.py` 같은 이름의 내 파일이 표준 라이브러리를 가린다. 3.13 부터는 오류 메시지가 "consider renaming …"으로 알려 준다(로컬 확인). `-P`/`PYTHONSAFEPATH`(3.11).
- `python path/x.py` 와 `python -m pkg.x`: 상대 import 가 되는지(`__package__`), "attempted relative import with no known parent package".
- 일반 패키지(`__init__.py`)와 네임스페이스 패키지(PEP 420): `__init__.py` 를 빠뜨리면 **조용히** 네임스페이스 패키지가 된다.
- 순환 import: 부분적으로 초기화된 모듈 때문에 `cannot import name … (most likely due to a circular import)` 가 난다. 해결책은 구조 분리, 함수 안 import, 그리고 3.15 의 **`lazy import`**(PEP 810)다. 로컬 확인: 사용 전에는 `json` 이 `sys.modules` 에 없고 전역 이름의 타입이 `lazy_import` 이며, 첫 사용 때 로드된다. `lazy` 는 모듈 스코프에서만 쓸 수 있다. `-X importtime` 으로 시작 시간을 잰다.
- `__pycache__` 와 `.pyc` 무효화(mtime 또는 hash). 커밋하지 않는다.
- 프로젝트 구성: `pyproject.toml`(PEP 621 `[project]`), **src 레이아웃**(테스트가 설치본을 import 하게 강제해 "로컬에선 되는데" 문제를 막는다), 빌드 백엔드, editable 설치(`pip install -e .`), `[project.scripts]` 진입점, PEP 735 `[dependency-groups]`.
- 가상환경의 실체: `python -m venv .venv` 는 `pyvenv.cfg`(home, version)와 `bin/python` 링크를 만든다. 활성화는 PATH 를 바꾸는 것일 뿐이다. `sys.prefix != sys.base_prefix` 로 venv 인지 판정한다. **PEP 668**: Homebrew python3.15 에 `pip install` 하면 `externally-managed-environment` 오류가 난다(로컬 확인). 그러니 항상 venv 를 쓴다. pyenv 는 **인터프리터 버전**, venv 는 **패키지 격리**를 맡는다. 잠금은 `pip freeze`, PEP 751 `pylock.toml`/`pip lock`(pip 26.2.2 에 있음, 실험적)이 있다. uv/poetry 는 이 위에 얹힌 도구이고, 실무 저장소의 `uv.lock`·`[tool.uv]` 를 읽을 수 있을 정도로 다룬다.
- 언제: 애플리케이션은 lock 파일로 재현성을 확보하고, 라이브러리는 범위 의존성을 쓴다.

**Java 습관 함정**: 패키지 구조를 깊게 중첩한다(`com.company.project…`), 파일 하나에 클래스 하나, 모듈 이름을 표준 라이브러리와 겹치게 짓는다, 전역 설치.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_module_executes_once.py` | import 를 두 번 해도 실행은 한 번 | 실행 로그, `sys.modules` |
| `ex02_from_import_binding.py` | `from m import x` 뒤 `m.x` 재바인딩이 보이지 않는다 | `is` 비교 |
| `ex03_stdlib_shadowing.py` | 임시 디렉터리의 `random.py` 가 표준 라이브러리를 가린다, `-P` 로 해결 | 서브프로세스 stderr 에 "consider renaming" 포함 판정 |
| `ex04_script_vs_module.py` | 상대 import 파일을 스크립트로, 그리고 `-m` 으로 실행 | 서브프로세스 결과 |
| `ex05_circular_import.py` | 순환 재현, 함수 안 import 와 `lazy import` 로 해결 | 오류 메시지 판정 |
| `ex06_lazy_import.py` | `lazy import` 전후의 `sys.modules`, `-X importtime` | 판정 |
| `ex07_venv_anatomy.py` | 현재 인터프리터가 venv 인지, site-packages, `pyvenv.cfg` | 판정만 |

| 실습 | 테스트가 확인하는 것 |
|---|---|
| `exercises/broken_app/` (일부러 넣은 결함 3개: 순환 import, 표준 라이브러리 이름 가림, 스크립트 실행 시 상대 import 실패) 수정 | 서브프로세스로 `python -m broken_app` 를 실행해 golden 과 비교, 모듈 이름이 `sys.stdlib_module_names` 와 겹치지 않는지 검사 |
| `plugins.load_plugins(package)`: `pkgutil.iter_modules` + `importlib.import_module` | 제공된 `sample_plugins` 를 이름순으로 로드, 각 모듈은 한 번만 실행, 잘못된 플러그인은 `PluginError` 로 감싼다(`__cause__`) |
