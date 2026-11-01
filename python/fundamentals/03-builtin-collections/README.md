# 03. 내장 자료구조의 내부 · 5h · 선행 02

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: list 는 ArrayList 이고 dict 는 HashMap 인가? 무엇이 같고, 무엇이 달라서 성능과 버그에 차이가 생기나?

**README**
- **list**: `PyObject*` 동적 배열이다. 원소는 항상 포인터라서 Java `ArrayList<Integer>` 처럼 박싱된 상태가 기본이다. 여분 할당(over-allocation) 때문에 `getsizeof` 가 계단식으로 늘어난다. `append` 는 분할 상환 O(1), `insert(0)`/`pop(0)` 은 O(n) 이므로 `collections.deque` 를 쓴다. `x in list` 는 O(n).
- **tuple**: 고정 크기이고 불변이다. 상수 튜플은 컴파일 시점 상수(`LOAD_CONST`)라서 리터럴 tuple 이 list 보다 싸다. `namedtuple`.
- **dict**: 개방 주소법 해시 테이블과 삽입 순서를 유지하는 compact 배열(3.6 구현, 3.7 언어 보장)로 이루어진다. Java `HashMap`(체이닝과 트리화, 순서 미보장)과 비교해 보라. 순회하는 도중에 크기를 바꾸면 `RuntimeError` 가 난다(Java `ConcurrentModificationException` 과 같은 역할). 조회는 해시 비교 다음에 `==` 비교 순서다. 인스턴스 `__dict__` 키 공유(PEP 412)는 한 줄 언급. view, `defaultdict`, `Counter`, `setdefault`, `|` 병합(3.9).
- **set**: 해시 테이블이고 `in` 은 O(1) 이다. **str 원소의 순회 순서는 실행할 때마다 다르다**(해시 랜덤화, `PYTHONHASHSEED`). Java `HashSet` 도 순서를 보장하지 않지만 실행마다 바뀌지는 않는다. 그래서 Java 에서 넘어온 사람의 테스트가 "가끔" 깨진다.
- **str**: 불변 유니코드다. PEP 393 compact 표현은 문자열마다 1/2/4바이트 가운데 최소 폭을 고른다. 반복 `+=` 연결은 CPython 이 참조가 하나일 때 제자리 확장으로 최적화하지만 보장되지 않는다(PEP 8). 그러니 `"".join` 이나 `io.StringIO` 를 쓴다(Java `StringBuilder` 에 해당).
- **bytes/bytearray**: `encode`/`decode`. 3.15부터 UTF-8 이 기본 인코딩이지만 이식성을 위해 `encoding=` 을 명시한다. `bytearray.take_bytes()`(3.15)는 복사 없이 bytes 를 꺼낸다.
- **정렬**: Timsort 이고 안정 정렬이다. Comparator 대신 `key=` 를 쓴다. 다중 키는 튜플 key 나 "안정 정렬 두 번". `list.sort()` 는 `None` 을 돌려준다(체이닝 함정).
- 선택표: 양끝 큐는 deque, 멤버십은 set/dict, 정렬 유지는 bisect, top-k 는 heapq, 불변 키는 tuple/frozenset/frozendict.

**Java 습관 함정**: `list.contains` 습관으로 큰 list 에 `in` 을 쓴다. 큐를 list 의 `pop(0)` 으로 만든다. set 순서에 기대는 테스트. `sorted(..., cmp)` 를 찾는다. 문자열을 루프에서 `+=` 한다.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_list_growth.py` | append 와 over-allocation | `getsizeof` 가 바뀐 횟수 < append 횟수 판정, 계단 값은 stderr |
| `ex02_deque_vs_list.py` | `pop(0)` 과 `popleft`, `in list` 와 `in set` | 결과 동일 판정과 `timeit`(`...`) |
| `ex03_dict_internals.py` | 삽입 순서, 삭제 뒤 재삽입 순서, 순회 중 변경 `RuntimeError`, 항상 같은 hash 를 돌려주는 키에서 `__eq__` 호출 수 폭증 | 호출 카운터 |
| `ex04_set_order.py` | `PYTHONHASHSEED` 를 바꿔 str set 순서가 달라지고 int set 은 같다 | 서브프로세스 2회 실행 비교 판정 |
| `ex05_str_internals.py` | PEP 393 폭(ASCII < 한글 < 이모지, 문자당 크기), join 과 `+=` | `getsizeof` 판정, `timeit`(`...`) |
| `ex06_sorting.py` | 안정 정렬로 하는 다중 키 정렬, `key` 와 `cmp_to_key`, `sort()` 의 `None` | 출력 |

| 실습 (`collections_drill.py`) | 테스트가 확인하는 것 |
|---|---|
| `LRUCache(capacity)`: dict 의 삽입 순서만으로 구현 | `get` 이 최근 사용을 갱신한다, 용량을 넘으면 가장 오래된 것을 지운다, 덮어쓰기 |
| `group_anagrams(words)` | 그룹 순서와 그룹 안 순서가 첫 등장 순서다(dict 순서 보장) |
| `top_k_frequent(words, k)` | 동률이면 사전순, k 가 고유 개수보다 큰 경우 |
| `sliding_max(nums, k)`: deque 단조 큐 | 정확성과 2×10⁵ 입력 시간 상한(O(nk) 풀이를 걸러낸다) |
