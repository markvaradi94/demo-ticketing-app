package io.callisto.ticketing.gcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves {@code META-INF/spring.factories} registration genuinely works — Spring
 * Boot's real bootstrap discovers and invokes this class via classpath scanning, not
 * a direct method call the way {@link GcpSecretsEnvironmentPostProcessorTest} makes
 * one. No GOOGLE_CLOUD_PROJECT env var is set in this test environment, so the
 * no-args constructor's real-GCP-client path resolves a null project id and the
 * post-processor no-ops — this test's job is confirming boot doesn't fail while
 * that happens, not exercising the real fetch (which needs real credentials this
 * repo's test suite doesn't have).
 */
class GcpSecretsEnvironmentPostProcessorRegistrationTest {

	@Test
	void bootsCleanlyOnTheCloudProfileWithoutAGcpProjectConfigured() {
		SpringApplication application = new SpringApplication(MinimalConfig.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setAdditionalProfiles("cloud");

		assertThatCode(() -> {
			ConfigurableApplicationContext context = application.run();
			context.close();
		}).doesNotThrowAnyException();
	}

	@Configuration
	static class MinimalConfig {
	}

}
