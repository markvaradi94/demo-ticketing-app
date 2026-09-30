pluginManagement {
	includeBuild("build-logic")
}

rootProject.name = "ticketing"

include("core-app", "payment-service", "notification-service", "shared")
