package exercises

// Job은 스케줄러 ready queue에 들어가는 작업이다.
type Job struct {
	ID   int
	Name string
}

// JobQueue는 *Job의 FIFO 큐다. zero value(var q JobQueue)로 바로 쓸 수 있어야 한다.
//
// 요구사항
//   - Push/Pop은 분할 상환(amortized) O(1).
//   - Pop으로 꺼낸 Job을 큐가 계속 참조하면 안 된다(GC가 회수할 수 있어야 함).
//   - Push/Pop이 번갈아 계속 일어나도(큐 길이는 작게 유지) 내부 버퍼가 끝없이 커지면 안 된다.
//   - 길이가 일정한 정상 상태에서 Push/Pop은 메모리를 할당하지 않는다.
//
// 힌트: Java ArrayDeque가 내부에서 무엇을 하는지 떠올려 보라.
// q.items = q.items[1:] 방식과 "head 인덱스만 증가" 방식이 각각 어느 요구사항을 어기는지는 README에 있다.
type JobQueue struct {
	// TODO: 필드를 설계하세요
}

// Push는 j를 큐 뒤에 넣는다.
func (q *JobQueue) Push(j *Job) {
	// TODO: 구현하세요
}

// Pop은 맨 앞 Job을 꺼낸다. 비어 있으면 (nil, false).
func (q *JobQueue) Pop() (*Job, bool) {
	// TODO: 구현하세요
	return nil, false
}

// Len은 큐에 든 Job 수다.
func (q *JobQueue) Len() int {
	// TODO: 구현하세요
	return 0
}

// Cap은 내부 버퍼 용량이다(테스트가 메모리 사용이 유계인지 확인하는 데 쓴다).
func (q *JobQueue) Cap() int {
	// TODO: 구현하세요
	return 0
}
