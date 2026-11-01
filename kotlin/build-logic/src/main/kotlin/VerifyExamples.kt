import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * 예제 파일 규약
 *  - `fun main` (또는 Java `static void main`) 이 있는 파일만 실행한다.
 *  - 파일 맨 위 주석 `// @run <label>: <JVM 옵션들>` 으로 실행 변형을 선언할 수 있다.
 *      없으면 옵션 없이 1회 실행하고 `<파일명>.expected` 와 비교.
 *      있으면 변형마다 실행하고 `<파일명>.<label>.expected` 와 비교.
 *  - stdout = 결정적인 관찰 결과(골든 비교 대상), stderr = 시간·할당량·GC 로그처럼 매번 달라지는 측정값(표시만).
 */
abstract class VerifyExamples @Inject constructor(
    private val exec: ExecOperations,
) : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val examplesDir: DirectoryProperty

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Input
    abstract val updateGolden: Property<Boolean>

    @get:Input
    @get:Optional
    @get:Option(option = "only", description = "파일명 일부로 실행할 예제를 고른다")
    abstract val only: Property<String>

    init {
        outputs.upToDateWhen { false } // 관찰이 목적이므로 매번 실행
    }

    private data class Run(val label: String?, val jvmArgs: List<String>)
    private data class Example(val file: File, val mainClass: String, val runs: List<Run>)

    @TaskAction
    fun verify() {
        val dir = examplesDir.get().asFile
        val examples = dir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .mapNotNull(::parse)
            .filter { ex -> only.orNull?.let { ex.file.name.contains(it) } ?: true }
            .sortedBy { it.file.name }
            .toList()

        val failures = mutableListOf<String>()
        for (ex in examples) for (run in ex.runs) {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val result = exec.javaexec {
                executable = javaLauncher.get().executablePath.asFile.absolutePath
                classpath = runtimeClasspath
                mainClass.set(ex.mainClass)
                jvmArgs(run.jvmArgs)
                standardOutput = stdout
                errorOutput = stderr
                isIgnoreExitValue = true
            }
            val name = ex.file.nameWithoutExtension + (run.label?.let { ".$it" } ?: "")
            val expectedFile = File(ex.file.parentFile, "$name.expected")
            val actual = normalize(stdout.toString(Charsets.UTF_8))

            val measured = stderr.toString(Charsets.UTF_8).trimEnd()
            if (measured.isNotEmpty()) {
                logger.lifecycle("── $name (stderr: 측정값, 검증 안 함)")
                measured.lines().forEach { logger.lifecycle("   $it") }
            }

            when {
                result.exitValue != 0 -> failures += "$name: exit code ${result.exitValue}\n$actual"
                updateGolden.get() -> { expectedFile.writeText(actual); logger.lifecycle("✎ $name: golden 갱신") }
                !expectedFile.exists() -> failures += "$name: ${expectedFile.name} 없음. 실제 출력:\n$actual"
                normalize(expectedFile.readText()) != actual ->
                    failures += "$name: 출력이 기대와 다름\n" + diff(normalize(expectedFile.readText()), actual)
                else -> logger.lifecycle("✔ $name")
            }
        }
        if (failures.isNotEmpty()) {
            failures.forEach { logger.error("✘ $it") }
            throw GradleException("예제 ${failures.size}개가 기대 출력과 다릅니다.")
        }
    }

    private fun parse(file: File): Example? {
        val text = file.readText()
        val isKotlin = file.extension == "kt"
        val hasMain = if (isKotlin) Regex("""^fun main\(""", RegexOption.MULTILINE).containsMatchIn(text)
        else text.contains("static void main(")
        if (!hasMain) return null
        val pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
        val jvmName = Regex("""@file:JvmName\("(\w+)"\)""").find(text)?.groupValues?.get(1)
        val simple = if (isKotlin) jvmName ?: (file.nameWithoutExtension + "Kt") else file.nameWithoutExtension
        val runs = Regex("""^//\s*@run\s+(\w+):(.*)$""", RegexOption.MULTILINE).findAll(text)
            .map { Run(it.groupValues[1], it.groupValues[2].trim().split(Regex("\\s+")).filter(String::isNotBlank)) }
            .toList()
            .ifEmpty { listOf(Run(null, emptyList())) }
        return Example(file, listOfNotNull(pkg, simple).joinToString("."), runs)
    }

    private fun normalize(s: String) = s.replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }.trimEnd() + "\n"

    private fun diff(expected: String, actual: String): String {
        val e = expected.lines(); val a = actual.lines()
        return (0 until maxOf(e.size, a.size))
            .filter { e.getOrNull(it) != a.getOrNull(it) }
            .take(15)
            .joinToString("\n") { "  line ${it + 1}\n    - ${e.getOrNull(it) ?: "<없음>"}\n    + ${a.getOrNull(it) ?: "<없음>"}" }
    }
}

abstract class Javap @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Input
    @get:Option(option = "class", description = "바이트코드를 볼 클래스 FQCN (예: s05.examples.E03BytecodeShapeKt)")
    abstract val className: Property<String>

    init { outputs.upToDateWhen { false } }

    @TaskAction
    fun run() {
        val javap = javaLauncher.get().metadata.installationPath.file("bin/javap").asFile
        exec.exec { commandLine(javap.absolutePath, "-c", "-p", "-cp", classpath.asPath, className.get()) }
    }
}
