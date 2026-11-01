# 06. 인터페이스: 암묵적 구현, 내부 표현, 비용

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 02 · 4h · 관련 변화: PGO 기반 devirtualization(1.21 GA), `errors.AsType`(1.26, 07장)

**핵심 질문**

1. 인터페이스 값은 메모리에서 무엇인가? 왜 `err != nil`인데 안에 든 포인터는 nil일 수 있나?
2. `implements` 없이 인터페이스를 만족한다는 것은 컴파일러가 언제 무엇을 확인한다는 뜻인가?
3. 인터페이스를 통한 호출은 직접 호출보다 얼마나 비싸고, 값을 인터페이스에 넣으면 왜 힙 할당이 생기나?
4. 인터페이스는 누가(생산자/소비자) 어디에 정의해야 하나?

**README**

- ① 내부 동작
  - **표현**
    - 인터페이스 값은 2워드(16B)다 **[로컬]**.
    - 메서드가 있는 인터페이스는 (itab, data), `any`는 (type, data)다.
    - itab은 (인터페이스 타입, 동적 타입, 메서드 포인터 표)이고, 처음 쓰일 때 만들어져 캐시된다.
  - **nil 인터페이스**는 두 워드가 모두 nil인 값이다. `var p *MyErr = nil; var e error = p`는 타입 워드가 채워지므로 `e != nil`이다(typed nil) **[로컬]**.
  - **값 저장(박싱)**
    - data 워드는 포인터다. 포인터가 아닌 값을 넣으면 복사본을 힙에 할당해 가리킨다.
    - 런타임 최적화로 할당이 생략되는 경우가 있다: 0바이트 값, 작은 정수 등. 정확한 조건은 구현 시 `AllocsPerRun`으로 확인한다 **[미확인]**.
    - `fmt.Println(x)`가 `x escapes to heap`을 내는 이유가 이것이다.
  - **호출 비용**
    - 인터페이스 메서드 호출은 itab의 함수 포인터를 통한 간접 호출이고 인라인할 수 없다.
    - 컴파일러가 동적 타입을 증명하면 devirtualize한다(`devirtualizing s.Area to Sq` **[로컬]**).
    - PGO는 핫 경로에서 조건부 devirtualization을 한다.
  - **만족 검사**
    - 정적 대입에서는 컴파일 타임에 메서드 집합(02장)을 확인한다.
    - 타입 단언과 타입 스위치는 런타임에 itab을 비교한다.
    - `var _ io.Reader = (*T)(nil)`은 컴파일 타임 단언 관용구다.
  - **비교**: 동적 타입과 값을 함께 비교한다. 동적 타입이 비교 불가(슬라이스 등)이면 런타임 panic이다.
  - **구조체에 인터페이스 임베딩**: 데코레이터나 부분 구현에 쓴다. 구현하지 않은 메서드를 부르면 nil 호출로 panic이 난다.
- ② 차이
  - Java는 명목적이다: 선언한 `implements`만 인정한다. Go는 구조적이다: 남의 타입도 나중에 만족시킬 수 있다. 그래서 인터페이스를 **소비자 쪽에 작게**(1~2 메서드) 정의하고, 테스트 대역을 쉽게 만든다.
  - Java JIT는 인라인 캐시와 CHA로 단형 인터페이스 호출 대부분을 인라인한다. Go AOT는 증명할 수 있을 때만 devirtualize하므로, 핫 루프에서 인터페이스 호출 비용이 Java보다 드러난다.
  - Java null은 하나다. Go에는 "nil 인터페이스"와 "nil을 담은 인터페이스" 두 종류가 있다.
  - 상속 계층과 추상 클래스 대신 작은 인터페이스를 조합한다(`io.ReadWriteCloser`).
- ③ 판단 기준
  - "Accept interfaces, return structs".
  - 인터페이스는 구현이 둘 이상 필요해지거나 테스트 경계가 생길 때 **소비자 패키지에** 만든다. 생산자 쪽에 미리 만들지 않는다(interface pollution).
  - 핫 루프에서는 구체 타입이나 제네릭(09장 비교)을 쓰거나, 루프 밖에서 타입 스위치를 한 번 한다.
  - 반환형을 구체 포인터 타입으로 했다가 `error`로 감싸 반환하지 않는다(typed nil).
- 함정
  - typed nil 반환.
  - 포인터 리시버 메서드만 있는 타입을 값으로 인터페이스에 넣는다(`method Inc has pointer receiver`).
  - 인터페이스에 든 값을 고친다(복사본이 바뀐다).
  - 비교 불가 동적 타입을 `==`로 비교해 panic.
  - 메서드 10개짜리 인터페이스.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `ifaceWords` | `Sizeof(any)=16`, `unsafe`로 (type, data) 워드를 읽어 typed nil의 타입 워드가 non-nil임을 출력 | `unsafe` |
| `typedNil` | `find(false)`의 `err == nil` → false, 고친 버전 → true **[로컬]** | 출력 |
| `methodSetCompileError` | `Counter does not implement Incrementer (method Inc has pointer receiver)` | `observe.CompileErrors` |
| `boxingAllocs` | `var a any = x`의 할당 횟수: 큰 int, 작은 int, 포인터, 구조체 | `AllocsPerRun` |
| `devirtualize` | 정적으로 알 수 있는 호출은 devirtualize, 슬라이스에 섞인 것은 간접 | `observe.Diag(-m)` |
| `typeSwitch` | 동적 타입 분기, 단언 실패 `ok=false` vs panic | 출력 |
| `uncomparablePanic` | `any([]int{1}) == any([]int{1})` → `comparing uncomparable type []int` | recover |
| `Benchmark*` | 직접 호출 / 인터페이스 호출 / 루프 밖 타입 스위치 후 직접 호출 | `--bench`, `-gcflags=-S`에서 레지스터 간접 `CALL` |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `Policy` 전략 | `Pick(ready []*Proc) int`를 FIFO/SJF/Priority로 구현 | 각 정책의 선택, **동률이면 FCFS**, 빈 큐 (CPU 스케줄러 알고리즘 교체 직결) |
| `ValidateAll` | 여러 검사 결과를 모은 에러, 오류가 없으면 **진짜 nil** 반환 | `err == nil`(typed nil이면 실패), 오류 있을 때 메시지 |
| `BlockDevice` 대역 | 인터페이스 `ReadBlock/WriteBlock/NumBlocks` + `MemDisk` + 호출 수를 세는 `CountingDevice`(임베딩) | 왕복, 범위 밖 에러, 읽기/쓰기 횟수, `var _ BlockDevice = (*MemDisk)(nil)` (SFS 테스트 대역) |
