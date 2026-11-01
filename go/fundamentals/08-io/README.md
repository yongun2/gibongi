# 08. I/O: io 조합, 파일·블록 I/O, 직렬화

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 05 06 07 · 4h · 관련 변화: `os.Root`(1.24), `omitzero`(1.24), `io.ReadAll` 2배 빠름(1.26), **`encoding/json`이 v2 위에서 동작 + `encoding/json/v2`(1.27)**

**핵심 질문**

1. `io.Reader`/`Writer` 계약(n과 err를 함께 반환)은 정확히 무엇이고, 왜 이 두 인터페이스로 거의 모든 I/O가 조합되나?
2. 버퍼링은 시스템 콜을 얼마나 줄이며, `Flush`를 잊으면 무엇이 사라지나?
3. 100만 줄 trace를 가장 싸게 읽는 방법은? (`fmt.Fscan` vs `bufio.Scanner`+`strconv`)
4. 디스크 이미지의 n번째 블록만 읽고 쓰려면? (`ReadAt/WriteAt` vs `Seek+Read`)

**README**

- ① 내부 동작
  - **Reader 계약**
    - `Read(p)`는 `0 < n ≤ len(p)`를 읽을 수 있고, `n > 0`과 `err == io.EOF`를 **동시에** 돌려줄 수 있다. 그래서 **n을 먼저 처리**한다.
    - 정확히 n바이트가 필요하면 `io.ReadFull`을 쓴다. `io.EOF`는 센티널 값이다.
  - **Writer 계약**: `n < len(p)`이면 반드시 `err != nil`이다.
  - **조합**
    - `io.Copy`는 32KB 버퍼를 쓰되, `WriterTo/ReaderFrom`이 있으면 버퍼를 생략한다.
    - `LimitReader`, `TeeReader`, `MultiWriter`, `SectionReader`, `Pipe`로 엮는다.
    - `os.File`, `bytes.Buffer`, `strings.Reader`, `net.Conn`, `gzip`이 모두 같은 인터페이스를 만족한다.
  - **os.File과 bufio**
    - `os.File`의 Read/Write 하나가 시스템 콜 하나다.
    - `bufio.Reader/Writer`는 기본 4KB 버퍼를 쓴다.
  - **bufio.Scanner의 한계**
    - 토큰 최대 길이는 **64KB**(`MaxScanTokenSize`)다 **[로컬 소스]**. 넘으면 `ErrTooLong`으로 멈추므로 `sc.Err()`를 확인해야 한다.
    - `Bytes()`는 내부 버퍼를 가리킨다. 다음 `Scan`에서 덮이므로 보관하면 안 된다(03의 aliasing).
  - **`fmt.Fscan`**은 리플렉션, rune 단위 읽기, 인터페이스 박싱을 거친다. Java `Scanner`처럼 느리다.
  - **ReadAt/WriteAt**
    - 오프셋을 지정해 읽고 쓰며, 파일 위치를 공유하지 않는다(pread/pwrite). 그래서 동시에 써도 안전하다.
    - 블록 장치 = `ReadAt(buf, blk*512)`.
  - **OpenFile 플래그**: `O_CREATE|O_EXCL`은 "이미 있으면 실패"(cpout에 필요). `Sync`는 fsync다.
  - **파일 시스템 추상화**: `os.Root`(1.24)는 디렉터리 밖으로 나가는 경로를 막는다. `io/fs`, `embed`, `testing/fstest`도 있다.
  - **JSON**
    - 구조체 태그를 쓴다. `omitempty`와 `omitzero`(1.24: `time.Time` zero 생략)는 다르다.
    - 1.27부터 `encoding/json`(v1 API)은 v2 구현 위에서 동작한다. 되돌리려면 `GOEXPERIMENT=nojsonv2`.
    - v2 기본값: nil 슬라이스를 `[]`로, 잘못된 UTF-8과 중복 키는 거부 **[RN 1.27, 로컬]**.
