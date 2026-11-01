"""같은 일(홀수만 제곱해 리스트로)을 네 가지로 쓰고, 바이트코드에서 차이를 찾는다.

    python ex03_bytecode.py      # 판정만
    python ex03_bytecode.py -v   # 전체 dis 출력까지 (stderr)

볼 것:
  - for+append : 원소마다 `out.append` 속성 조회(LOAD_ATTR) + 메서드 호출(CALL)
  - listcomp   : 3.12+(PEP 709) 함수 안으로 인라인. 중첩 code object 없음, 전용 opcode LIST_APPEND
  - genexpr    : 별도 code object(<genexpr>) → generator 객체. 원소마다 YIELD_VALUE/RESUME 왕복
  - map/filter : 바깥엔 호출 3번뿐. 원소 루프는 C 안에서 돌고, 원소마다 lambda(파이썬 함수)를 호출
"""

import dis
import sys
import types


def for_append(xs):
    out = []
    for x in xs:
        if x % 2:
            out.append(x * x)
    return out


def listcomp(xs):
    return [x * x for x in xs if x % 2]


def genexpr(xs):
    return list(x * x for x in xs if x % 2)


def map_filter(xs):
    return list(map(lambda x: x * x, filter(lambda x: x % 2, xs)))


def nested_codes(fn):
    return [c for c in fn.__code__.co_consts if isinstance(c, types.CodeType)]


def summary(fn):
    ins = list(dis.get_instructions(fn))
    attr_append = any(i.opname.startswith("LOAD_ATTR") and i.argval == "append" for i in ins)
    list_append = any(i.opname == "LIST_APPEND" for i in ins)
    nested = nested_codes(fn)
    yields = any(
        i.opname == "YIELD_VALUE" for c in nested for i in dis.get_instructions(c)
    )
    names = [c.co_name for c in nested]
    return f"append조회={attr_append!s:5} LIST_APPEND={list_append!s:5} 중첩code={names} 중첩안YIELD={yields}"


fns = [for_append, listcomp, genexpr, map_filter]
data = list(range(10))
print("결과 모두 같음:", len({tuple(f(data)) for f in fns}) == 1)
for fn in fns:
    print(f"{fn.__name__:10} | {summary(fn)}")

if "-v" in sys.argv:
    for fn in fns:
        print(f"\n===== {fn.__name__}", file=sys.stderr)
        dis.dis(fn, file=sys.stderr)

# Output:
# 결과 모두 같음: True
# for_append | append조회=True  LIST_APPEND=False 중첩code=[] 중첩안YIELD=False
# listcomp   | append조회=False LIST_APPEND=True  중첩code=[] 중첩안YIELD=False
# genexpr    | append조회=False LIST_APPEND=... 중첩code=['<genexpr>'] 중첩안YIELD=True
# map_filter | append조회=False LIST_APPEND=False 중첩code=['<lambda>', '<lambda>'] 중첩안YIELD=False
