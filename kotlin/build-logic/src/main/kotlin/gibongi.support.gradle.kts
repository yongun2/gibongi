// kotlin/support: 예제·테스트가 공유하는 측정 도구 (할당량, 바이트코드 요약)
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin {
    jvmToolchain(25)
    sourceSets.named("main") { kotlin.setSrcDirs(listOf("src")) }
}
sourceSets.named("main") { java.setSrcDirs(listOf("src")); resources.setSrcDirs(emptyList<Any>()) }
