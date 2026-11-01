// escape.go는 탈출 분석(escape analysis) 관찰 대상이다.
// Example_escape가 이 파일을 -gcflags=-m 으로 컴파일해서 진단을 출력한다.
package examples

// 상수 크기, 함수 밖으로 안 나감 → 스택
func SumConst() int {
	s := make([]int, 0, 8)
	for i := range 8 {
		s = append(s, i)
	}
	total := 0
	for _, v := range s {
		total += v
	}
	return total
}

// 크기가 런타임에 정해짐 → (Go 1.25+) 32바이트 이하면 스택 버퍼, 넘으면 힙
func SumVar(n int) int {
	s := make([]int, 0, n)
	for i := range n {
		s = append(s, i)
	}
	total := 0
	for _, v := range s {
		total += v
	}
	return total
}

// 반환되는 슬라이스 → 호출자가 계속 쓰므로 힙
func MakeReturned(n int) []int {
	return make([]int, n)
}

// 원소 주소를 전역에 저장 → backing array 전체가 힙
var Keep *int

func PinElement() {
	s := make([]int, 4)
	Keep = &s[2]
}
