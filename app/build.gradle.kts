plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Apply the application plugin to add support for building a CLI application in Java.
    application

    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.openapi.generator") version "7.8.0"

}

group = "ar.edu.austral.inf.sd"
version = "1.0.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-ui:1.6.8")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation("jakarta.validation:jakarta.validation-api:3.1.0")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")

    implementation("io.swagger.core.v3:swagger-annotations:2.1.10")
    implementation("io.swagger.core.v3:swagger-models:2.1.10")

    implementation("org.springframework:spring-web:6.1.12")

    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")

}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    // Define the main class for the application.
    mainClass = "ar.edu.austral.inf.sd.ApplicationKt"
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

tasks.create<JavaExec>("runProfile1") {
    mainClass.set("ar.edu.austral.inf.sd.ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--spring.config.location=classpath:/application-test1.properties")
}

tasks.create<JavaExec>("runProfile2") {
    mainClass.set("ar.edu.austral.inf.sd.ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--spring.config.location=classpath:/application-test2.properties")
}

tasks.create<JavaExec>("runProfile3") {
    mainClass.set("ar.edu.austral.inf.sd.ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--spring.config.location=classpath:/application-test3.properties")
}

tasks.create("openApiGenerateServer", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class.java) {
    templateDir.set(project.layout.projectDirectory.dir("src/main/openapi-generator").toString())
    generatorName.set("kotlin-spring")
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    inputSpec.set(projectDir.resolve("src/main/openapi.json").path)

    configOptions.put("serviceInterface", "true")

    configOptions.put("basePackage", "ar.edu.austral.inf.sd")
    configOptions.put("apiPackage", "ar.edu.austral.inf.sd.server.api")
    configOptions.put("modelPackage", "ar.edu.austral.inf.sd.server.model")
    configOptions.put("gradleBuildFile", "false")
    configOptions.put("useSpringBoot3", "true")
    configOptions.put("documentationProvider", "none")
    generateAliasAsModel.set(false)

    typeMappings.put("object+binary", "kotlin.ByteArray")
    typeMappings.put("object+multipart", "kotlin.ByteArray")

    outputDir.set(projectDir.absolutePath)
}

tasks.create("openApiGenerateClient", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class.java) {
    templateDir.set(project.layout.projectDirectory.dir("src/main/openapi-generator").toString())
    generatorName.set("kotlin")
    library.set("jvm-spring-restclient")
    inputSpec.set(projectDir.resolve("src/main/openapi.json").path)

    configOptions.put("serializationLibrary", "jackson")
    configOptions.put("omitGradleWrapper", "true")
    configOptions.put("packageName", "ar.edu.austral.inf.sd.client")

    configOptions.put("gradleBuildFile", "false")
    configOptions.put("useSpringBoot3", "true")
    configOptions.put("documentationProvider", "none")
    generateAliasAsModel.set(false)

    typeMappings.put("object+binary", "kotlin.ByteArray")
    typeMappings.put("object+multipart", "kotlin.ByteArray")

    outputDir.set(projectDir.absolutePath)

    // additionalProperties.put()
}
