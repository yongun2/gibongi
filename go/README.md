# Go

Go 1.27.1을 쓴다. 버전은 저장소 루트의 `mise.toml`에 고정해 뒀으니 루트에서 `mise install`만 하면 된다. 섹션 목록과 진행 현황은 [루트 README](../README.md#go)에 있다.

## 실행

```sh
./check go 03                     # 예제 + 실습
./check go 03 examples            # 예제만
./check go 03 exercises           # 실습만
./check go 03 --race              # 레이스 검출 켜기
./check go 03 examples --bench    # 벤치마크까지
./check go 03 -run TestWindow -v  # 테스트 하나만 자세히
./check go all
```

동시성 섹션처럼 폴더에 `.race` 파일이 있으면 `--race`를 안 붙여도 항상 켜진다.

## 폴더 구조

```
fundamentals/NN-주제/
  README.md
  examples/     Example 함수의 // Output: 주석으로 출력을 검증한다
  exercises/    빈 함수(*.go)와 테스트(*_test.go)
```

`internal/observe`는 escape analysis 같은 도구 출력을 예제 안에서 검증할 때 쓰는 헬퍼다. 이렇게 도구 출력에 기대는 예제는 Go 버전을 올리면 깨질 수 있는데, 그럴 때는 뭐가 바뀌었는지 들여다보는 것 자체가 공부가 된다.

`projects/`에는 기본기를 끝낸 뒤에 할 OS 시뮬레이터 세 개가 들어간다.
