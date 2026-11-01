# minisql

아직 명세를 쓰기 전이다. 기본기를 끝낸 뒤 `SPEC.md`와 뼈대 코드를 만든다. 40~60시간 정도 걸릴 것 같다.

CSV 파일을 테이블처럼 써서 SQL을 실행하는 작은 쿼리 엔진이다. `SELECT`, `WHERE`, `ORDER BY`, `LIMIT`, `GROUP BY`와 집계 함수(`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`), `INNER JOIN`, `EXPLAIN` 정도를 지원한다.

쿼리 문자열을 토큰으로 자르고, 파서로 AST를 만들고, 플래너가 이걸 연산자 트리로 바꿔서 실행한다. 연산자(Scan, Filter, Project, HashJoin, Sort, Limit, HashAggregate)는 전부 generator로 만들어서 행이 한 줄씩 위로 당겨지며 흘러가게 한다. 그래서 `LIMIT 5`면 위에서 5개를 받는 순간 아래쪽 스캔도 멈춘다. 반대로 Sort나 집계는 입력을 다 받아야 결과를 낼 수 있어서, 두 종류의 연산자가 메모리를 얼마나 쓰는지 직접 재 본다. 05장(이터레이션과 지연 평가)에서 배운 걸 실제 시스템 크기로 키운 과제다.

CSV 로더, 토큰 종류, AST 노드 정의, 결과 출력, CLI(`python -m minisql`), golden 테스트 러너와 데이터셋은 뼈대로 준비해 두고, 토크나이저, 파서, 식 평가기, 연산자 7개, 플래너, EXPLAIN 출력은 직접 구현한다.

검증은 `./check python project minisql`로 한다. 쿼리 파일마다 기대 결과(또는 오류 메시지)가 golden으로 있고, 10만 줄짜리 CSV에서 `LIMIT 5` 쿼리가 실제로 몇 줄만 읽는지, 메모리 최대 사용량이 한도 안인지도 확인한다.

관련 섹션: 03(dict 기반 조인과 그룹), 04(식을 클로저로 바꾸기), 05(연산자 반복자, 조기 종료), 06과 07(AST dataclass, match 문), 08(예외 계층), 09(메모리 측정), 10(패키지 구성), 11(mypy), 12(golden 테스트), 15(프로파일링)
