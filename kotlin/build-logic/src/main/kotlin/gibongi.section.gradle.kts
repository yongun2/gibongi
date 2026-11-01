// 모든 fundamentals/NN-* 와 projects/* 가 적용하는 컨벤션 플러그인.
// 디렉터리 규약:
//   examples/          -> "examples" 소스셋 (관찰용 main 함수 + *.expected 골든 출력)
//   exercises/src/     -> main 소스셋 (빈 구현)
//   exercises/test/    -> test 소스셋 (JUnit 6 + kotlin.test)
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = versionCatalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

kotlin {
    jvmToolchain(25)
}

val examples: SourceSet = sourceSets.create("examples")

sourceSets {
    main { java.setSrcDirs(listOf("exercises/src")); resources.setSrcDirs(emptyList<Any>()) }
    test { java.setSrcDirs(listOf("exercises/test")); resources.setSrcDirs(emptyList<Any>()) }
    named("examples") { java.setSrcDirs(listOf("examples")); resources.setSrcDirs(emptyList<Any>()) }
}
kotlin.sourceSets {
    named("main") { kotlin.setSrcDirs(listOf("exercises/src")) }
    named("test") { kotlin.setSrcDirs(listOf("exercises/test")) }
    named("examples") { kotlin.setSrcDirs(listOf("examples")) }
}

dependencies {
    "examplesImplementation"(project(":support"))
    testImplementation(project(":support"))
    testImplementation(kotlin("test"))
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testRuntimeOnly(lib("junit-launcher"))
}

val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }

tasks.test {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL   // 단언 메시지까지 보여 준다
        showStackTraces = false
        showCauses = false
        showStandardStreams = false
    }
}

val verifyExamples = tasks.register<VerifyExamples>("verifyExamples") {
    group = "verification"
    description = "examples/의 main 함수를 실행하고 stdout을 *.expected 와 비교한다 (stderr는 표시만)."
    examplesDir = layout.projectDirectory.dir("examples")
    runtimeClasspath.from(examples.runtimeClasspath)
    javaLauncher = launcher
    updateGolden = providers.gradleProperty("updateGolden").map { it != "false" }.orElse(false)
}

tasks.register<Javap>("javap") {
    group = "help"
    description = "javap -c -p 로 examples/exercises 클래스의 바이트코드를 출력한다. --class=<FQCN>"
    classpath.from(examples.output.classesDirs, sourceSets.main.get().output.classesDirs)
    javaLauncher = launcher
    dependsOn(tasks.named("examplesClasses"), tasks.named("classes"))
}

tasks.named("check") { dependsOn(verifyExamples) }

// ./check kotlin NN 이 호출하는 진입점: 예제 검증 + 실습 테스트를 모두 돌린다.
tasks.register("verifySection") {
    group = "verification"
    dependsOn(verifyExamples, tasks.test)
}
