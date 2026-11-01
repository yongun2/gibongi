"""05 실습: 반복자 프로토콜과 지연 평가 도구.

규칙
- 각 함수/메서드의 `raise NotImplementedError` 를 지우고 본문만 채운다(시그니처·docstring 은 그대로).
- 표준 라이브러리(itertools, collections, math 등)는 자유롭게 써도 된다.
- 테스트: 저장소 루트에서 `./check python 05`
"""

import itertools
import math
from collections.abc import Callable, Iterable, Iterator
from pathlib import Path
from typing import TextIO


class Countdown:
    """start, start-1, ..., 1 을 내놓는 **반복자(iterator)**. 한 번 소진되면 다시 돌지 않는다.

    >>> list(Countdown(3))
    [3, 2, 1]
    """

    def __init__(self, start: int) -> None:
        self._current = start

    def __iter__(self) -> "Countdown":
        return self

    def __next__(self) -> int:
        if self._current <= 0:
            raise StopIteration
        value = self._current
        self._current -= 1
        return value


class CountdownRange:
    """start..1 을 **몇 번이든** 순회할 수 있는 iterable. `__iter__` 를 부를 때마다 독립된 반복자를 준다."""

    def __init__(self, start: int) -> None:
        self._start = start

    def __iter__(self) -> Iterator[int]:
        return Countdown(self._start)


def chunked[T](iterable: Iterable[T], size: int) -> Iterator[list[T]]:
    """iterable 을 size 개씩 묶은 리스트를 지연 생성한다. 마지막 묶음은 더 짧을 수 있다.

    - size < 1 이면 **호출하는 즉시** ValueError (첫 next() 때가 아니라).
    - 필요한 만큼만 원소를 당겨온다: 무한 iterable 에도 동작해야 한다.
    """
    if size < 1:
        raise ValueError(f"size must be >= 1, got {size}")

    def generate() -> Iterator[list[T]]:
        it = iter(iterable)
        while batch := list(itertools.islice(it, size)):
            yield batch

    return generate()


def flatten(items: Iterable[object]) -> Iterator[object]:
    """임의 깊이로 중첩된 iterable 을 지연 평탄화한다.

    str / bytes 는 iterable 이지만 쪼개지 않고 하나의 원소로 취급한다.
    """
    for item in items:
        if isinstance(item, Iterable) and not isinstance(item, (str, bytes)):
            yield from flatten(item)
        else:
            yield item


def mean_and_stdev(numbers: Iterable[float]) -> tuple[float, float]:
    """(평균, 모표준편차)를 **한 번의 순회, O(1) 추가 메모리**로 계산한다.

    - 일회용 iterator(generator 등)를 받아도 정확해야 한다.
    - 큰 값 주변의 작은 분산도 정확해야 한다(E[x²]-E[x]² 공식은 상쇄 오차로 실패한다).
    - 비어 있으면 ValueError.
    """
    count, mean, m2 = 0, 0.0, 0.0
    for x in numbers:  # Welford: 평균과 편차제곱합을 한 번에 갱신
        count += 1
        delta = x - mean
        mean += delta / count
        m2 += delta * (x - mean)
    if count == 0:
        raise ValueError("mean_and_stdev() of empty data")
    return mean, math.sqrt(m2 / count)


def read_ints(path: Path, opener: Callable[..., TextIO] = open) -> Iterator[int]:
    """파일의 각 줄을 int 로 지연 변환해 내놓는다. 공백뿐인 줄은 건너뛴다.

    - 파일은 첫 원소를 요청받을 때 연다(호출 시점엔 열지 않는다).
    - 끝까지 소비하거나 generator 가 close() 되면 파일이 닫혀야 한다.
    - 파일은 `opener(path, encoding="utf-8")` 로 연다(테스트가 opener 를 바꿔 끼운다).
    """
    with opener(path, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                yield int(line)
