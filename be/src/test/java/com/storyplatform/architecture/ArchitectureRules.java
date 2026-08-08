package com.storyplatform.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared ArchUnit rule definitions used by architecture tests.
 */
public final class ArchitectureRules {

    private static final Pattern MODULE_PATTERN =
            Pattern.compile("com\\.storyplatform\\.([^.]+)(\\..*)?");

    /**
     * Business modules must not directly reference internal types from other
     * modules. Only published {@code application.contract} packages of another
     * module may be imported across module boundaries.
     */
    public static final ArchRule BUSINESS_MODULE_BOUNDARIES =
            ArchRuleDefinition.noClasses()
                    .that().resideInAPackage("com.storyplatform.(*)..")
                    .should(new ArchCondition<>("not depend on internal types "
                            + "of other modules") {
                        @Override
                        public void check(
                                JavaClass javaClass,
                                ConditionEvents events
                        ) {
                            String sourceModule = module(
                                    javaClass.getPackageName()
                            );
                            if (sourceModule == null) return;

                            for (JavaClass dependency
                                    : javaClass.getDirectDependenciesFromSelf()
                                    .stream()
                                    .map(d -> d.getTargetClass())
                                    .toList()) {
                                String targetPkg =
                                        dependency.getPackageName();
                                String targetModule = module(targetPkg);
                                if (targetModule == null) continue;
                                if (targetModule.equals(sourceModule)) continue;
                                if (targetPkg.contains(
                                        targetModule
                                                + ".application.contract"
                                )) continue;
                                String message = String.format(
                                        "%s in module '%s' must not depend on "
                                                + "%s in module '%s' "
                                                + "(only .application.contract "
                                                + "packages are allowed)",
                                        javaClass.getName(),
                                        sourceModule,
                                        dependency.getName(),
                                        targetModule
                                );
                                events.add(SimpleConditionEvent.violated(
                                        javaClass, message
                                ));
                            }
                        }
                    })
                    .as("Business modules may only depend on contract packages "
                            + "of other modules");

    /**
     * Top-level module packages must be free of dependency cycles.
     */
    public static final ArchRule TOP_LEVEL_PACKAGES_ARE_FREE_OF_CYCLES =
            SlicesRuleDefinition.slices()
                    .matching("com.storyplatform.(*)..")
                    .should().beFreeOfCycles()
                    .as("Top-level module packages must be cycle-free");

    /**
     * Domain classes must not depend on any framework (Spring, Jakarta EE, …).
     */
    public static final ArchRule DOMAIN_IS_FRAMEWORK_FREE =
            ArchRuleDefinition.noClasses()
                    .that().resideInAPackage("com.storyplatform.(*).domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta..",
                            "javax.."
                    )
                    .as("Domain classes must not depend on frameworks");

    /**
     * Domain classes must not depend on the application or infrastructure layer.
     */
    public static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS =
            ArchRuleDefinition.noClasses()
                    .that().resideInAPackage("com.storyplatform.(*).domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.storyplatform.(*).application..",
                            "com.storyplatform.(*).infrastructure.."
                    )
                    .as("Domain must not depend on application or infrastructure");

    /**
     * Application classes must not depend on infrastructure (adapter) classes.
     */
    public static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS =
            ArchRuleDefinition.noClasses()
                    .that().resideInAPackage("com.storyplatform.(*).application..")
                    .should().dependOnClassesThat().resideInAPackage(
                            "com.storyplatform.(*).infrastructure.."
                    )
                    .as("Application must not depend on infrastructure adapters");

    private ArchitectureRules() {
    }

    private static String module(String packageName) {
        Matcher matcher = MODULE_PATTERN.matcher(packageName);
        return matcher.matches() ? matcher.group(1) : null;
    }
}
