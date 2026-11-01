package examples_test

import "testing"

var sink []int

const N = 1000

// 실행: ./check go 03 --bench  (내부: go test -run '^$' -bench . -benchmem)
// 관찰: allocs/op. Grow는 성장할 때마다 새 배열을 할당·복사하고, 나머지는 1회.
func BenchmarkAppendGrow(b *testing.B) {
	for b.Loop() {
		var s []int
		for i := range N {
			s = append(s, i)
		}
		sink = s
	}
}

func BenchmarkAppendPrealloc(b *testing.B) {
	for b.Loop() {
		s := make([]int, 0, N)
		for i := range N {
			s = append(s, i)
		}
		sink = s
	}
}

func BenchmarkIndexAssign(b *testing.B) {
	for b.Loop() {
		s := make([]int, N)
		for i := range s {
			s[i] = i
		}
		sink = s
	}
}
