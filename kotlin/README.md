# Kotlin

Kotlin 2.4.21, Gradle 9.7.1, JDK 25를 쓴다. 테스트는 JUnit 6에 `kotlin.test` 단언을 붙여 쓰고, 코루틴은 kotlinx.coroutines 1.11.0이다. 섹션 목록과 진행 현황은 [루트 README](../README.md#kotlin)에 있다.

## 준비

JDK 25만 있으면 된다. Gradle과 Kotlin 컴파일러는 `gradlew`가 알아서 받아 온다. 14장(GC)은 JDK 17, 25, 26을 나란히 돌려 보는데, 로컬에 설치된 JDK를 Gradle이 찾아서 쓴다.

## 실행

```sh
./check kotlin 05                         # 예제 + 실습
./check kotlin 05 examples [예제 이름]      # 예제만
./check kotlin 05 exercises [테스트 이름]   # 실습만
./check kotlin 05 javap <클래스 FQCN>      # 바이트코드 보기
./check kotlin 05 update-golden           # 기대 출력 다시 만들기
./check kotlin all
```

`update-golden`은 Kotlin이나 JDK 버전을 올려서 기대 출력이 달라졌을 때 쓴다. 다시 만든 다음에는 diff를 보고 무엇이 바뀌었는지 확인한다.

## 폴더 구조

```
build-logic/          섹션 공통 빌드 설정 (gibongi.section 플러그인)
support/              할당량 측정, 바이트코드 요약 같은 관찰 도구
fundamentals/NN-주제/
  build.gradle.kts    plugins { id("gibongi.section") } 한 줄
  README.md
  examples/           main 함수와 기대 출력(*.expected)
  exercises/src/      빈 함수
  exercises/test/     테스트
```

`build.gradle.kts`가 있는 섹션만 빌드에 자동으로 들어가고, 아직 README만 있는 섹션은 빠진다.

예제가 stderr로 내는 값(실행 시간, GC 로그 같은 것)은 실행할 때마다 달라서 화면에 보여 주기만 하고 검증하지 않는다. 할당량을 재는 테스트는 HotSpot 전용 API를 쓰기 때문에 Temurin 같은 HotSpot JDK에서만 돈다.
