package examples_test

import (
	"fmt"
	"testing"

	"github.com/yongun2/gibongi/go/fundamentals/03-slices/examples"
)

var sinkInts []int64

// 무엇을 보나: 힙에서의 성장 정책. cap<256이면 2배, 그 뒤로는
// newcap += (newcap + 768) / 4 로 1.25배에 수렴하고, 마지막에 size class로 올림한다.
// 그래서 비율이 1.66, 1.51 ... 처럼 "깔끔하지 않게" 나온다.
func Example_growthPolicy() {
	s := make([]int64, 256)
	sinkInts = s // 힙에 있게 강제
	prev := cap(s)
	for range 5000 {
		s = append(s, 0)
		if c := cap(s); c != prev {
			fmt.Printf("%d→%d (x%.2f)\n", prev, c, float64(c)/float64(prev))
			prev = c
		}
	}
	sinkInts = s
	// Output:
	// 256→512 (x2.00)
	// 512→848 (x1.66)
	// 848→1280 (x1.51)
	// 1280→1792 (x1.40)
	// 1792→2560 (x1.43)
	// 2560→3408 (x1.33)
	// 3408→5120 (x1.50)
	// 5120→7168 (x1.40)
}

// 무엇을 보나: "does not escape"여도 크기가 변수면 힙 할당이 일어날 수 있다.
// Go 1.25+는 32바이트 이하일 때만 스택 버퍼를 쓴다: int 4개(32B)는 0회, 5개(40B)는 1회.
func Example_allocsVariableMake() {
	var sink int
	for _, n := range []int{4, 5} {
		allocs := testing.AllocsPerRun(100, func() { sink = examples.SumVar(n) })
		fmt.Printf("n=%d allocs=%.0f\n", n, allocs)
	}
	_ = sink
	// Output:
	// n=4 allocs=0
	// n=5 allocs=1
}
