package s05.exercises

// X03. 결과는 쉽다. 조건은 "힙 할당 예산"이다. 테스트가 ThreadMXBean 으로 실제 할당 바이트를 잰다.

/** 짝수 원소들의 제곱합. 원소 100만 개에서도 할당 1 KiB 미만. */
fun sumOfEvenSquares(xs: IntArray): Long = TODO("X03-1")

/** 각 값(0 until bound)의 등장 횟수. 결과 배열 말고는 거의 할당하지 않는다. */
fun histogram(xs: IntArray, bound: Int): IntArray = TODO("X03-2")

/** 서로 다른 값의 개수 (모든 값은 0 until bound). 할당 예산: bound 바이트 + 1 KiB. */
fun countDistinct(xs: IntArray, bound: Int): Int = TODO("X03-3")
