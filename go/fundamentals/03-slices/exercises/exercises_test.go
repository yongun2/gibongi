package exercises_test

import (
	"runtime"
	"slices"
	"testing"
	"unsafe"
	"weak"

	"github.com/yongun2/gibongi/go/fundamentals/03-slices/exercises"
)

func TestWindow(t *testing.T) {
	s := []int{0, 1, 2, 3, 4, 5}
	w := exercises.Window(s, 1, 3)

	if !slices.Equal(w, []int{1, 2}) {
		t.Fatalf("Window(s, 1, 3) = %v, want [1 2]", w)
	}
	if unsafe.SliceData(w) != &s[1] {
		t.Fatalf("복사하지 말고 s의 backing array를 그대로 보여 줘야 한다")
	}
	w = append(w, 100)
	if s[3] != 3 {
		t.Fatalf("append(w, 100)이 s[3]을 %d로 덮어썼다: cap을 제한해야 한다", s[3])
	}
}

func TestFilterInPlace(t *testing.T) {
	isEven := func(v int) bool { return v%2 == 0 }

	tests := []struct {
		name string
		in   []int
		want []int
	}{
		{"섞임", []int{1, 2, 3, 4, 5, 6}, []int{2, 4, 6}},
		{"모두 남음", []int{2, 4}, []int{2, 4}},
		{"모두 버림", []int{1, 3}, []int{}},
		{"빈 입력", []int{}, []int{}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := slices.Clone(tt.in)
			got := exercises.FilterInPlace(s, isEven)
			if !slices.Equal(got, tt.want) {
				t.Fatalf("got %v, want %v", got, tt.want)
			}
			if len(got) > 0 && unsafe.SliceData(got) != unsafe.SliceData(s) {
				t.Fatalf("새 배열을 만들지 말고 s의 backing array를 재사용해야 한다")
			}
			for i, v := range s[len(got):] {
				if v != 0 {
					t.Fatalf("남은 칸 s[%d] = %d, zero value로 지워야 한다", len(got)+i, v)
				}
			}
		})
	}

	t.Run("할당 0회", func(t *testing.T) {
		s := make([]int, 1000)
		allocs := testing.AllocsPerRun(10, func() {
			for i := range s {
				s[i] = i
			}
			_ = exercises.FilterInPlace(s, isEven)
		})
		if allocs != 0 {
			t.Fatalf("allocs = %v, want 0", allocs)
		}
	})
}

func TestJobQueue_FIFO(t *testing.T) {
	var q exercises.JobQueue // zero value로 바로 사용
	if _, ok := q.Pop(); ok {
		t.Fatal("빈 큐에서 Pop이 ok=true")
	}
	for i := range 100 {
		q.Push(&exercises.Job{ID: i})
	}
	if q.Len() != 100 {
		t.Fatalf("Len = %d, want 100", q.Len())
	}
	for i := range 100 {
		j, ok := q.Pop()
		if !ok || j.ID != i {
			t.Fatalf("Pop #%d = (%v, %v), want ID %d", i, j, ok, i)
		}
	}
	if q.Len() != 0 {
		t.Fatalf("Len = %d, want 0", q.Len())
	}
}

func TestJobQueue_BoundedWhenInterleaved(t *testing.T) {
	var q exercises.JobQueue
	for i := range 100_000 { // 큐 길이는 최대 8로 유지
		q.Push(&exercises.Job{ID: i})
		if q.Len() >= 8 {
			q.Pop()
		}
	}
	if q.Len() != 7 {
		t.Fatalf("Len = %d, want 7", q.Len())
	}
	if q.Cap() > 64 {
		t.Fatalf("길이 8짜리 큐의 버퍼 cap이 %d까지 커졌다: 앞쪽 빈 칸을 재사용해야 한다", q.Cap())
	}
}

func TestJobQueue_SteadyStateNoAlloc(t *testing.T) {
	var q exercises.JobQueue
	job := &exercises.Job{ID: 1}
	for range 8 {
		q.Push(job)
	}
	allocs := testing.AllocsPerRun(10, func() {
		for range 1000 { // 길이 8을 유지하며 1000번 순환
			q.Push(job)
			q.Pop()
		}
	})
	if q.Len() != 8 {
		t.Fatalf("Len = %d, want 8", q.Len())
	}
	if allocs != 0 {
		t.Fatalf("정상 상태 순환 1000회에 할당 %v회: 버퍼를 재사용하지 않고 계속 새로 만든다", allocs)
	}
}

// pushPop은 Job을 넣고 하나를 꺼낸 뒤, 꺼낸 Job을 가리키는 약한 포인터만 돌려준다.
// (강한 참조를 테스트 함수 스택에 남기지 않으려고 별도 함수로 분리)
//
//go:noinline
func pushPop(q *exercises.JobQueue) weak.Pointer[exercises.Job] {
	j := &exercises.Job{ID: 1, Name: "popped"}
	q.Push(j)
	q.Push(&exercises.Job{ID: 2, Name: "stays"})
	got, _ := q.Pop()
	return weak.Make(got)
}

func TestJobQueue_PopReleasesJob(t *testing.T) {
	var q exercises.JobQueue
	wp := pushPop(&q)
	runtime.GC()
	if wp.Value() != nil {
		t.Fatal("Pop한 Job이 GC되지 않았다: 큐가 아직 참조하고 있다(꺼낸 칸을 nil로 지워라)")
	}
	if q.Len() != 1 {
		t.Fatalf("Len = %d, want 1", q.Len())
	}
	runtime.KeepAlive(&q)
}

func TestMatrix(t *testing.T) {
	m := exercises.NewMatrix(3, 4)
	m.Set(1, 2, 7.5)
	if got := m.At(1, 2); got != 7.5 {
		t.Fatalf("At(1,2) = %v, want 7.5", got)
	}
	if got := m.Row(1); !slices.Equal(got, []float64{0, 0, 7.5, 0}) {
		t.Fatalf("Row(1) = %v", got)
	}
	m.Row(0)[3] = 1 // Row는 복사가 아니라 뷰
	if m.At(0, 3) != 1 {
		t.Fatal("Row가 복사본을 돌려준다: 행렬 데이터를 직접 보여 줘야 한다")
	}
	r0 := m.Row(0)
	_ = append(r0, 99)
	if m.At(1, 0) != 0 {
		t.Fatal("append(Row(0), 99)가 1행을 덮어썼다: cap을 제한해야 한다")
	}
}

func TestMatrix_SingleAllocation(t *testing.T) {
	if m := exercises.NewMatrix(100, 100); len(m.Row(99)) != 100 {
		t.Fatal("NewMatrix(100, 100)의 Row(99) 길이가 100이 아니다")
	}
	allocs := testing.AllocsPerRun(20, func() {
		m := exercises.NewMatrix(100, 100)
		m.Set(99, 99, 1)
	})
	// [][]float64로 만들면 행마다 1회 + 바깥 슬라이스 1회 = 101회.
	if allocs < 1 || allocs > 2 {
		t.Fatalf("allocs = %v: 행마다 따로 할당하지 말고 rows*cols 배열 하나를 쓰라", allocs)
	}
}
