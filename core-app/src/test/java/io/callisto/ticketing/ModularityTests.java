package io.callisto.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

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

	// Part of the final project's definition of done ("a README that explains
	// how to run it locally, and includes the generated module diagram") — this
	// is what actually generates it. A separate artifact from verify()'s own
	// spring-modulith-starter-core, confirmed against the real jars: the
	// Documenter/PlantUML generation lives in spring-modulith-docs, not bundled
	// with boundary verification.
	@Test
	void generatesModuleDiagram() {
		new Documenter(modules).writeModulesAsPlantUml();
	}

}
