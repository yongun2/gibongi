// Package observe는 예제(Example 함수)가 Go 툴체인의 진단 출력을
// "기대 출력"으로 검증할 수 있게 해 주는 작은 헬퍼 모음이다.
//
// 툴 출력은 Go 버전에 따라 바뀔 수 있다. 바뀌어서 예제가 깨지면
// 그 자체가 관찰 대상이다: 릴리스 노트에서 무엇이 바뀌었는지 찾아라.
package observe

import (
	"bufio"
	"bytes"
	"fmt"
	"os/exec"
	"path/filepath"
	"strings"
)

// Escapes는 현재 디렉터리의 패키지를 -gcflags=-m 으로 빌드하고,
// file에 대한 탈출 분석 결과 중 "escape"/"moved to heap" 줄만 돌려준다.
// 각 줄은 "줄:열: 메시지" 형식으로 경로를 떼어 낸다.
func Escapes(file string) []string {
	// -a 없이도 go 명령은 캐시된 컴파일러 출력을 재생(replay)하므로 매번 같은 결과를 본다.
	cmd := exec.Command("go", "build", "-gcflags=-m", ".")
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return []string{fmt.Sprintf("go build 실패: %v\n%s", err, stderr.String())}
	}
	var out []string
	sc := bufio.NewScanner(&stderr)
	for sc.Scan() {
		line := sc.Text()
		path, rest, ok := strings.Cut(line, ":")
		if !ok || filepath.Base(path) != file {
			continue
		}
		if strings.Contains(rest, "escape") || strings.Contains(rest, "moved to heap") {
			out = append(out, strings.TrimSpace(rest))
		}
	}
	return out
}
