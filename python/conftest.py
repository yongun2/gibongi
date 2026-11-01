"""examples/ex*.py 를 '새 인터프리터'에서 실행해 파일 끝 `# Output:` 블록과 비교한다.

- 왜 서브프로세스인가: refcount, gc 카운트, tracemalloc 피크 같은 관찰값은
  pytest가 이미 import한 수백 개 모듈의 영향을 받는다. 예제는 학습자가
  `python exNN.py` 로 돌렸을 때와 똑같은 깨끗한 환경에서 검증해야 한다.
- 비교 규칙: 줄 단위, 오른쪽 공백 무시. 기대 줄 안의 `...` 는 임의 문자열
  (doctest ELLIPSIS와 같은 의미). 측정값처럼 실행마다 달라지는 부분에만 쓴다.
"""

import difflib
import re
import subprocess
import sys
from pathlib import Path

import pytest

MARK = "# Output:"


def pytest_collect_file(parent: pytest.Collector, file_path: Path):
    if (
        file_path.suffix == ".py"
        and file_path.parent.name == "examples"
        and file_path.name.startswith("ex")
    ):
        return ExampleFile.from_parent(parent, path=file_path)
    return None


def expected_lines(source: str) -> list[str]:
    lines = source.splitlines()
    try:
        start = lines.index(MARK)
    except ValueError:
        raise ValueError(f"'{MARK}' 블록이 없습니다") from None
    out = []
    for line in lines[start + 1 :]:
        if not line.startswith("#"):
            break
        out.append(line[2:] if line.startswith("# ") else line[1:])
    return out


def line_matches(expected: str, actual: str) -> bool:
    if "..." not in expected:
        return expected.rstrip() == actual.rstrip()
    pattern = ".*?".join(re.escape(part) for part in expected.rstrip().split("..."))
    return re.fullmatch(pattern, actual.rstrip()) is not None


class ExampleFile(pytest.File):
    def collect(self):
        yield ExampleItem.from_parent(self, name="output")


class ExampleMismatch(Exception):
    pass


class ExampleItem(pytest.Item):
    def runtest(self) -> None:
        expected = expected_lines(self.path.read_text(encoding="utf-8"))
        proc = subprocess.run(
            [sys.executable, self.path.name],
            cwd=self.path.parent,
            capture_output=True,
            text=True,
            timeout=60,
        )
        if proc.returncode != 0:
            raise ExampleMismatch(f"종료 코드 {proc.returncode}\n{proc.stderr}")
        actual = proc.stdout.splitlines()
        ok = len(actual) == len(expected) and all(
            line_matches(e, a) for e, a in zip(expected, actual)
        )
        if not ok:
            diff = difflib.unified_diff(expected, actual, "기대(# Output:)", "실제(stdout)", lineterm="")
            raise ExampleMismatch("\n".join(diff))

    def repr_failure(self, excinfo, style=None):
        if isinstance(excinfo.value, ExampleMismatch):
            return f"{self.path.name} 출력 불일치\n{excinfo.value}"
        return super().repr_failure(excinfo)

    def reportinfo(self):
        return self.path, None, f"example: {self.path.name}"
