# 14. JVM 메모리와 GC — Java 17 → 23 → 25+ ★ 필수 섹션 (6h)

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**
1. 내 Kotlin 코드가 만든 객체는 어디에 할당되고, 누가 언제 어떻게 치우나?
2. G1과 Generational ZGC는 **구조적으로** 무엇이 다르고, 그 차이가 지연시간·처리량·메모리에 어떤 차이를 만드나?
3. Java 17 → 23에서 무엇이 바뀌었고(기본값 포함), 23 이후(로컬 25, 26)에는 무엇이 더 바뀌었나?
4. 박싱, 람다 캡처, 컬렉션 중간 할당 같은 Kotlin 코드 습관이 GC 부하로 이어지는 경로는? 언제 신경 써야 하고 언제 무시해도 되나?

## 14.1 README 내부 동작 ① 공통 기반 (GC 이전에 알아야 할 것)
- **할당 경로**: 스레드별 TLAB에서 포인터를 증가시키는 bump-pointer 할당 → 매우 싸다. 큰 배열은 TLAB 밖이나 G1의 humongous region으로 간다.
- **객체 레이아웃**: 헤더(mark word + class pointer)는 기본 12B(64bit + compressed class pointers), compact object headers면 8B. 8B 정렬, 참조는 compressed oops면 4B이고 아니면 8B. → `Integer` 16B, `Long` 24B(compact면 16B). 05 E03의 "원소당 B"가 이 숫자의 조합이라는 것을 직접 맞춰 본다.
- **약한 세대 가설**: 대부분의 객체는 금방 죽는다 → young 영역을 자주, 싸게 수집한다. 복사 수집 비용은 **살아 있는 객체**에 비례하고, 죽은 객체는 비용이 거의 없다. 그래서 "짧게 사는 쓰레기"는 생각보다 싸다.
- **배리어**: GC가 앱과 동시에 일하거나 세대를 나누려면 참조를 읽거나 쓸 때 끼어드는 코드(write/load barrier)가 필요하다. 이것이 처리량 비용의 근원이다.
- **STW vs concurrent, safepoint**: 일시정지는 "모든 앱 스레드를 멈춘 시간"이다.

## 14.2 README 내부 동작 ② G1 (JDK 9부터 기본 GC, JEP 248)
- 힙 = 같은 크기의 region(힙 크기로 자동 결정. 128MB 힙이면 1MB, 로그 `gc+init`으로 확인함). 각 region의 역할: eden/survivor/old/humongous(region 크기의 절반 이상인 객체).
- **young 수집 = STW evacuation**: eden과 survivor의 살아 있는 객체를 복사한다. 일시정지 시간은 살아 있는 객체 양에 비례하고, 목표치(`MaxGCPauseMillis`, 기본 200ms, soft goal)에 맞춰 young 크기를 조절한다.
- **old 수집**: concurrent marking(SATB) → mixed collection(young + 일부 old region을 STW로 evacuation) → 실패하면 Full GC.
- **세대 간 참조**: post-write barrier가 card table을 더럽히고, concurrent refinement 스레드가 region별 remembered set(네이티브 메모리)에 반영한다. pre-write barrier는 SATB 마킹용.
- 실패 모드: evacuation failure, humongous 단편화, Full GC.

**JDK 17 → 23 동안 G1에서 바뀐 것** (알고리즘의 뼈대는 같고 메모리와 일시정지 예측성이 좋아졌다. 출처: G1 메인테이너 Thomas Schatzl의 릴리스별 정리)

| JDK | 변화 | 효과 |
|---|---|---|
| 18 | remembered set 재작성(JDK-8017163), region 최대 크기 32MB → 512MB(JDK-8275056) | 저자 벤치마크(20GB)에서 remset 네이티브 메모리 피크가 약 2GB에서 1.3GB로 감소 |
| 20 | 전체 힙 마킹 비트맵 하나 제거(JDK-8210708), refinement 스레드 적응 제어(JDK-8137022), preventive GC 기본 비활성(JDK-8293861) | 네이티브 메모리를 힙의 약 1.5% 절약, 불필요한 CPU 감소 |
| 21 | Full GC가 humongous 객체도 이동 | region 단편화로 인한 OOME 위험 감소 |
| 22 | **JEP 423 Region Pinning** — JNI critical 구간에서 GC 전체를 막던 방식 대신 해당 region만 고정(JDK-8318706). evacuation 실패 region을 다음 GC에서 회수(JDK-8140326) | "JNI 때문에 Java 스레드가 멈추는 일이 없어짐" |
| 23 | G1은 조용한 릴리스. Parallel GC의 Full GC를 G1식 알고리즘으로 교체(JDK-8329203) | – |

