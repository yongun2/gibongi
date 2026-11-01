"""for 문은 iter() / next() / StopIteration 프로토콜로 펼쳐진다.

관찰: 1) while+next 로 손으로 펼친 것과 결과가 같다
      2) for 문의 바이트코드엔 GET_ITER/FOR_ITER 가 있고, next() '호출'(CALL)은 없다
      3) 반복자는 iter(it) is it 이고, 소진되면 조용히 끝난다
"""

import dis


def with_for(xs):
    total = 0
    for x in xs:
        total += x
    return total


def with_while(xs):
    total = 0
    it = iter(xs)  # for 의 GET_ITER 에 해당
    while True:
        try:
            x = next(it)  # for 의 FOR_ITER 에 해당
        except StopIteration:  # 소진 신호. for 는 이걸 예외 없이 처리한다
            break
        total += x
    return total


data = [3, 1, 4, 1, 5]
print("같은 결과:", with_for(data) == with_while(data))

ops = {ins.opname for ins in dis.get_instructions(with_for)}
print("GET_ITER 있음:", "GET_ITER" in ops)
print("FOR_ITER 있음:", "FOR_ITER" in ops)
print("함수 호출(CALL) 있음:", any(op.startswith("CALL") for op in ops))

it = iter(data)
print(type(it).__name__, "| iter(it) is it:", iter(it) is it)
print("앞의 두 개:", next(it), next(it))
print("나머지:", list(it))
print("소진 후 next(it, 기본값):", next(it, "끝"))

# Output:
# 같은 결과: True
# GET_ITER 있음: True
# FOR_ITER 있음: True
# 함수 호출(CALL) 있음: False
# list_iterator | iter(it) is it: True
# 앞의 두 개: 3 1
# 나머지: [4, 1, 5]
# 소진 후 next(it, 기본값): 끝
