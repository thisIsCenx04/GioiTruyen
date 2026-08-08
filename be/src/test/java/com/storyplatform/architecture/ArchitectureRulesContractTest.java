package com.storyplatform.architecture;

import com.storyplatform.catalog.architecturefixture.CatalogFixture;
import com.storyplatform.publishing.architecturefixture.InvalidPublishingFixture;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureRulesContractTest {

	@Test
	void crossModuleDependencyFixtureViolatesBoundaryRule() {
		var violatingClasses = new ClassFileImporter().importClasses(
				CatalogFixture.class,
				InvalidPublishingFixture.class
		);

		assertThatThrownBy(() -> ArchitectureRules.BUSINESS_MODULE_BOUNDARIES.check(violatingClasses))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("InvalidPublishingFixture")
				.hasMessageContaining("CatalogFixture");
	}
}
