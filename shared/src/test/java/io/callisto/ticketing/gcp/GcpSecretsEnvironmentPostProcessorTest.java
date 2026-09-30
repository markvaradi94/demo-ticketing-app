package io.callisto.ticketing.gcp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test: the branching logic (which profile, which project id, which mappings)
 * with a recording fake in place of the real GCP call — no network, no real
 * credentials, no Spring context. Proves this class does the right thing without
 * ever needing to reach Secret Manager for real; the actual fetch implementation
 * (a five-line try-with-resources around the Google client) is trusted the same way
 * this course has always trusted a thin framework/SDK call it isn't the point of
 * the lesson to re-test.
 */
class GcpSecretsEnvironmentPostProcessorTest {

	private final List<String> fetchedSecretNames = new ArrayList<>();

	@Test
	void doesNothingWhenTheCloudProfileIsNotActive() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("gcp.secrets.mappings.db-password", "spring.datasource.password");
		GcpSecretsEnvironmentPostProcessor postProcessor = postProcessor("project-1");

		postProcessor.postProcessEnvironment(environment, null);

		assertThat(fetchedSecretNames).isEmpty();
		assertThat(environment.getProperty("spring.datasource.password")).isNull();
	}

	@Test
	void doesNothingWhenNoProjectIdIsConfigured() {
		MockEnvironment environment = cloudEnvironment();
		environment.setProperty("gcp.secrets.mappings.db-password", "spring.datasource.password");
		GcpSecretsEnvironmentPostProcessor postProcessor = postProcessor(null);

		postProcessor.postProcessEnvironment(environment, null);

		assertThat(fetchedSecretNames).isEmpty();
	}

	@Test
	void doesNothingWhenNoMappingsAreConfigured() {
		MockEnvironment environment = cloudEnvironment();
		GcpSecretsEnvironmentPostProcessor postProcessor = postProcessor("project-1");

		postProcessor.postProcessEnvironment(environment, null);

		assertThat(fetchedSecretNames).isEmpty();
	}

	@Test
	void fetchesAndExposesEachMappedSecret() {
		MockEnvironment environment = cloudEnvironment();
		environment.setProperty("gcp.secrets.mappings.cloud-sql-jdbc-url", "spring.datasource.url");
		environment.setProperty("gcp.secrets.mappings.atlas-uri", "spring.mongodb.uri");
		GcpSecretsEnvironmentPostProcessor postProcessor = postProcessor("project-1");

		postProcessor.postProcessEnvironment(environment, null);

		assertThat(fetchedSecretNames).containsExactlyInAnyOrder("cloud-sql-jdbc-url", "atlas-uri");
		assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("value-of-cloud-sql-jdbc-url");
		assertThat(environment.getProperty("spring.mongodb.uri")).isEqualTo("value-of-atlas-uri");
	}

	private static MockEnvironment cloudEnvironment() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("cloud");
		return environment;
	}

	private GcpSecretsEnvironmentPostProcessor postProcessor(String projectId) {
		return new GcpSecretsEnvironmentPostProcessor(projectId, (project, secretName) -> {
			fetchedSecretNames.add(secretName);
			return "value-of-" + secretName;
		});
	}

}
