package io.callisto.ticketing.gcp;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

// Hand-rolled, not spring-cloud-gcp-starter-secretmanager — that starter does not
// support Spring Boot 4 (confirmed: released versions throw at startup from
// outdated ConfigData bootstrap logic, tracked as an open incompatibility upstream).
// This talks to the plain google-cloud-secretmanager client directly instead, which
// has no Spring dependency of its own and so isn't affected by the starter's
// problem.
//
// Runs on the `cloud` profile only, and only if gcp.secrets.mappings.* entries
// exist — core-app declares three (Cloud SQL, Atlas, CloudAMQP),
// notification-service declares one (CloudAMQP only), payment-service declares
// none and this class never does anything there. Each mapping's key is a Secret
// Manager secret name, its value the Spring property key the fetched secret value
// should appear as — e.g. gcp.secrets.mappings.cloud-sql-jdbc-url=spring.datasource.url
// means "fetch the latest version of secret cloud-sql-jdbc-url, expose it as
// spring.datasource.url." Every secret this app uses is a single, complete,
// ready-to-use connection string (credentials embedded), not separate
// username/password secrets — keeps this class generic instead of needing to know
// the shape of any one dependency's credentials.
//
// Registered the newer way for this interface in Boot 4.1: org.springframework.boot.EnvironmentPostProcessor,
// not the deprecated org.springframework.boot.env.EnvironmentPostProcessor — confirmed
// against the actual 4.1.1 jar, since search results on this point were
// contradictory. Still uses META-INF/spring.factories for registration — that
// mechanism itself didn't move to the newer *.imports file format the way
// @AutoConfiguration classes did.
public class GcpSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private final String projectId;
	private final SecretFetcher secretFetcher;

	public GcpSecretsEnvironmentPostProcessor() {
		this(System.getenv("GOOGLE_CLOUD_PROJECT"), GcpSecretsEnvironmentPostProcessor::fetchFromSecretManager);
	}

	// Package-private — lets a test swap in a fake project id / fetcher without
	// any real GCP call or static mocking.
	GcpSecretsEnvironmentPostProcessor(String projectId, SecretFetcher secretFetcher) {
		this.projectId = projectId;
		this.secretFetcher = secretFetcher;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (!environment.acceptsProfiles(Profiles.of("cloud"))) {
			return;
		}
		if (projectId == null || projectId.isBlank()) {
			return;
		}

		Map<String, String> mappings = Binder.get(environment)
				.bind("gcp.secrets.mappings", Bindable.mapOf(String.class, String.class))
				.orElse(Map.of());
		if (mappings.isEmpty()) {
			return;
		}

		Map<String, Object> resolved = new LinkedHashMap<>();
		mappings.forEach((secretName, propertyKey) -> resolved.put(propertyKey, secretFetcher.fetch(projectId, secretName)));

		environment.getPropertySources().addFirst(new MapPropertySource("gcp-secret-manager", resolved));
	}

	private static String fetchFromSecretManager(String projectId, String secretName) {
		try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
			SecretVersionName versionName = SecretVersionName.of(projectId, secretName, "latest");
			return client.accessSecretVersion(versionName).getPayload().getData().toStringUtf8();
		} catch (IOException e) {
			throw new IllegalStateException("Could not fetch secret '" + secretName + "' from Secret Manager", e);
		}
	}

	@FunctionalInterface
	interface SecretFetcher {
		String fetch(String projectId, String secretName);
	}

}
