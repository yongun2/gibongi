package s05.examples;

import java.util.List;

/** Kotlin의 List<String>을 받아 아무렇지 않게 수정하는 평범한 Java 코드. */
public final class JavaMutator {
    private JavaMutator() {}

    public static void addTo(List<String> list, String value) {
        list.add(value);
    }
}