## 14.3 README 내부 동작 ③ ZGC: JDK 17의 단일 세대 → Generational
- **JDK 17의 ZGC**(JDK 15 product, JEP 377): 세대 구분이 없어서 **매 사이클 힙 전체를 마킹**한다. 포인터 상위 비트에 색(메타데이터)을 넣고, 같은 물리 메모리를 가상 주소 3곳에 매핑(multi-mapping. `ps`의 RSS가 부풀어 보이는 이유)한다. **load barrier만** 있다. 동시 마킹과 동시 재배치로 일시정지가 1ms 미만이다. 대신 할당 속도를 못 따라가면 **allocation stall**(할당한 스레드가 GC를 기다림)이 생긴다.
- **Generational ZGC** (JEP 439, JDK 21에서 `-XX:+ZGenerational`로 opt-in → **JEP 474, JDK 23에서 `-XX:+UseZGC`의 기본 모드** → **JEP 490, JDK 24에서 단일 세대 모드 제거**, `ZGenerational`은 obsolete 경고만 냄). JEP 439가 설명하는 구조 변화:
  - young과 old를 독립적으로 수집한다. young 수집이 자주 돌아 빨리 메모리를 돌려준다.
  - **store barrier 추가**: old → young 참조를 기록(remembered set)하고 마킹 일부를 담당한다. load barrier는 메타데이터 제거와 오래된 포인터 수정만 한다.
  - remembered set = old region마다 **비트맵 2개를 번갈아 쓰는 double-buffering**. card table 방식에 필요한 메모리 펜스가 없다.
  - **multi-mapping 제거**, 메타데이터를 포인터 하위 비트로 옮기고 스택이나 레지스터의 참조는 색이 없다 → `ps`에 힙 사용량이 정확히 보인다.
  - 너무 꽉 찬 region은 옮기지 않고 그 자리에서 나이를 먹인다(dense region in-place aging).
  - JEP가 밝힌 효과: allocation stall 위험 감소, 필요한 힙 감소(Cassandra 벤치마크에서 **4분의 1 힙으로 4배 처리량**, 일시정지는 여전히 1ms 미만), GC CPU 감소. 대신 배리어가 무거워지고 일부 "본질적으로 비세대적인" 워크로드는 약간 느려질 수 있다.
- **G1 vs Generational ZGC 구조 비교**

| | G1 | Generational ZGC |
|---|---|---|
| young 수집 | STW evacuation | 동시 mark·relocate (짧은 동기화 일시정지만) |
| old 수집 | concurrent mark + STW mixed evacuation | 동시 major collection |
| 배리어 | write barrier(SATB pre + card post) | load barrier + store barrier |
| 세대 간 참조 | card table + region별 remset(네이티브) | old region별 double-buffered 비트맵 |
| 참조 크기 | compressed oops(4B, 약 32GB 미만 힙) | **colored pointer라 compressed oops 미사용 → 8B**(로컬에서 `UseCompressedOops=false` 확인) |
| 일시정지 | 목표 200ms(soft), 보통 수 ms~수십 ms, 살아 있는 객체량에 비례 | 1ms 미만, 힙 크기와 무관 |
| 실패 모드 | humongous 단편화, evacuation failure → Full GC | allocation stall |
| 처리량 | 높음(Parallel 다음) | 배리어와 동시 작업 CPU 비용으로 다소 낮음 |
| 메모리 | remset 네이티브(릴리스마다 감소), 4B 참조 | 헤드룸 필요 + 8B 참조 |
| 언제 | 범용 기본값. 처리량과 지연의 균형 | 큰 힙 + 엄격한 꼬리 지연(p99.9), CPU 여유가 있을 때 |

- Parallel(최대 처리량, 긴 STW), Serial(작은 힙·단일 CPU), Shenandoah(동시 압축, region 기반, generational 모드는 JDK 24 실험(JEP 404) → JDK 25 product(JEP 521, 기본 모드는 아님) → JDK 28에서 기본 모드로 targeted(JEP 535))는 비교표 한 줄씩으로만 다룬다.

## 14.4 README 내부 동작 ④ JDK 23 이후의 핵심 변화 (로컬 25·26)

