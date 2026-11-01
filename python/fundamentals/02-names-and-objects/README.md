# 02. 이름, 객체, 참조 · 4h · 선행 01

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: `a = b` 는 무엇을 복사하나? 리스트를 함수에 넘기면 원본이 바뀌는데 정수는 왜 안 바뀌나? `is` 는 언제 쓰나?

**README**
- 모든 값은 힙에 있는 객체다(참조 카운트, 타입 포인터, 값). Java 의 primitive 가 없어서 `int` 도 객체다. **변수는 이름공간(dict, 또는 함수의 지역 슬롯)에 있는 "이름 → 객체 참조"** 다. 대입은 바인딩일 뿐이고 절대 복사하지 않는다.
- `id()`(CPython 에서는 주소), `is`(동일성)와 `==`(`__eq__`, 동등성). `is` 는 `None`·sentinel 비교에만 쓴다. 작은 정수 캐시와 문자열 interning 은 구현 디테일이다. **관찰(3.15.2), 문서 미확인**: 작은 정수 캐시 범위가 −5..1024 다(널리 알려진 범위는 −5..256). 유명한 "256 is 256, 257 is not 257" 데모가 3.15.2 에선 다르게 나온다. 이것이 값 비교에 `is` 를 쓰면 안 되는 이유를 직접 보여 준다. 컴파일러도 `x is 1` 에 `SyntaxWarning` 을 낸다.
- 가변(list/dict/set/bytearray)과 불변(int/str/tuple/frozenset/bytes/`frozendict`(3.15, PEP 814)). 불변 객체에 연산하면 새 객체가 생기고 이름이 다시 바인딩된다.
- `+=` 의 두 얼굴: `__iadd__` 가 있으면(list) 제자리에서 바꾸고, 없으면(tuple/int/str) `__add__` 로 새 객체를 만든 뒤 재바인딩한다. dis 로 보면 `BINARY_OP (+=)` 뒤에 **항상 `STORE_*`** 가 따라온다. 그래서 `t = ([],); t[0] += [1]` 은 리스트를 먼저 바꾼 다음 튜플 항목 대입에서 `TypeError` 를 낸다(로컬 확인: 예외가 나지만 `t == ([1],)`).
- 인자 전달은 call by sharing 이다. Java 의 참조 전달과 같지만 Python 엔 "primitive 는 복사" 같은 예외가 없다. 재바인딩과 변이를 구분해야 한다.
- 얕은 복사(`list(x)`, `x[:]`, `copy.copy`)와 깊은 복사(`copy.deepcopy` 는 memo 로 순환과 공유를 보존한다). `[[0]*3]*3` 별칭 버그.
- 해시 가능성: dict 키와 set 원소는 hashable 이어야 한다. 해시가 바뀌면 버킷을 잃기 때문이다. list 를 담은 tuple 은 해시할 수 없다.
- refcount 를 관찰할 때 주의할 점: `sys.getrefcount` 는 인자로 넘긴 임시 참조 때문에 1이 더 나온다. 3.14부터는 borrowed 참조(`LOAD_FAST_BORROW`) 때문에 예전보다 작게 나올 수 있다(로컬: 지역 리스트가 1). immortal 객체(PEP 683, 3.12)는 아주 큰 값이 나온다(로컬: `getrefcount(None) == 3221225472`). 공식 문서도 "0이나 1 이외의 값에 의존하지 말라"고 한다. 그래서 이 섹션은 **상대 변화**만 본다.

**Java 습관 함정**: `==` 의 의미가 반대다(Java `==` 는 참조 비교, Python `==` 는 값 비교). 문자열 비교에 `is` 를 쓴다. "기본형이니 복사되겠지"라고 생각한다. 가변 인자를 그대로 필드에 저장한다(방어적 복사가 필요한 지점).

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_binding.py` | 대입, 재바인딩, 변이 구분 | `id()` 가 같은지를 bool 로 출력 |
| `ex02_identity_vs_equality.py` | `==` 와 `is`, 정수 캐시 경계, `is` 리터럴 경고 | 경계 줄은 `...`(실제 값은 stderr), `warnings.catch_warnings` + `compile` |
| `ex03_augmented_assignment.py` | list 와 tuple 의 `+=`, `t[0] += [1]` 퍼즐 | `id` 비교, dis 에서 `BINARY_OP` 뒤 `STORE_SUBSCR` 판정 |
| `ex04_call_by_sharing.py` | 함수 안 변이와 재바인딩(Java 대비 주석) | 출력 |
| `ex05_copying.py` | `[[0]*3]*3`, copy 와 deepcopy, deepcopy 가 공유와 순환을 보존 | `id` 비교 |
| `ex06_refcount.py` | getrefcount 의 상대 변화, `sys._is_immortal`(3.14+), `del` 직후 해제(09 예고) | `weakref.finalize` 콜백 순서 |

| 실습 (`aliasing.py`) | 테스트가 확인하는 것 |
|---|---|
| `make_grid(rows, cols, fill)` | 한 칸을 바꿔도 다른 행에 퍼지지 않는다 |
| `deep_copy(obj)`: list/dict/set/tuple/원자값, `copy` 모듈 사용 금지 | 결과가 원본과 같다(`==`). 가변 내부 객체의 `id` 가 모두 다르다. 공유하던 하위 객체가 복사본에서도 공유된다. 자기 참조 리스트를 처리한다. 불변 원자값은 같은 객체다. `copy.deepcopy` 를 monkeypatch 해서 부르면 실패하게 만든다 |
| `with_appended(items, value)`: 원본 불변 계약 | 원본의 내용과 `id` 가 그대로다 |
| `freeze(obj)`: list→tuple, dict→`frozendict`, set→frozenset 재귀 변환 | `hash()` 가 성공하고 dict 키로 쓸 수 있다. 동등성 |
