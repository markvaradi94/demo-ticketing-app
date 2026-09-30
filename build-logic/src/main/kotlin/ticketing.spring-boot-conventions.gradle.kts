import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
	id("ticketing.java-conventions")
	id("org.springframework.boot")
}

group = "io.callisto"
version = "0.0.1-SNAPSHOT"

dependencies {
	"compileOnly"("org.projectlombok:lombok")
	"annotationProcessor"("org.projectlombok:lombok")
	"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// bootBuildImage itself needs no plugin beyond org.springframework.boot, already
// applied above — it's a task the Spring Boot Gradle plugin registers on every
// project it's applied to. What's configured here is just the image name, derived
// from each subproject's own name so core-app/payment-service/notification-service
// each get a sensibly-tagged image without per-service repetition. Cloud Native
// Buildpacks (the mechanism underneath) produce a real, runnable OCI image directly
// from the compiled classes — no Dockerfile anywhere in this repo.
tasks.named<BootBuildImage>("bootBuildImage") {
	imageName.set("ticketing/${project.name}:latest")
}
