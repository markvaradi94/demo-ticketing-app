plugins {
	java
	id("io.spring.dependency-management")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

// Every module in this build — bootable or not — wants Spring-BOM-managed
// dependency versions (JUnit, AssertJ, etc.), not just the three actual Spring Boot
// apps. shared is a plain library with no Spring Boot Gradle plugin, but it still
// needs this for its own test dependencies to resolve at the same versions
// everything else uses. Version hardcoded here rather than catalog-referenced —
// same known, expected shape as the Spring Boot Gradle plugin version in
// build-logic's own build.gradle.kts: a composite build's precompiled script
// plugins resolve their own buildscript classpath before the root project's
// version catalog exists, so they can't reference gradle/libs.versions.toml here.
// Keep this version in sync with [versions] spring-boot in the root catalog by hand.
dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Xlint:deprecation")
}
