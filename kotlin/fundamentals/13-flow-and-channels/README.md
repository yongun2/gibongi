# 13. 코루틴 3: Flow와 Channel (5h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: Flow는 Sequence와 무엇이 같고 다른가? 콜드/핫 스트림과 배압은 내부적으로 어떻게 동작하나?

**내부 동작**
- `Flow<T>`는 `suspend fun collect(collector)` 하나짜리 인터페이스다. 콜드라서 수집할 때마다 처음부터 실행된다. 연산자는 collector를 감싸는 함수다(**push**). Sequence는 iterator를 감싼다(**pull**, 05). 배압은 별도 프로토콜이 아니라 `emit`이 suspend되는 것이다.
- `take`의 업스트림 중단은 내부 예외(`AbortFlowException`)로 구현된다. 그래서 `catch`를 잘못 두면 이상해진다.
- 컨텍스트 보존: flow 안에서 `withContext`는 금지(예외)이고, `flowOn`은 업스트림만 바꾼다(내부적으로 채널 삽입). `buffer`, `conflate`, `collectLatest`.
- 예외 투명성: `catch` 연산자는 업스트림 예외만 잡는다. `onCompletion`.
- Hot: `SharedFlow`(replay, 버퍼 정책)와 `StateFlow`(conflation, 같은 값 무시), `stateIn`/`shareIn`.
- Channel: rendezvous/buffered/conflated/unlimited, `produce`, fan-in/fan-out, close와 cancel의 차이, actor 패턴.
- 비교: Java Stream(동기, 1회성), Flow(비동기 콜드), Reactor Flux(구독 기반 `request(n)` 배압). Spring WebFlux와의 연결은 kotlin-spring에서.

**예제**: E01 FlowVsSequenceOrder(05 E01과 같은 파이프라인을 Flow로, `buffer` 추가 시 순서 변화, 골든) / E02 Backpressure(빠른 생산자와 느린 소비자: 기본/`buffer(2)`/`conflate`/`collectLatest`, 가상 시간 trace 골든) / E03 ContextPreservation(`withContext` 위반 예외 메시지, `flowOn` 스레드 이름, 골든) / E04 StateFlowConflation(가상 시간, 골든) / E05 ChannelFanOut(단일 스레드 디스패처로 결정적, 골든).

**실습**: X01 Flow 연산자 직접 구현(`myMap/myFilter/myTake`) — 05 X01의 push 버전. 테스트: 순서, take 후 업스트림 중단. / X02 `debounce`/`throttleFirst` — 테스트: 가상 시간. / X03 로그 라인 Flow → 초 단위 윈도 집계 → StateFlow 대시보드 상태. 테스트: 가상 시간 스냅샷.
