plugins {
	id("ticketing.java-conventions")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

group = "io.callisto"
version = "0.0.1-SNAPSHOT"

dependencies {
	"compileOnly"("org.projectlombok:lombok")
	"annotationProcessor"("org.projectlombok:lombok")
	"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
