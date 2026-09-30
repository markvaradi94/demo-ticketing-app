plugins {
	id("ticketing.java-conventions")
}

dependencies {
	// shared started out holding only RabbitMQ message contracts (session 7). This
	// session adds a second, different kind of cross-service concern — GCP secret
	// fetching that both core-app and notification-service need on the cloud
	// profile — rather than duplicating real logic in each. A genuine scope
	// expansion, not scope creep: still "things more than one deployable needs to
	// agree on or reuse," just a second flavor of it.
	//
	// spring-boot (not a starter) — shared is a plain library, not itself a
	// bootable app; this is just the one jar EnvironmentPostProcessor's interface
	// lives in.
	implementation(libs.spring.boot.core)
	implementation(platform(libs.google.cloud.libraries.bom))
	implementation(libs.google.cloud.secretmanager)

	testImplementation("org.junit.jupiter:junit-jupiter")
	testImplementation("org.assertj:assertj-core")
	testImplementation("org.springframework:spring-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
