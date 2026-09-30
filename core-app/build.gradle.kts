plugins {
	id("ticketing.spring-boot-conventions")
}

dependencies {
	implementation(project(":shared"))
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-restclient")
	implementation("io.micrometer:micrometer-tracing-bridge-brave")
	implementation(libs.spring.modulith.starter.core)
	implementation(libs.resilience4j.spring.boot4)
	runtimeOnly("org.postgresql:postgresql")
	// Only actually exercised on the cloud profile — the driver-level piece that
	// lets a plain JDBC URL (?cloudSqlInstance=...&socketFactory=...) reach Cloud
	// SQL through the Cloud SQL Auth Proxy's connector, no separate proxy process
	// or sidecar container needed.
	runtimeOnly(libs.google.cloud.sql.postgres.socket.factory)
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation(libs.testcontainers.postgresql)
	testImplementation(libs.testcontainers.mongodb)
	testImplementation(libs.spring.modulith.starter.test)
	testImplementation(libs.archunit.junit5)
}
