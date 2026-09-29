package io.callisto.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Enforces the module boundaries package structure alone can't: catalog/booking/
 * review/shared are meant to be independent modules, only reaching each other's
 * public (root-package) API, never another module's internals, never a cycle.
 * verify() throws if any of that's violated. Pure static analysis over the compiled
 * classes (ArchUnit under the hood) — no Spring context, no database, fast enough to
 * run on every build.
 */
class ModularityTests {

	ApplicationModules modules = ApplicationModules.of(TicketingApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

}
