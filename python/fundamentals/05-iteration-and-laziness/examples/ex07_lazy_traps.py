"""지연 평가가 만드는 실무 버그 세 가지.

1) genexpr 는 '맨 앞 for 의 iterable'만 생성 시점에 평가하고, 나머지(조건·식)는 소비 시점에 평가한다
2) with 블록 안에서 만든 generator 를 밖으로 반환하면, 소비할 때는 파일이 이미 닫혀 있다
3) 예외는 '정의한 곳'이 아니라 '소비하는 곳'에서 터진다
"""

import os
import tempfile

# 1) 평가 시점
xs = [1, 2, 3]
allowed = {1, 2, 3}
g = (x for x in xs if x in allowed)
xs = [10, 20]  # 이름 xs 를 다른 리스트에 묶어도 g 는 원래 리스트를 이미 잡고 있다
allowed = {3}  # 하지만 조건의 allowed 는 소비 시점에 조회된다
print("1) 결과:", list(g))

# 2) 닫힌 파일
path = os.path.join(tempfile.mkdtemp(), "nums.txt")
with open(path, "w", encoding="utf-8") as f:
    f.write("1\n2\n3\n")


def read_numbers_buggy(p):
    with open(p, encoding="utf-8") as f:
        return (int(line) for line in f)  # with 를 벗어나며 f.close()


def read_numbers_ok(p):
    with open(p, encoding="utf-8") as f:  # generator 함수: with 가 소비 내내 열려 있다
        for line in f:
            yield int(line)


try:
    print(list(read_numbers_buggy(path)))
except ValueError as e:
    print("2) buggy:", type(e).__name__, "-", e)
print("2) ok   :", list(read_numbers_ok(path)))


# 3) 예외 위치
def parse_all(lines):
    return (int(s) for s in lines)  # 여기서는 아무 일도 일어나지 않는다


parsed = parse_all(["1", "x", "3"])
print("3) 생성은 성공:", type(parsed).__name__)
try:
    for n in parsed:
        print("3) 소비:", n)
except ValueError as e:
    print("3) 소비 중 예외:", e)

# Output:
# 1) 결과: [3]
# 2) buggy: ValueError - I/O operation on closed file.
# 2) ok   : [1, 2, 3]
# 3) 생성은 성공: generator
# 3) 소비: 1
# 3) 소비 중 예외: invalid literal for int() with base 10: 'x'
