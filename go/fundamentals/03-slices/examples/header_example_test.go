package examples_test

import (
	"fmt"
	"unsafe"
)

// 무엇을 보나: 배열은 값, 슬라이스는 {ptr, len, cap} 헤더(arm64에서 24바이트)다.
// 슬라이싱은 복사가 아니라 "같은 배열 위에 창을 하나 더 내는 것"이다.
func Example_header() {
	arr := [6]int{0, 1, 2, 3, 4, 5}
	s := arr[1:3]

	fmt.Println(unsafe.Sizeof(arr), unsafe.Sizeof(s)) // 배열 48바이트(6*8), 헤더 24바이트
	fmt.Println(len(s), cap(s), s)                    // cap은 arr 끝까지: 6-1
	fmt.Println(unsafe.SliceData(s) == &arr[1])       // 헤더의 ptr은 arr[1]을 가리킨다

	s = s[:cap(s)] // len은 cap까지 다시 늘릴 수 있다(앞쪽은 못 돌아간다)
	fmt.Println(s)

	b := arr // 배열 대입은 48바이트 전체 복사 (Java 배열 참조 대입과 다름)
	b[0] = 100
	fmt.Println(arr[0], b[0])
	// Output:
	// 48 24
	// 2 5 [1 2]
	// true
	// [1 2 3 4 5]
	// 0 100
}

// 무엇을 보나: cap 안에서의 append는 "공유 배열에 쓰기"다.
// 두 슬라이스가 같은 칸에 append하면 서로를 덮어쓴다.
func Example_appendAliasing() {
	a := make([]int, 3, 4) // [0 0 0], 여유 1칸
	b := append(a, 1)      // a[3] 자리에 1을 씀
	c := append(a, 2)      // 같은 자리에 2를 씀 → b[3]도 바뀐다
	fmt.Println(b[3], c[3])
	fmt.Println(unsafe.SliceData(b) == unsafe.SliceData(a))

	d := append(b, 3) // b는 len==cap → 새 배열로 이사
	d[0] = 99
	fmt.Println(a[0], d[0], unsafe.SliceData(d) == unsafe.SliceData(a))
	// Output:
	// 2 2
	// true
	// 0 99 false
}

// 무엇을 보나: full slice expression s[lo:hi:max]로 cap을 잘라 두면
// 다음 append는 반드시 새 배열을 만든다. "남에게 건네는 부분 슬라이스"의 안전장치.
func Example_fullSliceExpr() {
	base := []int{1, 2, 3, 4, 5}
	view := base[1:3] // cap 4
	view = append(view, 100)
	fmt.Println(base, view)

	base = []int{1, 2, 3, 4, 5}
	safe := base[1:3:3] // cap 2
	safe = append(safe, 100)
	fmt.Println(base, safe)
	// Output:
	// [1 2 3 100 5] [2 3 100]
	// [1 2 3 4 5] [2 3 100]
}

func addItem(s []int, v int)  { s = append(s, v) } // 헤더 "복사본"의 len만 바뀐다
func setFirst(s []int, v int) { s[0] = v }         // backing array는 공유된다

// 무엇을 보나: 슬라이스는 "헤더를 값으로" 넘긴다. 원소 쓰기는 보이고 len 변화는 안 보인다.
// Java의 list.add(x) 습관으로 짜면 호출자 쪽에서 원소가 "사라진다".
func Example_passHeaderByValue() {
	s := make([]int, 1, 10)
	addItem(s, 7)
	setFirst(s, 42)
	fmt.Println(len(s), s)
	fmt.Println(s[:2]) // 7은 배열에 써졌지만 s의 len이 1이라 보이지 않았을 뿐
	// Output:
	// 1 [42]
	// [42 7]
}
