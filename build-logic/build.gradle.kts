plugins {
	`kotlin-dsl`
}

repositories {
	gradlePluginPortal()
	mavenCentral()
}

dependencies {
	// build-logic is an included build — it resolves its own buildscript classpath
	// before the root project's version catalog exists, so it can't reference
	// gradle/libs.versions.toml here. Hardcoding the Spring Boot plugin version is a
	// known, expected shape for composite builds, not an oversight; keep it in sync
	// with [versions] spring-boot in the root catalog by hand.
	implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.1")
	implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}
