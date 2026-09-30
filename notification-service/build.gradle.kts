plugins {
	id("ticketing.spring-boot-conventions")
}

dependencies {
	implementation(project(":shared"))
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-tracing-bridge-brave")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
}