| JDK | JEP / 변경 | 의미 |
|---|---|---|
| 24 | JEP 490 ZGC 단일 세대 제거 | `-XX:-ZGenerational`도 generational로 동작(경고) |
| 25 | **JEP 519 Compact Object Headers** product (`-XX:+UseCompactObjectHeaders`, unlock 불필요, 기본값 아님) | JEP: SPECjbb2015에서 힙 22% 감소, CPU 8% 감소. **로컬 측정: `Long` 24B → 16B, Sequence 체인 12.0 → 8.0 B/원소** |
| 25 | **JEP 521 Generational Shenandoah** product | `-XX:ShenandoahGCMode=generational` |
| 26 | **JEP 522 G1 동기화 감소로 처리량 개선** | card table 2개를 쓰고 핸드셰이크로 교체. x64 write barrier가 약 50개 → 12개 명령어. 참조 필드를 많이 쓰는 앱에서 5~15%, 그 외 최대 약 5% |
| 27 (2026-09 GA) | **JEP 523 G1을 모든 환경의 기본 GC로** | 이전에는 CPU 1개 또는 메모리 1792MB 미만이면 Serial이 선택됨(**로컬 JDK 25에서 `-XX:ActiveProcessorCount=1` → Serial 확인**) |

그 밖의 변화(JEP 404·475·450·491, G1 remset 개선, JEP 516·534·535)는 이름만 짚고 넘어간다. 실무 판단에 필요한 것은 위 다섯 줄이다.

## 14.5 README ⑤ Kotlin 코드 습관 → GC 부하 (이 환경에서 측정한 값)

| 습관 | 무엇이 생기나 | 측정값 | 대안 |
|---|---|---|---|
| 대형 `List`에 Iterable 체인(`filter.map.sum`) | 단계마다 ArrayList + 박싱 | 20.5 B/원소(G1), **29.0 (ZGC)**, 16.5 (compact) | Sequence, 루프, primitive 배열 |
| 같은 체인을 수백만 원소에 반복 | region 절반 이상의 backing 배열 | G1 로그 `(G1 Humongous Allocation)`, 작은 힙이면 Full GC | 청크 처리, Sequence |
| `Sequence<Int>.map { it.toLong() * it }` | 원소당 `Long` 박스 | 12.0 B/원소(짝수 50%) | `IntArray` + 루프 / `sumOf` |
| Kotlin 람다 → Java `Stream<Integer>.mapToLong` | Function1 + 어댑터 + `Long` 박싱 | 12.0 B/원소 | SAM 생성자, `IntStream` |
| `Map<String, Int>`에 `+1` | 128 이상이면 매번 `Integer` 박싱 | 05 X04에서 예산 초과로 확인 | `groupingBy().eachCount()`(키마다 `Ref.IntRef` 하나) |
| 캡처 람다 생성 | 람다 객체 | 16 B/생성 | 비캡처, inline |
| `(Int) -> Int` 함수 값 호출 | 인자와 반환값 박싱 | 48 B / `f(f(x))` | inline 고차 함수 |
| `enum.values()` | 배열 복사 | 32 B/호출 | `entries` |
| `List<UserId>`(value class) | `box-impl` | 박스 1개/원소 | `LongArray`, 전용 컬렉션 |
| suspend 호출이 실제로 중단될 때 | continuation 객체 | 11 E01에서 측정 | – |

**언제 신경 쓰나**: 기본은 가독성이 우선이다(young에서 죽는 객체는 싸다). 다음 경우에는 측정하고 줄인다. ① 할당률이 GC 빈도를 끌어올려 G1 young GC가 잦아지거나 ZGC가 allocation stall을 낼 때 ② 큰 중간 결과가 young GC를 넘겨 살아남아 old로 승격될 때(배치, 캐시) ③ G1 humongous 할당 ④ 지연 민감 핫패스.

## 14.6 예제

GC 섹션은 **jvmTarget 17로 컴파일**해서 같은 클래스를 JDK 17·25·26에서 모두 실행한다(로컬에 3개 모두 있음). 예제 헤더 문법에 JDK 선택을 추가한다: `// @run <label>@<jdk>: <JVM 옵션>` (예: `// @run zgc@17: -XX:+UseZGC`). 그러려면 `support`를 두 모듈로 나눈다: `support:measure`(JDK 17 타깃), `support:bytecode`(Class-File API, JDK 25).

