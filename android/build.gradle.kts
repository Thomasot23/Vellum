plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
val ktlint by configurations.creating {
    attributes {
        attribute(
            org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Bundling.SHADOWED),
        )
    }
}
dependencies { ktlint(libs.ktlint.cli) }
tasks.register<JavaExec>("ktlintCheck") {
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("**/src/**/*.kt", "*.gradle.kts", "app/*.gradle.kts")
}
tasks.register<JavaExec>("ktlintFormat") {
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("-F", "**/src/**/*.kt", "*.gradle.kts", "app/*.gradle.kts")
}
