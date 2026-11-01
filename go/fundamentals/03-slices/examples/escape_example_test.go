package examples_test

import (
	"fmt"

	"github.com/yongun2/gibongi/go/internal/observe"
)

// 무엇을 보나: 컴파일러가 슬라이스 backing array를 스택에 둘지 힙에 둘지
// 어떻게 판단하는지. "does not escape"는 "힙 할당 0회"와 같은 말이 아니다
// (sumVar 참고 → Example_allocsVariableMake에서 실제 할당 횟수로 확인).
func Example_escape() {
	for _, line := range observe.Escapes("escape.go") {
		fmt.Println(line)
	}
	// Output:
	// 7:11: make([]int, 0, 8) does not escape
	// 9:13: append does not escape
	// 20:11: make([]int, 0, n) does not escape
	// 22:13: append does not escape
	// 33:13: make([]int, n) escapes to heap
	// 40:11: make([]int, 4) escapes to heap
}
