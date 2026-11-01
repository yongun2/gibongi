package exercises

// FilterInPlace는 keep이 true인 원소만 순서대로 남긴 슬라이스를 돌려준다.
//   - 새 배열을 할당하지 않는다: 결과는 s의 backing array를 재사용한다.
//   - 결과 뒤에 남은 칸(s[len(결과):len(s)])은 zero value로 지운다.
//     (Go 1.22부터 slices.DeleteFunc가 하는 일과 같다. 포인터 원소였다면 GC를 위해 필수.)
func FilterInPlace(s []int, keep func(int) bool) []int {
	// TODO: 구현하세요
	return nil
}
