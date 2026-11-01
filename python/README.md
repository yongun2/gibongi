# Python

Python 3.15.2를 쓴다. 버전은 루트 `mise.toml`에 고정해 뒀다. 테스트는 pytest로 돌리고, 11장부터는 mypy 타입 검사도 같이 돈다. 섹션 목록과 진행 현황은 [루트 README](../README.md#python)에 있다.

## 환경 구성

```sh
mise install          # 저장소 루트에서
cd python
python -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install --group dev
```

패키지는 항상 venv에 설치한다. Homebrew로 설치한 Python은 아예 전역 `pip install`을 막아 둔다(PEP 668). 13장에서 쓰는 free-threaded 빌드는 선택이고, 설치 방법은 13장에서 다룬다.

## 실행

```sh
./check python 05                 # 예제 + 실습
./check python 05 examples        # 예제만
./check python 05 exercises -x    # 실습만. 뒤에 붙인 인자는 pytest로 넘어간다
./check python all
```

다른 인터프리터로 돌리고 싶으면 `PY=python/.venv-ft/bin/python ./check python 13`처럼 `PY`를 지정한다.

## 폴더 구조

```
fundamentals/NN-주제/
  README.md
  examples/     exNN_*.py, 파일 끝의 # Output: 주석이 기대 출력
  exercises/    빈 함수(raise NotImplementedError)와 test_*.py
```

예제는 `conftest.py`가 새 인터프리터로 하나씩 실행해서 `# Output:` 블록과 비교한다. pytest 안에서 바로 돌리면 이미 불러온 모듈들 때문에 참조 카운트나 메모리 측정값이 달라지기 때문이다. 실행할 때마다 달라지는 값은 기대 출력에 `...`로 적어 두면 어떤 문자열이 와도 통과한다.
