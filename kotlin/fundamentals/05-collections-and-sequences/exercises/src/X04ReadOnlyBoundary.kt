package s05.exercises

/** X04-1. 내부 리스트를 노출하되, 받은 쪽이 캐스팅·Java 호출로 수정해도 Playlist 는 바뀌지 않아야 한다. */
class Playlist {
    private val songs = mutableListOf<String>()

    fun add(song: String) { songs += song }

    val size: Int get() = songs.size

    val tracks: List<String> get() = TODO("X04-1")
}

/**
 * X04-2. 단어별 등장 횟수. 키 순서는 처음 등장한 순서.
 * 할당 예산: 단어 "종류" 수에 비례해야 한다 (전체 단어 수에 비례하면 안 됨).
 * 힌트: groupBy 와 groupingBy 는 무엇이 다른가? Map<String, Int> 의 값을 +1 할 때 무슨 일이 생기나?
 */
fun wordCounts(words: List<String>): Map<String, Int> = TODO("X04-2")
