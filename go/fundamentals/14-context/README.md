# 14. context: 취소·데드라인·요청 범위 값

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 13 · 2.5h · `.race` · 관련 변화: `WithCancelCause`(1.20), `AfterFunc/WithoutCancel/WithDeadlineCause`(1.21), `T.Context`(1.24), `signal.NotifyContext` cause(1.26)

**핵심 질문**

1. context는 내부적으로 어떤 트리이고, 취소는 어떻게 자식에게 전파되나?
2. 왜 ThreadLocal 같은 암묵 전달이 아니라 첫 번째 인자로 명시 전달하나?
3. 취소는 "강제 종료"인가? 취소된 고루틴이 실제로 멈추려면 코드가 무엇을 해야 하나?
4. cancel을 호출하지 않으면 무엇이 새나?

**README**

- ① 내부 동작
  - **구조**
    - 메서드: `Deadline/Done/Err/Value`. 불변 노드가 부모 쪽으로 연결된 트리다.
    - `cancelCtx`는 자식 집합과 지연 생성되는 done 채널을 가진다.
    - 취소하면 done을 close하고, 자식을 재귀로 취소하고, 부모의 자식 집합에서 자신을 뺀다.
  - **데드라인**: `WithTimeout/Deadline` = `cancelCtx` + `time.AfterFunc` 타이머.
  - **Value**: 부모 쪽으로 거슬러 올라가는 **선형 탐색**(O(깊이))이다. 요청 범위 메타데이터용이고, 의존성 주입용이 아니다. 키는 비공개 타입으로 만든다.
  - **Cause와 확장 API**
    - `Cause`(1.20), `AfterFunc`(1.21: 취소되면 콜백 실행 → 블로킹 I/O 깨우기).
    - `WithoutCancel`(1.21: 값은 물려받고 취소는 끊는다 → 응답 후 백그라운드 작업).
    - `t.Context()`(1.24: 테스트가 끝나면 취소).
  - **취소는 협력적이다**: Done을 select하거나 ctx를 받는 API를 부를 때만 반응한다. CPU 루프는 주기적으로 `ctx.Err()`를 확인해야 한다.
  - **cancel 미호출**: 부모의 자식 집합에 남고 타이머도 유지된다 → 부모가 끝날 때까지 샌다. vet `lostcancel`이 잡는다.
- ② 차이
  - Java는 `Thread.interrupt`(플래그 + `InterruptedException`), `Future.cancel`, ThreadLocal/MDC/ScopedValue로 암묵 전파하고, 타임아웃 방식이 API마다 다르다.
  - Go는 표준 타입 하나로 취소, 데드라인, 값을 **명시 전달**한다. 고루틴에 ID가 없으니 ThreadLocal이 불가능하다.
  - Java 구조적 동시성(`StructuredTaskScope`)에 대응하는 것은 `errgroup.WithContext` 패턴이다(실습).
- ③ 판단 기준
  - I/O나 오래 걸리는 작업을 하는 함수는 첫 인자로 ctx를 받는다. 구조체 필드에 저장하지 않는다.
  - `WithTimeout`은 호출 경계에서 만들고, cancel은 즉시 `defer`한다.
  - 계속 살아야 할 후속 작업은 `WithoutCancel`로 분리한다.
  - `Value`는 요청 메타데이터에만 쓴다.
  - 라이브러리는 `ctx.Err()`를 그대로 반환하거나 `%w`로 감싸, 호출자가 `errors.Is(err, context.Canceled)`로 판별할 수 있게 한다.
- 함정
  - cancel 누락.
  - nil ctx(`context.TODO()`를 쓴다).
  - Value 남용.
  - 취소 후에도 select 없는 채널 송신에서 막힌다(누수).
  - 중간에 `Background()`를 새로 만들어 전파가 끊긴다.
  - 요청 ctx로 백그라운드 작업을 시작해 응답 직후 취소된다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `cancelPropagation` | 부모 취소 → 손자까지 Err, 자식 취소는 부모에 영향 없음 | 출력 |
| `deadlineSynctest` | 5초 타임아웃이 가상 시간으로 즉시, `DeadlineExceeded`와 `Cause` | 출력 **[로컬: synctest + WithTimeout 동작 확인]** |
| `valueLookup` | 깊이 1/10/100 체인에서 Value 탐색 | 벤치로 O(깊이) |
| `afterFunc` | 취소 시 `net.Pipe` 연결 Close → 막힌 Read가 깨어남 | 출력 |
| `withoutCancel` | 부모 취소 후에도 자식 살아 있음, 값은 유지 | 출력 |
| `lostcancelVet` | `the cancel function ... is not used on all paths` | `observe.Vet` |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| 미니 errgroup `Group` | `WithContext`, `Go(func(ctx) error)`, `Wait()`, `SetLimit(n)` | 첫 에러 반환, 첫 에러 시 다른 작업이 ctx 취소를 관찰, 동시 실행 ≤ n, 누수 없음(synctest), `-race` |
| `Retry(ctx, n, backoff, fn)` | 지수 백오프 재시도 | (synctest) 가상 시계상 대기 시간이 정확, 취소 시 즉시 `ctx.Err()` + Cause 보존 |
| `Pipeline(ctx, src, stages...)` | 단계별 고루틴 파이프라인 | 중간 취소 후 모든 단계 종료(버블 누수 검사), 결과 순서 |
