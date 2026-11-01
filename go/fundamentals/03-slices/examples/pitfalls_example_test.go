package examples_test

import (
	"encoding/json"
	jsonv2 "encoding/json/v2"
	"fmt"
	"runtime"
	"slices"
)

type proc struct{ id, served int }

// 무엇을 보나: range의 두 번째 값은 원소의 "복사본"이다.
// Java for-each는 객체 참조를 주므로 p.served++가 원본을 바꾸지만, Go 값 슬라이스는 아니다.
func Example_rangeCopies() {
	procs := []proc{{1, 0}, {2, 0}}
	for _, p := range procs {
		p.served++ // 복사본 수정 → 사라짐
	}
	fmt.Println(procs)
	for i := range procs {
		procs[i].served++ // 인덱스로 원본 수정
	}
	fmt.Println(procs)
	// Output:
	// [{1 0} {2 0}]
	// [{1 1} {2 1}]
}

// 무엇을 보나: 원소 주소(&procs[0])를 들고 있다가 append가 배열을 옮기면
// 그 포인터는 "옛 배열"을 가리킨다. 스케줄러의 running 포인터에서 흔히 터진다.
func Example_stalePointer() {
	procs := make([]proc, 1, 1)
	running := &procs[0]
	procs = append(procs, proc{id: 2}) // cap 초과 → 새 배열로 복사
	running.served = 10                // 옛 배열에 기록됨
	fmt.Println(procs[0].served, running.served)
	// Output:
	// 0 10
}

// 무엇을 보나: nil 슬라이스와 빈 슬라이스는 len은 같지만 구분된다.
// encoding/json(v1 API)은 nil → null, encoding/json/v2는 nil → [] (Go 1.27 기준).
func Example_nilVersusEmpty() {
	var nilS []int
	empty := []int{}
	fmt.Println(nilS == nil, empty == nil, len(nilS), len(empty))

	j1, _ := json.Marshal(nilS)
	j2, _ := json.Marshal(empty)
	j3, _ := jsonv2.Marshal(nilS)
	fmt.Println(string(j1), string(j2), string(j3))

	nilS = append(nilS, 1) // nil에도 append 가능: zero value가 바로 쓸모 있다
	fmt.Println(nilS, slices.Equal(nilS, []int{1}))
	// Output:
	// true false 0 0
	// null [] []
	// [1] true
}

func heapInUse() int64 {
	runtime.GC()
	var m runtime.MemStats
	runtime.ReadMemStats(&m)
	return int64(m.HeapAlloc)
}

// 무엇을 보나: 16바이트짜리 부분 슬라이스 하나가 64MiB 배열 전체를 GC에서 지킨다.
// GC는 "배열의 일부만 살아 있다"를 모른다. 필요한 만큼 복사(slices.Clone)해야 풀린다.
func Example_retention() {
	before := heapInUse()
	big := make([]byte, 64<<20)
	head := big[:16]
	big = nil
	fmt.Println("부분 슬라이스만 남김, 64MiB 유지:", heapInUse()-before > 60<<20)

	head = slices.Clone(head)
	fmt.Println("Clone 후 64MiB 해제:", heapInUse()-before < 1<<20)
	runtime.KeepAlive(head)
	// Output:
	// 부분 슬라이스만 남김, 64MiB 유지: true
	// Clone 후 64MiB 해제: true
}