- ② 차이

  | Java | Go |
  |---|---|
  | InputStream/Reader 계층: 바이트·문자 분리, 데코레이터 상속 | 1메서드 인터페이스 두 개 |
  | `BufferedReader.readLine` | `bufio.Scanner` (**줄 길이 제한**) |
  | `java.util.Scanner` (느림) | `fmt.Fscan` — 같은 함정 |
  | try-with-resources | `defer Close` |
  | `RandomAccessFile.seek+read` | `ReadAt` (동시성 안전) |
  | Jackson: 애너테이션, 바이트코드 생성 | `encoding/json`: struct tag, 리플렉션 |

- ③ 판단 기준
  - 함수는 파일 이름 대신 `io.Reader/Writer`를 받는다. 테스트에서 `strings.Reader`나 `bytes.Buffer`를 넘길 수 있다.
  - 줄 단위 텍스트는 `Scanner`. 줄이 길 수 있으면 `Buffer` 설정 또는 `bufio.Reader.ReadString`.
  - 출력이 크면 `bufio.Writer` + `Flush`(에러 확인).
  - 블록이나 레코드 단위면 `ReadAt/WriteAt` + 버퍼 재사용.
  - 작은 파일은 `os.ReadFile`.
- 함정
  - `n`을 무시하고 `err`만 본다.
  - `Flush`를 빠뜨린다.
  - `Scanner`가 64KB 넘는 줄에서 조용히 멈춘다.
  - `Bytes()`를 보관한다.
  - 쓰기 파일의 Close 에러를 무시한다.
  - `Seek+Read`를 여러 고루틴이 공유한다.

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `readerContract` | `iotest.OneByteReader`, `iotest.DataErrReader`로 순진한 루프가 마지막 데이터를 잃음 vs `io.ReadAll` | 출력 |
| `composition` | `LimitReader+TeeReader+MultiWriter+SectionReader` 체인 | 출력 |
| `flushForgotten` | `bufio.Writer`에 쓰고 Flush 안 하면 하위 Writer에 0바이트 | 출력 |
| `syscallCount` | Write 호출 수를 세는 래퍼: 1만 줄 직접 쓰기 1만 회 vs bufio 경유 수 회 | 출력 |
| `scannerTooLong` | 70KB 줄 → `bufio.ErrTooLong`, `Buffer` 설정 후 성공 | 출력 |
| `scannerBytesAliasing` | `Bytes()` 보관 시 내용이 덮어써짐 | 출력 |
| `blockIO` | 임시 파일에 블록 3개 `WriteAt` → `ReadAt(blk 1)` | 출력 |
| `jsonV1V2` | `omitzero`, nil 슬라이스(`null` vs `[]`), 중복 키 v1 허용·v2 거부 | 출력 |
| `Benchmark*` | trace 100만 줄 파싱: `fmt.Fscan` vs `Scanner+Sscanf` vs `Scanner+Cut+ParseUint` | `--bench` (VM 시뮬레이터 성능 판단 근거) |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `TraceReader` | `Next() (addr, write, err)`, `bufio.Scanner` 기반, 줄 번호가 들어간 에러 | 픽스처 결과, `line 3: ...` 에러, 마지막 줄 개행 없음, 줄당 할당 0(Scanner 버퍼 제외) |
| `RoundRobinTraces` | 여러 `io.Reader`를 process 0..N 순서로 한 줄씩 교대 (가상 메모리 프로젝트의 trace 읽기 규칙) | 방문 순서, 길이가 다른 입력, 중간 에러 전파 |
| `Disk` | `Open/ReadBlock/WriteBlock/Close` on `os.File`(ReadAt/WriteAt), 06의 `BlockDevice` 만족 | `t.TempDir` 이미지 왕복, 범위 밖·크기 불일치 에러, 다시 열어도 유지 |
| `CopyIn` | `io.Reader` → 블록들, 공간 부족이면 **가능한 만큼 복사 후** `ErrNoSpace` (SFS cpin 규칙) | 정확한 바이트 수, 마지막 블록 부분 채움, 부분 복사 + 에러 동시 반환 |
