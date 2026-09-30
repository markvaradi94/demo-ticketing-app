plugins {
	id("ticketing.spring-boot-conventions")
}

dependencies {
	implementation(project(":shared"))
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// Not pulled in transitively — unlike core-app/payment-service, this service has
	// no web starter, and JacksonJsonMessageConverter needs Jackson 3 on the
	// classpath to actually construct, not just compile against.
	implementation("org.springframework.boot:spring-boot-starter-jackson")
	implementation("io.micrometer:micrometer-tracing-bridge-brave")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation(libs.testcontainers.rabbitmq)
}
