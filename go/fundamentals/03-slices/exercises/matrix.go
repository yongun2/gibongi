package exercises

// Matrix는 rows x cols 실수 행렬이다. [][]float64 대신 길이 rows*cols인
// 하나의 []float64에 행 우선(row-major)으로 저장한다.
type Matrix struct {
	rows, cols int
	data       []float64
}

// NewMatrix는 0으로 채운 행렬을 만든다. 힙 할당은 backing array 한 번이면 충분하다.
func NewMatrix(rows, cols int) *Matrix {
	// TODO: 구현하세요
	return &Matrix{}
}

func (m *Matrix) At(r, c int) float64 {
	// TODO: 구현하세요
	return 0
}

func (m *Matrix) Set(r, c int, v float64) {
	// TODO: 구현하세요
}

// Row는 r행을 복사 없이 보여 준다. 호출자가 결과에 append해도
// 다음 행(r+1)이 바뀌면 안 된다.
func (m *Matrix) Row(r int) []float64 {
	// TODO: 구현하세요
	return nil
}
