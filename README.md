# gibongi

Kotlin, Go, Python을 실무에서 쓰게 되면서 기본기부터 제대로 다지려고 만든 저장소다.

문법을 외우기보다 코드가 실제로 어떻게 돌아가는지를 보려고 한다. 예를 들어 Kotlin의 `map`/`filter`가 그냥 for 문으로 짠 것과 뭐가 다른지 궁금하면, 바이트코드를 열어 보고 메모리를 얼마나 쓰는지 재 보는 식이다. 그렇다고 끝도 없이 파고들지는 않고, 그 언어로 일하는 개발자가 알아야 할 정도까지만 다룬다.

## 공부 방식

언어마다 `fundamentals`와 `projects` 두 단계로 나눴다.

`fundamentals`는 주제별 섹션으로 되어 있다. 섹션마다 설명(README), 직접 돌려 보는 예제(`examples/`), 빈 함수를 채우는 실습(`exercises/`)이 있고, 예제 출력과 실습 정답은 `./check`로 확인한다.

`projects`는 기본기를 다 끝낸 뒤에 하는 종합 과제다. 요구사항 명세를 보고 구현한 다음, 실행 결과가 명세를 만족하는지 테스트로 확인한다.

## 실행

```sh
mise install                      # go, python 버전 맞추기 (Kotlin은 JDK 25만 있으면 된다)
./check go 03                     # 섹션 하나 확인 (예제 + 실습)
./check go 03 examples            # 예제만
./check go 03 exercises           # 실습만
./check go all                    # 전체
./check python project minisql    # 프로젝트
```

통과하면 0, 실패하면 1, 명령을 잘못 썼거나 환경이 덜 갖춰졌으면 2로 끝난다. 언어별 설정과 추가 옵션은 [go](go/README.md), [kotlin](kotlin/README.md), [python](python/README.md) 폴더의 README에 적어 뒀다.

## 브랜치

- `main`: 커리큘럼. 실습 함수는 비어 있다.
- `solution`: 모범 답안. main에서 실습 함수만 채운 상태다.
- `practice`: 내가 직접 푸는 곳.

다 풀고 나면 `git diff practice solution -- <섹션 경로>`로 내 풀이와 답안을 비교한다. main이 바뀌면 나머지 두 브랜치에 merge하고, 아래 진행 현황은 main에서 고친다.

## 진행 현황

"자료"는 섹션 내용이 다 만들어졌는지(준비됨) 아직 설계 메모만 있는지(초안)를, "진행"은 내가 어디까지 했는지를 적는다.

### Go

| # | 주제 | 시간 | 자료 | 진행 |
|---|---|---|---|---|
| 01 | [Java 개발자를 위한 빠른 지도](go/fundamentals/01-quick-map) | 3h | 초안 | |
| 02 | [값, 포인터, 구조체, 메서드](go/fundamentals/02-values-and-pointers) | 4h | 초안 | |
| 03 | [슬라이스와 배열](go/fundamentals/03-slices) | 4h | 준비됨 | |
| 04 | [맵](go/fundamentals/04-maps) | 2.5h | 초안 | |
| 05 | [문자열, 정수, 비트, 바이너리 인코딩](go/fundamentals/05-data-representation) | 4h | 초안 | |
| 06 | [인터페이스](go/fundamentals/06-interfaces) | 4h | 초안 | |
| 07 | [에러, defer, panic](go/fundamentals/07-errors) | 3h | 초안 | |
| 08 | [I/O와 직렬화](go/fundamentals/08-io) | 4h | 초안 | |
| 09 | [제네릭](go/fundamentals/09-generics) | 3.5h | 초안 | |
| 10 | [함수, 클로저, 이터레이터](go/fundamentals/10-closures-and-iterators) | 4h | 초안 | |
| 11 | [메모리 관리와 GC, 프로파일링](go/fundamentals/11-memory-and-gc) | 4.5h | 초안 | |
| 12 | [고루틴과 채널](go/fundamentals/12-goroutines-and-channels) | 4.5h | 초안 | |
| 13 | [동기화와 메모리 모델](go/fundamentals/13-sync-and-memory-model) | 4h | 초안 | |
| 14 | [context](go/fundamentals/14-context) | 2.5h | 초안 | |
| 15 | [패키지, 모듈, 테스트](go/fundamentals/15-packages-and-testing) | 4h | 초안 | |

| 프로젝트 | 내용 | 자료 | 진행 |
|---|---|---|---|
| [cpu-scheduling](go/projects/cpu-scheduling) | CPU 스케줄링 시뮬레이터 | 초안 | |
| [virtual-memory](go/projects/virtual-memory) | 페이지 테이블과 페이지 교체 시뮬레이터 | 초안 | |
| [file-system](go/projects/file-system) | 디스크 이미지 위의 작은 파일 시스템과 셸 | 초안 | |

