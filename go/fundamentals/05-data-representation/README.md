# 05. 데이터 표현: 문자열·바이트·정수·비트·바이너리 인코딩

> 아직 설계 메모만 있는 섹션이다. 예제와 실습은 이 메모를 바탕으로 만든다.

> 선행 03 · 4h · 관련 변화: `strings.Clone`(1.20), `binary.Encode/Decode/Append`(1.23), `strings.Lines/SplitSeq`(1.24), `bytes/strings.CutLast`(1.27)

OS 프로젝트 매핑에서 비트 연산, 부호 없는 고정 폭 정수, 리틀엔디언 구조체, NUL로 끝나는 이름이 모두 필요하다고 나왔다. 그래서 처음에는 문자열 섹션의 일부였던 내용을 독립 섹션으로 키웠다(6장).

**핵심 질문**

1. `len("한글")`은 왜 6이고, `s[0]`과 `for _, r := range s`는 각각 무엇을 돌려주나?
2. `string ↔ []byte` 변환은 언제 복사하고 언제 안 하나? `+=`로 문자열을 쌓으면 왜 느린가?
3. Go 정수는 Java와 무엇이 다른가(부호 없는 타입, 시프트, 오버플로, 암묵 변환 없음)?
4. 디스크의 리틀엔디언 바이트를 Go 구조체로 어떻게 읽고 쓰나? 언제 리플렉션 기반 `binary.Read`를 쓰고 언제 손으로 디코딩하나?

**README**

- ① 내부 동작
  - **문자열의 정체**
    - `string`은 {ptr, len} 16B이고 불변 UTF-8 바이트 열이다.
    - 인덱스 `s[i]`는 바이트를 준다. `range`는 rune을 디코딩하며, 잘못된 UTF-8은 U+FFFD(폭 1)로 준다.
  - **변환의 복사**
    - `string(b)`와 `[]byte(s)`는 불변성을 지키려고 일반적으로 **복사**한다.
    - 컴파일러가 복사를 생략하는 경우가 있다: 맵 조회 `m[string(b)]`, 비교, 연결 임시값, `range []byte(s)`. `AllocsPerRun`으로 확인한다.
    - `unsafe.String/StringData`로 복사를 강제로 없애는 것은 최후 수단이다.
  - **부분 문자열**은 메모리를 공유한다. 03의 보유 문제와 같고, `strings.Clone`으로 끊는다.
  - **연결**
    - `+`는 매번 새 문자열을 만든다. 루프에서 쓰면 O(n²)이다.
    - `strings.Builder`는 `[]byte`에 append하다가 `String()`에서 복사 없이 변환한다.
    - `fmt.Sprintf`는 리플렉션과 인터페이스 박싱을 거쳐 `strconv.Append*`보다 느리다.
  - **정수**
    - `int/uint`는 플랫폼 크기다(arm64에서 64비트). 고정 폭이 필요하면 `int8~uint64`를 쓴다.
    - 다른 정수 타입끼리 연산하려면 명시 변환이 필요하다.
    - 오버플로는 **조용히 wrap**된다. 검사가 필요하면 `math/bits.Add64` 등을 쓴다.
    - 상수는 untyped이고 임의 정밀도다. 쓰이는 시점에 타입이 정해지고, 넘치면 컴파일 에러다.
  - **시프트**
    - 부호 있는 `>>`는 산술 시프트, 부호 없는 `>>`는 논리 시프트다. `>>>`는 없고 타입이 결정한다.
    - **시프트 횟수가 비트 폭 이상이면 결과는 0**이다. Java는 횟수를 마스킹해 `1 << 32 == 1`이 된다.
    - 음수 시프트 횟수는 panic이다.
  - **비트 연산**
    - 연산자: `& | ^`, `&^`(AND NOT), 단항 `^`(NOT, Java의 `~`).
    - `math/bits`: `TrailingZeros`, `OnesCount`, `LeadingZeros`, `Len`. 하드웨어 명령으로 컴파일된다(`-S`로 확인).
  - **열거형**: `iota` + `String()` 메서드(`stringer` 도구는 15장의 `tool` 지시어).
  - **실수**: float64는 IEEE754, 정수 나눗셈은 0 방향 절사. 통계 출력은 `%.2f`, 정수↔실수 변환은 명시한다.
  - **`encoding/binary`**
    - 바이트 순서: `ByteOrder`(`LittleEndian.Uint32(b)`, `PutUint32`, `AppendUint32`).
    - `binary.Read/Write`(1.23부터 `Encode/Decode`)는 리플렉션으로 **고정 크기 타입만** 다룬다: 고정 폭 정수, 배열, 그런 필드만 가진 구조체. `int`, `string`, 슬라이스 필드는 안 된다.
    - 손으로 디코딩할 때 `_ = b[3]` 힌트를 주면 범위 검사 4개가 1개로 줄어든다(BCE).
  - **고정 길이 이름** `[60]byte`: NUL로 끝난다. 읽을 때는 `bytes.IndexByte(b[:], 0)`, 쓸 때는 `copy(arr[:], s)`.
  - **16진수 파싱**: `strconv.ParseUint(s, 16, 32)`. `fmt.Sscanf`는 리플렉션과 할당 때문에 느리다(08장 벤치).
