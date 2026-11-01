package exercises

// Window는 s[i:j]와 같은 원소를 "복사 없이" 보여 주는 슬라이스를 돌려준다.
// 단, 호출자가 결과에 append해도 s의 j번째 이후 원소는 절대 바뀌면 안 된다.
func Window(s []int, i, j int) []int {
	return s[i:j:j]
}
