import itertools
import math
import statistics
import tracemalloc
from collections.abc import Iterable, Iterator

import pytest

from lazy_tools import (
    Countdown,
    CountdownRange,
    chunked,
    flatten,
    mean_and_stdev,
    read_ints,
)


class Tracked:
    """몇 개의 원소가 당겨졌는지, __iter__ 가 몇 번 불렸는지 세는 iterable."""

    def __init__(self, source):
        self.source = source
        self.pulled = 0
        self.iter_calls = 0

    def __iter__(self):
        self.iter_calls += 1
        for x in self.source:
            self.pulled += 1
            yield x


# --- Countdown / CountdownRange: iterator vs iterable --------------------------

def test_countdown_yields_values():
    assert list(Countdown(3)) == [3, 2, 1]
    assert list(Countdown(0)) == []


def test_countdown_is_one_shot_iterator():
    c = Countdown(2)
    assert iter(c) is c
    assert isinstance(c, Iterator)
    assert list(c) == [2, 1]
    assert list(c) == []
    with pytest.raises(StopIteration):
        next(c)


def test_countdown_shares_state_between_loops():
    c = Countdown(4)
    assert list(zip(c, c)) == [(4, 3), (2, 1)]


def test_countdown_range_is_reiterable():
    r = CountdownRange(3)
    assert isinstance(r, Iterable) and not isinstance(r, Iterator)
    assert list(r) == list(r) == [3, 2, 1]
    assert list(zip(r, r)) == [(3, 3), (2, 2), (1, 1)]


# --- chunked ---------------------------------------------------------------------

def test_chunked_basic():
    assert list(chunked(range(7), 3)) == [[0, 1, 2], [3, 4, 5], [6]]
    assert list(chunked([], 3)) == []


def test_chunked_validates_eagerly():
    with pytest.raises(ValueError):
        chunked([1, 2], 0)  # next() 없이 호출만 해도 실패해야 한다


def test_chunked_is_lazy():
    src = Tracked(itertools.count())
    it = chunked(src, 3)
    assert iter(it) is it
    assert src.pulled == 0
    assert next(it) == [0, 1, 2]
    assert src.pulled == 3


def test_chunked_infinite():
    assert list(itertools.islice(chunked(itertools.count(), 2), 3)) == [[0, 1], [2, 3], [4, 5]]


# --- flatten ---------------------------------------------------------------------

def test_flatten_nested():
    data = [1, [2, (3, [4, []])], (x for x in [5, 6])]
    assert list(flatten(data)) == [1, 2, 3, 4, 5, 6]


def test_flatten_keeps_strings_whole():
    assert list(flatten(["ab", [b"cd", ["ef"]]])) == ["ab", b"cd", "ef"]


def test_flatten_is_lazy():
    nested = ([i] for i in itertools.count())
    assert list(itertools.islice(flatten(nested), 4)) == [0, 1, 2, 3]


# --- mean_and_stdev --------------------------------------------------------------

def test_mean_and_stdev_values():
    data = [2, 4, 4, 4, 5, 5, 7, 9]
    assert mean_and_stdev(data) == pytest.approx((5.0, 2.0))


def test_mean_and_stdev_accepts_one_shot_iterator():
    src = Tracked([2, 4, 4, 4, 5, 5, 7, 9])
    assert mean_and_stdev(src) == pytest.approx((5.0, 2.0))
    assert src.iter_calls == 1


def test_mean_and_stdev_numerically_stable():
    data = [1e9 + d for d in (4, 7, 13, 16)]
    mean, stdev = mean_and_stdev(iter(data))
    assert mean == pytest.approx(1e9 + 10)
    assert stdev == pytest.approx(statistics.pstdev(data), rel=1e-9)


def test_mean_and_stdev_constant_memory():
    n = 300_000
    tracemalloc.start()
    tracemalloc.reset_peak()
    try:
        mean, _ = mean_and_stdev(float(i % 100) for i in range(n))
        _, peak = tracemalloc.get_traced_memory()
    finally:
        tracemalloc.stop()
    assert mean == pytest.approx(49.5)
    assert peak < 256 * 1024, f"peak {peak:,}B: 입력 전체를 메모리에 올리고 있지 않은가?"


def test_mean_and_stdev_empty():
    with pytest.raises(ValueError):
        mean_and_stdev(iter([]))


# --- read_ints -------------------------------------------------------------------

@pytest.fixture
def numbers_file(tmp_path):
    p = tmp_path / "nums.txt"
    p.write_text("1\n  \n2\n 3 \n", encoding="utf-8")
    return p


@pytest.fixture
def spy_open():
    opened = []

    def opener(*args, **kwargs):
        f = open(*args, **kwargs)
        opened.append(f)
        return f

    opener.opened = opened
    return opener


def test_read_ints_values(numbers_file):
    assert list(read_ints(numbers_file)) == [1, 2, 3]


def test_read_ints_opens_lazily_and_closes_at_end(numbers_file, spy_open):
    gen = read_ints(numbers_file, opener=spy_open)
    assert spy_open.opened == [], "호출 시점에 파일을 열면 안 된다"
    assert list(gen) == [1, 2, 3]
    assert len(spy_open.opened) == 1
    assert spy_open.opened[0].closed


def test_read_ints_closes_on_early_close(numbers_file, spy_open):
    gen = read_ints(numbers_file, opener=spy_open)
    assert next(gen) == 1
    assert not spy_open.opened[0].closed
    gen.close()
    assert spy_open.opened[0].closed
