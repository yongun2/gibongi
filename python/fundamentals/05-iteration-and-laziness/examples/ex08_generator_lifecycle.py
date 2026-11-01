"""generator 는 '멈춰 있는 함수 프레임'이다. 상태, 정리(finally), 반환값을 관찰한다.

Java 에는 대응물이 없다: 같은 걸 하려면 상태 필드를 가진 Iterator 클래스를 손으로 짜야 한다.
"""

import inspect


def numbers(n):
    print("  [gen] 시작")
    try:
        for i in range(n):
            print(f"  [gen] yield {i}")
            yield i
        return "완료"  # StopIteration.value 로 전달된다
    finally:
        print("  [gen] finally 실행")


g = numbers(3)
print("생성 직후:", inspect.getgeneratorstate(g))  # 본문은 아직 한 줄도 실행 안 됨
print("next →", next(g))
print("상태:", inspect.getgeneratorstate(g), "| 지역변수:", inspect.getgeneratorlocals(g))
print("next →", next(g))
g.close()  # 멈춘 지점에 GeneratorExit 를 던진다 → finally 실행
print("close 후:", inspect.getgeneratorstate(g))

print("-- 끝까지 돌리면 반환값은 StopIteration.value")
g2 = numbers(1)
next(g2)
try:
    next(g2)
except StopIteration as stop:
    print("StopIteration.value =", stop.value)


def delegator():
    result = yield from numbers(1)  # 하위 generator 의 반환값을 받는다
    print("  [delegator] 받은 값:", result)


print("-- yield from")
print(list(delegator()))

# Output:
# 생성 직후: GEN_CREATED
#   [gen] 시작
#   [gen] yield 0
# next → 0
# 상태: GEN_SUSPENDED | 지역변수: {'n': 3, 'i': 0}
#   [gen] yield 1
# next → 1
#   [gen] finally 실행
# close 후: GEN_CLOSED
# -- 끝까지 돌리면 반환값은 StopIteration.value
#   [gen] 시작
#   [gen] yield 0
#   [gen] finally 실행
# StopIteration.value = 완료
# -- yield from
#   [gen] 시작
#   [gen] yield 0
#   [gen] finally 실행
#   [delegator] 받은 값: 완료
# [0]
