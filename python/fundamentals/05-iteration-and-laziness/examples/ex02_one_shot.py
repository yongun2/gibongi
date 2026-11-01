"""iterable(여러 번 순회 가능) vs iterator(일회용). 일회용을 두 번 돌면 '예외 없이' 비어 있다.

Java Stream 을 두 번 소비하면 IllegalStateException 이 나지만,
Python 의 generator/map/filter/zip/파일 객체는 두 번째 순회에서 그냥 아무것도 내놓지 않는다.
"""

nums = [1, 2, 3]
print("list: iter() 마다 새 반복자:", iter(nums) is not iter(nums))

squares = (n * n for n in nums)
print("generator: iter(g) is g:", iter(squares) is squares)
print("첫 번째 sum:", sum(squares))
print("두 번째 sum:", sum(squares))

m = map(str, nums)
print("map 두 번 list():", list(m), list(m))


def normalize(values):
    total = sum(values)  # 1차 순회
    return [v / total for v in values]  # 2차 순회


print("list 입력:", normalize([1, 1, 2]))
print("generator 입력:", normalize(v for v in [1, 1, 2]))

d = {"a": 1, "b": 2}
items = d.items()  # dict view 는 iterable(재순회 가능)이지 iterator 가 아니다
print("dict view 재순회:", list(items) == list(items))

# Output:
# list: iter() 마다 새 반복자: True
# generator: iter(g) is g: True
# 첫 번째 sum: 14
# 두 번째 sum: 0
# map 두 번 list(): ['1', '2', '3'] []
# list 입력: [0.25, 0.25, 0.5]
# generator 입력: []
# dict view 재순회: True