| 파일 | 무엇을 보여주나 | 실행 변형 | 관찰 (stdout 골든 / stderr) |
|---|---|---|---|
| E01WhichGcAmI | 선택된 GC와 그 구조(MXBean 이름, 메모리 풀, 관련 플래그) | g1, zgc, parallel, serial, shenandoah, shenandoah-gen, onecpu, zgc@17, onecpu@17 | **골든**. 로컬 실측: JDK 17 ZGC = `ZGC Cycles [ZHeap]`, `ZGC Pauses` (풀 1개 = 세대 없음) / JDK 25 ZGC = `ZGC Minor Cycles`, `ZGC Major Cycles`, `… Pauses` + 풀 `ZGC Young Generation`, `ZGC Old Generation` / ZGC는 `UseCompressedOops` 꺼짐 / `ActiveProcessorCount=1` → `Copy`, `MarkSweepCompact`(Serial) |
| E02ObjectLayout | `Any()`, `Integer`, `Long`, `LongArray(1)`, 빈 `ArrayList`, `List<Long>` 원소 1개의 실제 바이트 | g1, compact, zgc, g1@17 | **골든**(할당량으로 측정, `-XX:-DoEscapeAnalysis`). 헤더 8/12B, 참조 4/8B, 정렬을 숫자로 확인 |
| E03AllocationToGcLog | "주문 리포트" 워크로드를 Iterable 체인 / Sequence / primitive 루프 3가지로 | g1, zgc, zgc@17 (각 `-Xmx128m -Xlog:gc:stderr`) | stdout은 체크섬만 골든. stderr의 GC 로그: G1 `Pause Young (Normal) (G1 Evacuation Pause) 35M->3M(64M) 1.7ms`, `(G1 Humongous Allocation)` / ZGC 25 `Minor Collection (Allocation Rate)`, `Allocation Stall (main) 5.1ms` / ZGC 17 `Garbage Collection (Allocation Stall) 64M(100%)->34M(53%)` (모두 로컬에서 실제로 관찰) |
| E04LatencyUnderChurn | 요청 처리 루프의 지연 분포(p50/p99/max) + 백그라운드 중수명 캐시 churn | g1, zgc, parallel, zgc@17 | stdout은 처리 건수만 골든. stderr: 지연 히스토그램, 프로세스 CPU 시간. Parallel은 max가 크고, ZGC는 max가 작지만 CPU를 더 쓰는 트레이드오프를 직접 본다 |
| E05HumongousAndPromotion | 큰 중간 리스트 → humongous, 청크 처리로 해소 | g1 (`-Xlog:gc,gc+heap=info`) | stderr의 `Humongous regions: 16->16`, GC 원인 비교 |
| E06JfrAllocationSites | JFR로 할당 지점 찾기 → 05의 박싱 지점이 상위에 나오는지 | `-XX:StartFlightRecording:filename=build/e06.jfr,settings=profile` | README의 절차: `$JAVA_HOME/bin/jfr view allocation-by-site build/e06.jfr`, `jfr view gc`(JDK 25에서 동작 확인) |

## 14.7 실습
- **X01 `GcLogParser`** — `-Xlog:gc` 한 줄씩을 sealed `GcEvent`(Pause, ConcurrentCycle, ZCollection(minor/major), AllocationStall …)로 파싱하고 `GcSummary`(일시정지 수, 합계, 최대, p99, stall 수와 합계)를 계산. 테스트 픽스처는 로컬 JDK 17/25/26에서 실제로 뽑은 G1·ZGC 로그. 테스트: 이벤트 분류, 요약 수치, 처음 보는 줄 무시.
- **X02 `AllocationDiet`** — 동작하지만 할당이 많은 `OrderReport.build(orders)`(groupBy, Pair, data class copy, `Map<String, Long>` 누적, 템플릿)를 제공한다. 사용자는 같은 출력을 내는 `buildLean`을 작성한다. 테스트: 무작위 입력 1만 건에서 결과 동일 + **할당이 원본의 10% 미만**.

## 14.8 Java 습관 함정 (GC 편)
- `System.gc()`로 측정하기(ZGC는 young을 먼저 승격시킨다. JEP 439), `-Xmx`만 올리고 원인을 보지 않기, GC 로그 없이 튜닝 플래그 복사하기, "ZGC는 일시정지가 없으니 메모리를 마음껏 써도 된다"(allocation stall과 8B 참조), JDK 17 시절 ZGC 지식(multi-mapping RSS, 비세대)을 23+에 적용하기.
