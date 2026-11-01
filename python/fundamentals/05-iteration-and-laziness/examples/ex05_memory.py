"""메모리: list comprehension 은 결과 전체를 담고, generator 는 '현재 원소 하나 + 프레임'만 담는다.

tracemalloc 으로 '실행 중 최대 할당량(peak)'을, sys.getsizeof 로 '객체 자체 크기'를 본다.
구체적인 바이트 수는 버전/플랫폼마다 달라서 stdout 엔 관계(부등식)만 찍고, 숫자는 stderr 로 보낸다.
"""

import sys
import tracemalloc

N = 1_000_000


def peak_bytes(fn):
    tracemalloc.start()
    tracemalloc.reset_peak()
    fn()
    _, peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()
    return peak


def total_listcomp():
    return sum([x * x for x in range(N)])


def total_genexpr():
    return sum(x * x for x in range(N))


def total_loop():
    t = 0
    for x in range(N):
        t += x * x
    return t


print("세 결과 같음:", total_listcomp() == total_genexpr() == total_loop())

p_list = peak_bytes(total_listcomp)
p_gen = peak_bytes(total_genexpr)
p_loop = peak_bytes(total_loop)
print(f"[관찰] peak listcomp={p_list:,}B genexpr={p_gen:,}B loop={p_loop:,}B", file=sys.stderr)
print("listcomp peak 가 원소 수 x 8B(포인터) 이상:", p_list >= N * 8)
print("genexpr peak 가 listcomp 의 1/100 미만:", p_gen < p_list / 100)
print("loop peak 도 1/100 미만:", p_loop < p_list / 100)

small = sys.getsizeof(x for x in range(10))
big = sys.getsizeof(x for x in range(N))
print(f"[관찰] getsizeof(genexpr) N=10:{small}B N={N:,}:{big}B", file=sys.stderr)
print("generator 크기는 N 과 무관:", small == big)

lst = [x * x for x in range(N)]
shallow = sys.getsizeof(lst)
print(f"[관찰] getsizeof(list)={shallow:,}B (int 객체들은 미포함)", file=sys.stderr)
print("getsizeof(list) 는 원소(int 객체) 크기를 세지 않는다:", shallow < p_list / 2)

# Output:
# 세 결과 같음: True
# listcomp peak 가 원소 수 x 8B(포인터) 이상: True
# genexpr peak 가 listcomp 의 1/100 미만: True
# loop peak 도 1/100 미만: True
# generator 크기는 N 과 무관: True
# getsizeof(list) 는 원소(int 객체) 크기를 세지 않는다: True
