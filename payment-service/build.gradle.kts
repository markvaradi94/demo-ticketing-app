plugins {
	id("ticketing.spring-boot-conventions")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-tracing-bridge-brave")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