### Kotlin

| # | 주제 | 시간 | 자료 | 진행 |
|---|---|---|---|---|
| 01 | [Java 개발자를 위한 빠른 지도](kotlin/fundamentals/01-quick-map) | 4h | 초안 | |
| 02 | [타입 시스템과 null 안전성](kotlin/fundamentals/02-types-and-null-safety) | 4h | 초안 | |
| 03 | [클래스, data/sealed/object/value class](kotlin/fundamentals/03-classes-and-objects) | 5h | 초안 | |
| 04 | [함수, 람다, inline, 확장 함수](kotlin/fundamentals/04-functions-lambdas-inline) | 5h | 초안 | |
| 05 | [컬렉션과 Sequence](kotlin/fundamentals/05-collections-and-sequences) | 6h | 준비됨 | |
| 06 | [제네릭과 변성](kotlin/fundamentals/06-generics-and-variance) | 3h | 초안 | |
| 07 | [예외와 에러 모델링](kotlin/fundamentals/07-error-handling) | 3h | 초안 | |
| 08 | [위임과 DSL](kotlin/fundamentals/08-delegation-and-dsl) | 3h | 초안 | |
| 09 | [Java 상호운용](kotlin/fundamentals/09-java-interop) | 4h | 초안 | |
| 10 | [프로젝트 구성과 테스트](kotlin/fundamentals/10-project-structure-and-testing) | 3h | 초안 | |
| 11 | [코루틴 1: suspend 함수의 정체](kotlin/fundamentals/11-coroutines-under-the-hood) | 6h | 초안 | |
| 12 | [코루틴 2: 구조적 동시성과 취소](kotlin/fundamentals/12-structured-concurrency) | 6h | 초안 | |
| 13 | [코루틴 3: Flow와 Channel](kotlin/fundamentals/13-flow-and-channels) | 5h | 초안 | |
| 14 | [JVM 메모리와 GC (JDK 17 → 25)](kotlin/fundamentals/14-jvm-memory-and-gc) | 6h | 초안 | |

| 프로젝트 | 내용 | 자료 | 진행 |
|---|---|---|---|
| [matching-engine](kotlin/projects/matching-engine) | 거래소 주문 매칭 엔진 | 초안 | |

### Python

| # | 주제 | 시간 | 자료 | 진행 |
|---|---|---|---|---|
| 01 | [Java 개발자를 위한 빠른 지도](python/fundamentals/01-quick-map) | 3h | 초안 | |
| 02 | [이름, 객체, 참조](python/fundamentals/02-names-and-objects) | 4h | 초안 | |
| 03 | [내장 자료구조의 내부](python/fundamentals/03-builtin-collections) | 5h | 초안 | |
| 04 | [함수, 클로저, 데코레이터](python/fundamentals/04-functions-and-closures) | 5h | 초안 | |
| 05 | [이터레이션과 지연 평가](python/fundamentals/05-iteration-and-laziness) | 5h | 준비됨 | |
| 06 | [클래스와 속성 조회](python/fundamentals/06-classes-and-attributes) | 5h | 초안 | |
| 07 | [데이터 모델과 덕 타이핑](python/fundamentals/07-data-model-and-protocols) | 5h | 초안 | |
| 08 | [예외, 컨텍스트 매니저, 로깅](python/fundamentals/08-exceptions-and-resources) | 4h | 초안 | |
| 09 | [메모리 관리와 GC](python/fundamentals/09-memory-and-gc) | 4h | 초안 | |
| 10 | [모듈, import, 가상환경](python/fundamentals/10-modules-and-packaging) | 4h | 초안 | |
| 11 | [타입 힌트와 정적 검사](python/fundamentals/11-type-hints) | 4h | 초안 | |
| 12 | [pytest](python/fundamentals/12-testing) | 3h | 초안 | |
| 13 | [동시성 1: GIL, 스레드, 프로세스](python/fundamentals/13-threads-and-processes) | 5h | 초안 | |
| 14 | [동시성 2: asyncio](python/fundamentals/14-asyncio) | 5h | 초안 | |
| 15 | [성능 측정과 프로파일링](python/fundamentals/15-performance) | 3h | 초안 | |

| 프로젝트 | 내용 | 자료 | 진행 |
|---|---|---|---|
| [minisql](python/projects/minisql) | CSV 위에서 도는 작은 SQL 엔진 | 초안 | |

Kotlin + Spring은 이 세 언어를 마친 다음에 추가한다.
