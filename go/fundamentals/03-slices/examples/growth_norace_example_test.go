//go:build !race

// -race 빌드에서는 컴파일러의 스택 버퍼 최적화가 꺼져 cap 수열이 1 2 4 8...로 달라진다.
// 그래서 이 예제만 race 빌드에서 제외한다. 그 차이 자체가 "cap 값에 의존하지 마라"의 근거다.

package examples_test

import "fmt"

// 무엇을 보나: nil에서 시작하는 append 루프. Go 1.26부터 컴파일러가 32바이트 스택 버퍼를
// 투기적으로 써서 첫 cap이 4(int64 4개 = 32B)부터 시작한다. 1.25 이전엔 1, 2, 4, ...였다.
// → cap의 정확한 값은 구현 디테일이다. 코드가 의존하면 안 된다.
func Example_growthFromNil() {
	var s []int64
	prev := -1
	for i := range 300 {
		s = append(s, int64(i))
		if cap(s) != prev {
			fmt.Print(cap(s), " ")
			prev = cap(s)
		}
	}
	fmt.Println()
	// Output:
	// 4 8 16 32 64 128 256 512
}
