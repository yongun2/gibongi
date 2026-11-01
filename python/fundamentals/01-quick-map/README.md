# 01. Java 개발자를 위한 빠른 지도 · 3h · 선행 없음

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

**핵심 질문**: Java 코드를 Python 으로 옮길 때 문법보다 먼저 바뀌는 것은 무엇인가? Python 코드는 어떻게 실행되나?

**README**
- 실행 모델: 소스를 바이트코드로 컴파일해(`__pycache__/*.pyc`) CPython 스택 VM 이 실행한다. 컴파일할 때 타입 검사가 없어서 오류가 실행 시점에 드러난다(그래서 11장). 모듈 최상위 코드는 import 할 때 **실행된다**(Java 클래스의 static 초기화와 비슷하지만 파일 전체가 실행된다). `if __name__ == "__main__":` 의 의미. JIT 는 3.13부터 실험적 기능이고 기본은 꺼져 있다. Homebrew 3.15.2 빌드에는 JIT 가 빌드돼 있지 않다(로컬 `sys._jit.is_available()` → False).
- Java↔Python 압축 대응표: 들여쓰기 블록, 선언 없는 변수, `final` 없음(대문자 상수 관례, `typing.Final`), `null`→`None`, `&&`→`and`, 삼항식 `a if c else b`, `for(int i…)`→`range`/`enumerate`/`zip`, `switch`→`match`·dict 디스패치, 오버로딩 없음→기본값·키워드 인자, `interface`→Protocol/ABC(07), getter/setter→속성/`property`(06), static 메서드→모듈 함수, checked exception 없음(08), try-with-resources→`with`(08), `String.format`→f-string(`f"{x=}"` 디버그 형식), `equals`→`==`, 참조 비교→`is`, 제네릭→타입 힌트(런타임에 검사하지 않음, 11), package-private 없음→`_` 관례.
- 버그로 직결되는 숫자 의미론:
  - int 는 무한 정밀도라 오버플로가 없다. 대신 큰 수는 느리고 메모리를 많이 쓴다
  - `/` 는 항상 float 를 돌려준다
  - `//` 는 내림 나눗셈이다. `-7 // 2 == -4` 인데 Java 는 `-3`
  - `%` 의 부호는 제수를 따른다. `-7 % 2 == 1` 인데 Java 는 `-1`
  - `round(2.5) == 2` (은행가 반올림)
  - float 오차가 있으므로 돈 계산은 `decimal.Decimal`
- truthiness: `0`, `""`, `[]`, `{}`, `None` 은 거짓이다. `if x:` 로 쓰면 `None` 과 `0` 을 구분하지 못한다.
- `and`/`or` 는 bool 이 아니라 **피연산자 자체**를 돌려준다. `port = given or 8080` 은 `given=0` 을 삼킨다.
- 비교 연쇄 `a < b < c`.
- 문자열은 불변이고 유니코드 코드포인트의 시퀀스다(`len("한") == 1`. Java `char` 는 UTF-16 코드 유닛이다). 3.15부터 `open()` 의 기본 인코딩은 UTF-8 이다(PEP 686, 로컬 `sys.flags.utf8_mode == 1` 확인).
- 표준 라이브러리 지도: pathlib, datetime(aware/naive, `utcnow()` 사용 중단 예정), json, logging(08), argparse, collections/itertools/functools(03·05·04), dataclasses/enum(07), re, subprocess. 각 항목이 어느 섹션에서 깊게 다뤄지는지 표시한다.

**Java 습관 함정**: 인덱스 루프(`for i in range(len(xs))`), 세미콜론과 괄호 습관, `null` 검사를 `== None` 으로 하기, 정수 나눗셈을 그대로 이식.

| 예제 | 보여주는 것 | 관찰 |
|---|---|---|
| `ex01_numbers.py` | `//`·`%`·`round`·무한 정밀도 int·float 오차와 Decimal. 주석에 Java 결과를 대조 | 출력, `sys.getsizeof(2**100)` 가 `2**10` 보다 큰지 판정 |
| `ex02_truthiness.py` | 거짓 값 표, `or` 기본값 관용구가 0과 ""를 삼키는 버그, `is None` 패턴 | 출력 |
| `ex03_execution_model.py` | 모듈 최상위 코드의 실행 시점, `__name__`, `.pyc` 경로(`importlib.util.cache_from_source`), `dis` 첫 사용 | 서브프로세스 실행 결과, dis 판정 |
| `ex04_java_style_vs_idiomatic.py` | 인덱스 루프·getter·static 유틸 클래스를 관용 코드와 나란히 놓고 결과가 같은지 확인 | 결과 동일 판정 |

| 실습 (`quickmap.py`) | 테스트가 확인하는 것 |
|---|---|
| `java_div(a, b)`, `java_mod(a, b)`: Java 의미론의 정수 나눗셈과 나머지 재현(언어 간 이식에서 실제로 필요) | 음수와 경계값 표, 0으로 나누면 `ZeroDivisionError` |
| `first_not_none(*values)` | `0`, `""`, `False` 를 None 과 구분한다 |
| `word_freq(text, n)` (Counter 없이 dict 로) | 동률은 첫 등장 순서(dict 삽입 순서 보장 활용), 대소문자와 구두점 정규화 |
| `parse_kv("a=1; b=2")` → dict | 공백 처리, 잘못된 쌍이면 `ValueError` 와 메시지 |
