pluginManagement { includeBuild("build-logic") }

rootProject.name = "gibongi-kotlin"

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include(":support")

// fundamentals/NN-*, projects/* 를 자동으로 서브프로젝트로 등록한다.
for (group in listOf("fundamentals", "projects")) {
    file(group).listFiles { f -> f.isDirectory && File(f, "build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { include(":$group:${it.name}") }
}
