package io.callisto.ticketing.booking;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

@AnalyzeClasses(packages = "io.callisto.ticketing.booking", importOptions = ImportOption.DoNotIncludeTests.class)
class BookingArchitectureTests {

	@ArchTest
	static final ArchRule respectsHexagonalLayering = Architectures.layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.layer("Domain").definedBy("io.callisto.ticketing.booking.domain..")
			.layer("Application").definedBy("io.callisto.ticketing.booking.application..")
			.layer("Adapter").definedBy("io.callisto.ticketing.booking.adapter..")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter")
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter")
			.whereLayer("Adapter").mayNotBeAccessedByAnyLayer();

}
