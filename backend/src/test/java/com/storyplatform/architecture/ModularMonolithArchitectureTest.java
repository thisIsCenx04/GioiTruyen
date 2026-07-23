package com.storyplatform.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ModularMonolithArchitectureTest {

	private static final ClassFileImporter PRODUCTION_CLASSES = new ClassFileImporter()
			.withImportOption(new ImportOption.DoNotIncludeTests());

	@Test
	void businessModulesRespectPublishedBoundaries() {
		ArchitectureRules.BUSINESS_MODULE_BOUNDARIES.check(
				PRODUCTION_CLASSES.importPackages("com.storyplatform")
		);
	}

	@Test
	void topLevelPackagesAreFreeOfCycles() {
		ArchitectureRules.TOP_LEVEL_PACKAGES_ARE_FREE_OF_CYCLES.check(
				PRODUCTION_CLASSES.importPackages("com.storyplatform")
		);
	}

	@Test
	void domainIsFrameworkFree() {
		ArchitectureRules.DOMAIN_IS_FRAMEWORK_FREE.check(
				PRODUCTION_CLASSES.importPackages("com.storyplatform")
		);
	}

	@Test
	void dependenciesPointTowardTheDomain() {
		var productionClasses = PRODUCTION_CLASSES.importPackages("com.storyplatform");

		ArchitectureRules.DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS.check(productionClasses);
		ArchitectureRules.APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS.check(productionClasses);
	}
}
