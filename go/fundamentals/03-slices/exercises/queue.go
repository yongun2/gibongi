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
	buf  []*Job // 원형 버퍼
	head int    // 맨 앞 원소 위치
	n    int    // 원소 수
}

// Push는 j를 큐 뒤에 넣는다.
func (q *JobQueue) Push(j *Job) {
	if q.n == len(q.buf) {
		q.grow()
	}
	q.buf[(q.head+q.n)%len(q.buf)] = j
	q.n++
}

func (q *JobQueue) grow() {
	newBuf := make([]*Job, max(4, 2*len(q.buf)))
	// 원형으로 감긴 두 조각을 순서대로 펴서 옮긴다.
	k := copy(newBuf, q.buf[q.head:])
	copy(newBuf[k:], q.buf[:q.head])
	q.buf, q.head = newBuf, 0
}

// Pop은 맨 앞 Job을 꺼낸다. 비어 있으면 (nil, false).
func (q *JobQueue) Pop() (*Job, bool) {
	if q.n == 0 {
		return nil, false
	}
	j := q.buf[q.head]
	q.buf[q.head] = nil // GC가 회수할 수 있도록 참조를 끊는다
	q.head = (q.head + 1) % len(q.buf)
	q.n--
	return j, true
}

// Len은 큐에 든 Job 수다.
func (q *JobQueue) Len() int {
	return q.n
}

// Cap은 내부 버퍼 용량이다(테스트가 메모리 사용이 유계인지 확인하는 데 쓴다).
func (q *JobQueue) Cap() int {
	return len(q.buf)
}
