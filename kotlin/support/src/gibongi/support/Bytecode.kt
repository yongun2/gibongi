package gibongi.support

import java.lang.classfile.ClassFile
import java.lang.classfile.instruction.InvokeDynamicInstruction
import java.lang.classfile.instruction.InvokeInstruction
import java.lang.classfile.instruction.NewObjectInstruction

/** 메서드 하나의 바이트코드에서 "무엇을 만들고(new) 무엇을 부르는지(invoke*)"만 추린 요약. */
data class MethodShape(
    val name: String,
    val allocations: List<String>,   // new 로 생성하는 타입
    val calls: List<String>,         // invokevirtual/static/interface/special 대상 (Owner.name)
    val invokeDynamics: List<String>,// invokedynamic 부트스트랩 (예: LambdaMetafactory.metafactory)
)

/**
 * JDK 24에서 정식화된 Class-File API(JEP 484)로 .class 를 읽는다.
 * javap 출력 전체를 골든으로 비교하면 컴파일러 버전마다 깨지므로, 학습에 의미 있는 "모양"만 뽑는다.
 */
fun methodShapes(clazz: Class<*>, vararg methodNames: String): List<MethodShape> {
    val path = clazz.name.replace('.', '/') + ".class"
    val bytes = clazz.classLoader.getResourceAsStream(path)!!.use { it.readAllBytes() }
    val model = ClassFile.of().parse(bytes)
    return methodNames.map { wanted ->
        val method = model.methods().first { it.methodName().stringValue() == wanted }
        val code = method.code().orElseThrow()
        val news = mutableListOf<String>()
        val calls = mutableListOf<String>()
        val indys = mutableListOf<String>()
        for (e in code) when (e) {
            is NewObjectInstruction -> news += e.className().asInternalName().substringAfterLast('/')
            is InvokeInstruction -> calls += e.owner().asInternalName().substringAfterLast('/') + "." + e.name().stringValue()
            is InvokeDynamicInstruction -> indys += e.bootstrapMethod().owner().displayName() + "." + e.bootstrapMethod().methodName()
            else -> {}
        }
        MethodShape(wanted, news, calls.distinct(), indys)
    }
}

fun printShape(s: MethodShape) {
    println("  ${s.name}")
    println("    new          : ${s.allocations.ifEmpty { listOf("(없음)") }.joinToString()}")
    println("    invokedynamic: ${s.invokeDynamics.ifEmpty { listOf("(없음)") }.joinToString()}")
    println("    calls        : ${s.calls.joinToString()}")
}
