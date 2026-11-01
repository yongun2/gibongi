"""eager(단계마다 리스트) vs lazy(원소 단위로 파이프라인 통과) — Kotlin 의 List 체인 vs Sequence 와 같은 구도.

관찰: 1) 실행 순서: eager 는 '모두 filter → 모두 map', lazy 는 원소별로 filter→map 이 번갈아 나온다
      2) 앞의 몇 개만 필요할 때 lazy 는 필요한 만큼만 원소를 당긴다(short-circuit)
      3) 조건이 한 개짜리 list comprehension 은 이미 '한 번의 루프'다 (중간 리스트 없음)
"""

from itertools import islice

log = []


def is_even(x):
    log.append(f"filter({x})")
    return x % 2 == 0


def square(x):
    log.append(f"map({x})")
    return x * x


src = range(4)

# 1) eager: Kotlin 의 xs.filter{}.map{} 처럼 단계마다 중간 리스트가 생긴다
log.clear()
evens = [x for x in src if is_even(x)]  # 중간 리스트
eager = [square(x) for x in evens]
print("eager :", " ".join(log))

# 2) lazy: Kotlin 의 xs.asSequence().filter{}.map{} 와 같은 모양
log.clear()
lazy = (square(x) for x in (x for x in src if is_even(x)))
print("생성 직후 log:", log)  # 아직 아무것도 실행 안 됨
result = list(lazy)
print("lazy  :", " ".join(log))
print("결과 같음:", eager == result)

# 3) 한 줄 comprehension 은 filter 와 map 을 한 루프에서 처리한다 (중간 리스트 X)
log.clear()
one_pass = [square(x) for x in src if is_even(x)]
print("1-pass:", " ".join(log))

# 4) 앞의 5개만 필요할 때 당겨온 원소 수
pulled = 0


def counted(n):
    global pulled
    for i in range(n):
        pulled += 1
        yield i


N = 1_000_000
pulled = 0
first5_eager = [x * x for x in [x for x in counted(N) if x % 3 == 0]][:5]
print("eager 가 당긴 원소:", pulled)

pulled = 0
first5_lazy = list(islice((x * x for x in counted(N) if x % 3 == 0), 5))
print("lazy  가 당긴 원소:", pulled, first5_lazy)
print("앞 5개 같음:", first5_eager == first5_lazy)

# Output:
# eager : filter(0) filter(1) filter(2) filter(3) map(0) map(2)
# 생성 직후 log: []
# lazy  : filter(0) map(0) filter(1) filter(2) map(2) filter(3)
# 결과 같음: True
# 1-pass: filter(0) map(0) filter(1) filter(2) map(2) filter(3)
# eager 가 당긴 원소: 1000000
# lazy  가 당긴 원소: 13 [0, 9, 36, 81, 144]
# 앞 5개 같음: True
