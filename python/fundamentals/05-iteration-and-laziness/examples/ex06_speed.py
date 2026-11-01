"""속도: 같은 결과를 내는 방법들의 상대 비용을 timeit 으로 잰다.

시간은 기계·부하마다 달라서 기대 출력의 숫자는 `...` 로 비워 둔다(검증은 '돌아가는가'만).
직접 실행해 숫자를 보고, README 의 '원소당 실행되는 것' 표와 맞는지 확인할 것.
"""

import timeit

xs = list(range(10_000))


def bench(stmt):
    return min(timeit.repeat(stmt, globals={"xs": xs}, number=300, repeat=5)) / 300


def report(title, cases):
    print(f"[{title}]")
    base = bench(cases["listcomp"])
    for name, stmt in cases.items():
        t = bench(stmt)
        print(f"  {name:17} {t * 1e6:7.1f} us  listcomp 대비 x{t / base:.2f}")


# A. 변환이 '식'일 때: 원소당 오버헤드 차이가 그대로 드러난다
report("x * 2", {
    "listcomp": "[x * 2 for x in xs]",
    "for+append": "out = []\nfor x in xs:\n    out.append(x * 2)",
    "list(genexpr)": "list(x * 2 for x in xs)",
    "list(map(lambda))": "list(map(lambda x: x * 2, xs))",
})

# B. 이미 있는 C 함수를 적용할 때: map 은 원소당 파이썬 프레임을 만들지 않는다
report("str(x)", {
    "listcomp": "[str(x) for x in xs]",
    "list(map(str))": "list(map(str, xs))",
})

# Output:
# [x * 2]
#   listcomp          ... us  listcomp 대비 x...
#   for+append        ... us  listcomp 대비 x...
#   list(genexpr)     ... us  listcomp 대비 x...
#   list(map(lambda)) ... us  listcomp 대비 x...
# [str(x)]
#   listcomp          ... us  listcomp 대비 x...
#   list(map(str))    ... us  listcomp 대비 x...
