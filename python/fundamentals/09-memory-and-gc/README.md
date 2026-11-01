# 09. 메모리 관리와 GC · 4h · 선행 02, 06, 08

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: 객체는 언제 해제되나? JVM GC 와 무엇이 다른가? Python 에서 메모리 누수는 어떻게 생기고, 어떻게 찾나?

**README**
- **두 겹 구조**
  1. 참조 카운팅: refcount 가 0이 되는 즉시 해제된다(결정론적. `weakref.finalize` 와 `__del__` 이 즉시 호출된다).
  2. 순환 GC: refcount 로 풀리지 않는 **순환만** 담당한다. 컨테이너 객체만 추적하고(`gc.is_tracked`) 세대별로 동작한다. 로컬 3.15.2 에서 `gc.get_threshold() == (2000, 10, 10)`, `len(gc.get_stats()) == 3`. `gc.collect()` 는 수거한 개수를 돌려준다.
- **JVM 과의 대비**: JVM 은 추적식 GC 하나로 해제하고, 해제 시점이 비결정적이며, 객체를 이동하고 압축한다. CPython 은 객체를 **이동하지 않는다**(`id` 가 안정적인 이유). 그래서 단편화가 생기고 해제한 메모리가 OS 로 잘 돌아가지 않는다(pymalloc arena). 프로세스 RSS 가 안 줄어드는 현상의 배경이다. Java 의 GC 튜닝 감각(힙 크기, 수집기 선택)은 Python 에서 대부분 해당이 없고, 대신 **참조 구조와 객체 수**가 핵심 변수다.
- **버전 변화와 실무 함의**
  - 3.12, PEP 683: immortal 객체(None, True, 작은 int 등)의 refcount 는 고정이다.
  - 3.13: free-threaded 실험 빌드(biased refcounting, 지연 refcount, mimalloc).
  - 3.14.0~3.14.4: 증분 GC(young/old 2세대, 최대 일시정지 감소)가 들어갔다. **프로덕션의 메모리 압박 보고 때문에 3.14.5에서 3.13 의 세대별 GC 로 되돌렸고, 3.15 도 세대별 GC 다**(What's New 3.14/3.15). 3.16 에 다시 들일지는 PEP 절차로 논의 중이다(미확정). 즉 **같은 3.14 라도 패치 버전에 따라 GC 가 다르다.** 메모리 문제를 조사할 때는 패치 버전까지 확인한다.
  - 3.14: borrowed 참조 때문에 `getrefcount` 가 예전보다 작게 나올 수 있다.
  - free-threaded 빌드: 지연 refcount 대상 객체는 refcount 가 0이어도 즉시 해제되지 않을 수 있다(공식 HOWTO). immortal 범위는 3.14부터 코드 상수와 interned str 로 줄었다. GC 가 stop-the-world 방식이라는 설명은 비공식 자료뿐이다(**미확인**).
- **실무 누수 패턴**: 끝없이 자라는 모듈 전역 dict/캐시, `lru_cache(maxsize=None)` 와 메서드(self 고정), 등록만 하고 해제하지 않는 콜백과 옵저버, 예외나 트레이스백 저장(프레임 → 지역 변수 전체가 살아남는다), `__del__` 이 있는 순환(3.4+ PEP 442 로 수거되지만 순서는 비결정적), C 확장.
- **도구**: `tracemalloc`(스냅샷 `compare_to`, `statistics("lineno")`), `gc.get_referrers`(디버깅 전용), `weakref`(`ref`, `WeakValueDictionary`, `WeakKeyDictionary`, `WeakSet`, `WeakMethod`, `finalize`), `sys.getsizeof` 는 얕은 크기다. fork 기반 서버에서 copy-on-write 를 지키려면 `gc.freeze()`(3.7)를 쓴다. `gc.disable()` 은 대안이 아니다(Instagram 사례와 그 후속).
- **언제**: 캐시는 크기 제한이나 weak 참조로 만든다. 리소스 해제는 `with` 로 한다(GC 에 의존하지 않는다). 순환을 만드는 부모 포인터는 `weakref` 로 둔다.

**Java 습관 함정**: "GC 가 알아서" 하리라는 생각(리소스와 캐시), `finalize()` 를 쓰던 감각으로 `__del__` 을 쓰는 것, 메모리 문제를 힙 크기 조정으로 풀려는 것.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_refcount_dealloc.py` | `del` 직후 해제와, 컨테이너에 담겨 있을 땐 해제되지 않는 것 | `weakref.finalize` 콜백 순서 로그 |
| `ex02_cycles.py` | 순환은 `del` 로 해제되지 않고 `gc.collect()` 에서 수거된다 | finalize 시점, 수거 개수 ≥ 2 판정 |
| `ex03_gc_generations.py` | 임계값, 카운트, 수집 이벤트 | `gc.get_threshold`/`get_count`/`gc.callbacks`(세대, 수거 수) |
| `ex04_refcount_caveats.py` | immortal, borrowed 참조, getrefcount 를 쓰면 안 되는 이유 | `sys._is_immortal`, 판정만 |
| `ex05_leak_hunt.py` | 메서드에 붙은 `lru_cache` 누수 재현과 추적 | `tracemalloc` 스냅샷 비교 상위 항목의 파일:줄(크기는 `...`), `weakref` 생존 판정 |
| `ex06_traceback_keeps_frames.py` | 저장해 둔 예외가 큰 지역 객체를 붙잡는다, `traceback.clear_frames` | `weakref` 생존 판정 |

| 실습 (`memory.py`) | 테스트가 확인하는 것 |
|---|---|
| `deep_sizeof(obj)` | 공유 하위 객체는 한 번만 센다, 순환에서도 끝난다, 결과 ≥ 얕은 크기 합 |
| `Registry` (WeakValueDictionary 기반) | 외부 참조가 사라지면 즉시 제거(gc 호출 없이), 이름 목록 |
| `EventBus.subscribe(cb)` | bound method 는 `WeakMethod` 로 보관해 구독 객체를 붙잡지 않는다. 일반 함수는 유지 |
| `TreeNode` (부모 weakref) | `gc.disable()` 상태에서도 루트를 `del` 하면 전체가 해제된다(finalize 호출) |
| 제공된 `leaky_service.py` 수정 | 요청 1만 건을 처리한 뒤 tracemalloc 증가량이 임계값 미만 |