- ② 차이

  | Java | Go |
  |---|---|
  | `char`, `String.length()`는 UTF-16 단위 | 바이트 / rune |
  | `substring`은 복사 (7u6+) | 부분 문자열이 메모리 공유 |
  | 부호 없는 타입 없음 (`Integer.toUnsignedLong`, `>>>`) | `uint32` 등 → 주소·비트맵 코드가 직설적 |
  | 시프트 횟수 마스킹 | 0 |
  | 암묵 확장 변환 (`int → long`) | 모든 변환 명시 |
  | `ByteBuffer.order(LITTLE_ENDIAN).getInt()` | `binary.LittleEndian.Uint32(b)` |

- ③ 판단 기준
  - 프로토콜이나 trace처럼 ASCII면 바이트 단위로, 사람 언어면 rune 단위로 처리한다.
  - 루프에서 문자열을 조립하면 `Builder`(+`Grow`), 한두 번이면 `+`, 서식이 복잡하면 `fmt`.
  - 핫 경로의 숫자↔문자열 변환은 `strconv`.
  - 비트 필드는 부호 없는 고정 폭 타입 + 이름 있는 마스크 상수로 표현한다.
  - 바이너리 구조체는 `binary.Read/Decode`로 시작하고, 프로파일에 걸리면 수동 디코딩으로 바꾼다.
- 함정
  - `len`이 바이트 수다.
  - `string(65)`는 `"A"`다(vet `stringintconv`가 잡는다).
  - 루프에서 `+=`.
  - 무분별한 정수 변환으로 값이 잘린다.
  - `uint` 뺄셈 언더플로(`a-b`, `b>a`).
  - `uint32(1)<<n`에서 `n=32`이면 0이다.
  - `binary.Read`에 `int` 필드를 넣는다.
  - NUL 패딩이 남은 이름을 그대로 문자열로 쓴다(끝에 `\x00`).

**예제**

| 예제 | 보여 주는 것 | 관찰 |
|---|---|---|
| `bytesVsRunes` | `len("한글")=6`, rune 수 2, range 인덱스 0·3, `s[0]` | 출력 |
| `invalidUTF8` | range가 U+FFFD를 냄 | 출력 |
| `conversionAllocs` | `string(b)` 1회 vs `m[string(b)]` 0회 vs 비교 0회 | `AllocsPerRun` |
| `substringRetention` | 부분 문자열이 큰 문자열을 붙잡음, `strings.Clone` | `ReadMemStats` |
| `shiftSemantics` | 변수 시프트 `uint32(1)<<32 == 0`, `int8(-8)>>1`, `uint8(0xF8)>>1`, `int8(127)+1` | 출력 |
| `bitsPackage` | `TrailingZeros`, `OnesCount`, `&^` | 출력 |
| `addressSplit` | 실제 trace 주소 `0x2f8773d8` → VPN/offset, 2단계 인덱스 | 출력 |
| `superblockDecode` | `testdata/superblock.bin`(이 저장소의 파일 시스템 프로젝트 형식으로 직접 만든 512B 슈퍼블록) → `binary.Read` → magic, 블록 수, 디스크 이름 필드, `Sizeof==512` | 출력 (픽스처는 `xxd`로 직접 확인) |
| `bce` | `b[0] \| b[1]<<8 \| ...`의 범위 검사 vs `_ = b[3]` 후 | `observe.Diag(-d=ssa/check_bce/debug=1)` |
| `enumString` | `iota` + `String()`의 `%v` 출력 | 출력 |
| `Benchmark*` | `+=` / Builder / Builder+Grow / Sprintf / `strconv.AppendInt` | `--bench` |

**실습**

| 실습 | 구현 | 테스트가 확인하는 것 |
|---|---|---|
| `SplitAddr/JoinAddr` | 32비트 주소, 4KiB 페이지, `l1Bits` → (l1, l2, off) | 테이블 테스트(l1Bits 1·8·10·20), 무작위 1만 개 왕복 속성 |
| `Bitmap` | `[]byte` 위에서 LSB-first(SFS 규약: 블록 b = 바이트 b/8의 비트 b%8) `Set/Clear/Test/FirstZero(from)` | 픽스처 `ff 3f 00 ..`(블록 0~13이 사용 중인 비트맵)에서 `FirstZero=14`, 바이트 경계, 꽉 참 → `(-1,false)` |
| `ParseTraceLine` | `"2f8773d8 R"` → (addr, write, err) | 잘못된 입력별 에러, **`AllocsPerRun == 0`** (Sscanf로는 실패) |
| `Superblock/Inode/DirEntry` | 512B 블록 Encode/Decode (리틀엔디언) | 왕복, 매직 불일치 에러, 60바이트 꽉 찬 이름과 NUL 처리, indirect 블록(u32 128개) 디코딩 |
