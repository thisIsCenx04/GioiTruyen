package com.storyplatform.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

final class ArchitectureRules {

	private static final String BASE_PACKAGE = "com.storyplatform";

	private static final Set<String> BUSINESS_MODULES = Set.of(
			"analytics",
			"catalog",
			"community",
			"discovery",
			"identity",
			"media",
			"moderation",
			"monetization",
			"notifications",
			"publishing",
			"reading",
			"teams"
	);

	private static final String[] BUSINESS_MODULE_PACKAGES = BUSINESS_MODULES.stream()
			.map(module -> BASE_PACKAGE + "." + module + "..")
			.toArray(String[]::new);

	static final ArchRule BUSINESS_MODULE_BOUNDARIES = classes()
			.that().resideInAnyPackage(BUSINESS_MODULE_PACKAGES)
			.should(respectBusinessModuleBoundaries())
			.allowEmptyShould(true)
			.because("business modules communicate through published application contracts");

	static final ArchRule TOP_LEVEL_PACKAGES_ARE_FREE_OF_CYCLES = slices()
			.matching(BASE_PACKAGE + ".(*)..")
			.should().beFreeOfCycles()
			.because("cyclic module dependencies make a modular monolith impossible to evolve safely");

	static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..",
					"jakarta..",
					"com.mongodb..",
					"org.bson..",
					"org.springframework.data.."
			)
			.allowEmptyShould(true)
			.because("domain code must remain independent from frameworks and persistence");

	static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"..application..",
					"..infrastructure..",
					"..api.."
			)
			.allowEmptyShould(true)
			.because("dependencies must point toward the domain");

	static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses()
			.that().resideInAPackage("..application..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"..infrastructure..",
					"..api.."
			)
			.allowEmptyShould(true)
			.because("application use cases must not depend on delivery or persistence adapters");

	private ArchitectureRules() {
	}

	private static ArchCondition<JavaClass> respectBusinessModuleBoundaries() {
		return new ArchCondition<>(
				"depend only on the same module, shared code, "
						+ "or another module's application contracts"
		) {
			@Override
			public void check(JavaClass sourceClass, ConditionEvents events) {
				sourceClass.getDirectDependenciesFromSelf().stream()
						.filter(ArchitectureRules::isForbiddenCrossModuleDependency)
						.forEach(dependency -> events.add(SimpleConditionEvent.violated(
								dependency,
								dependency.getDescription()
						)));
			}
		};
	}

	private static boolean isForbiddenCrossModuleDependency(Dependency dependency) {
		String sourceModule = moduleName(dependency.getOriginClass());
		String targetModule = moduleName(dependency.getTargetClass());

		if (sourceModule == null || targetModule == null || sourceModule.equals(targetModule)) {
			return false;
		}

		String targetPackage = dependency.getTargetClass().getPackageName();
		return !targetPackage.startsWith(BASE_PACKAGE + "." + targetModule + ".application.contract");
	}

	private static String moduleName(JavaClass javaClass) {
		String packageName = javaClass.getPackageName();
		String prefix = BASE_PACKAGE + ".";

		if (!packageName.startsWith(prefix)) {
			return null;
		}

		String relativePackage = packageName.substring(prefix.length());
		int separator = relativePackage.indexOf('.');
		String candidate = separator < 0 ? relativePackage : relativePackage.substring(0, separator);

		return BUSINESS_MODULES.contains(candidate) ? candidate : null;
	}
}
