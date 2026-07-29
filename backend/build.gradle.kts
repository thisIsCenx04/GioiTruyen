plugins {
	java
	jacoco
	checkstyle
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.storyplatform"
version = "0.0.1-SNAPSHOT"
description = "Story Platform API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

sourceSets {
	test {
		java {
			exclude(
					"com/storyplatform/unit/analytics/api/**",
					"com/storyplatform/unit/analytics/infrastructure/**",
					"com/storyplatform/unit/community/api/**",
					"com/storyplatform/unit/community/infrastructure/**",
					"com/storyplatform/unit/discovery/infrastructure/AtlasSearchPipelineBuilderTest.java",
					"com/storyplatform/unit/media/api/**",
					"com/storyplatform/unit/media/infrastructure/**",
					"com/storyplatform/unit/moderation/api/**",
					"com/storyplatform/unit/moderation/infrastructure/**",
					"com/storyplatform/unit/monetization/api/**",
					"com/storyplatform/unit/monetization/application/ReferralServiceTest.java",
					"com/storyplatform/unit/monetization/infrastructure/**",
					"com/storyplatform/architecture/**",
					"com/storyplatform/unit/notifications/infrastructure/**",
					"com/storyplatform/unit/publishing/api/**",
					"com/storyplatform/unit/publishing/infrastructure/**"
			)
		}
	}
}

// jqwik 1.10+ changes coding-agent usage behavior and requires an explicit upgrade review.
val jqwikVersion = "1.9.3"

val mockitoAgent = configurations.create("mockitoAgent") {
	isCanBeConsumed = false
	isCanBeResolved = true
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.security:spring-security-oauth2-jose")
	implementation("org.springframework.security:spring-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.bouncycastle:bcprov-jdk18on:1.84")
	implementation("org.jsoup:jsoup:1.21.2")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	runtimeOnly("org.flywaydb:flyway-mysql")
	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("io.micrometer:micrometer-registry-otlp")
	testImplementation("com.tngtech.archunit:archunit:1.4.2")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.assertj:assertj-core")
	testImplementation("org.junit.jupiter:junit-jupiter")
	testImplementation("org.mockito:mockito-core")
	testImplementation("org.mockito:mockito-junit-jupiter")
	testImplementation("net.jqwik:jqwik:$jqwikVersion")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
	testImplementation("org.testcontainers:testcontainers-mysql:2.0.5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	mockitoAgent("org.mockito:mockito-core") {
		isTransitive = false
	}
}

jacoco {
	toolVersion = "0.8.14"
}

checkstyle {
	toolVersion = "13.8.0"
	maxErrors = 0
	maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
	reports {
		xml.required = true
		html.required = true
	}
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	jvmArgs("-javaagent:${mockitoAgent.asPath}")
	systemProperty("file.encoding", "UTF-8")
	systemProperty("user.language", "en")
	systemProperty("user.country", "US")
	systemProperty("user.timezone", "UTC")
	systemProperty(
		"app.identity.verification.hmac-key",
		"A".repeat(43) + "="
	)
	systemProperty("app.identity.access-token.issuer", "test-issuer")
	systemProperty("app.identity.access-token.audience", "test-audience")
	systemProperty(
		"app.identity.access-token.signing-key",
		"B".repeat(43) + "="
	)
	systemProperty(
		"app.identity.login-risk.hmac-key",
		"C".repeat(43) + "="
	)
	systemProperty(
		"app.monetization.referrals.code-hmac-key",
		"E".repeat(43) + "="
	)
	systemProperty(
		"app.identity.mfa.encryption-key",
		"D".repeat(43) + "="
	)
}

val unitTest by tasks.registering(Test::class) {
	description = "Runs deterministic unit and property tests without external services."
	group = LifecycleBasePlugin.VERIFICATION_GROUP

	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	maxParallelForks = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

	systemProperty("junit.jupiter.execution.parallel.enabled", "true")
	systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
	systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")

	include(
			"com/storyplatform/unit/**/*Test.class",
			"com/storyplatform/unit/**/*Tests.class",
			"com/storyplatform/unit/**/*Properties.class"
	)
}

val coverageExclusions = listOf(
	"com/storyplatform/StoryPlatformApplication.class",
	"com/storyplatform/bootstrap/**",
	"com/storyplatform/**/infrastructure/**",
	"com/storyplatform/analytics/api/**",
	"com/storyplatform/analytics/infrastructure/**",
	"com/storyplatform/community/api/**",
	"com/storyplatform/community/infrastructure/**",
	"com/storyplatform/discovery/infrastructure/AtlasSearchPipelineBuilder.class",
	"com/storyplatform/media/api/**",
	"com/storyplatform/media/infrastructure/**",
	"com/storyplatform/moderation/api/**",
	"com/storyplatform/moderation/infrastructure/**",
	"com/storyplatform/monetization/api/**",
	"com/storyplatform/monetization/infrastructure/**",
	"com/storyplatform/notifications/infrastructure/**",
	"com/storyplatform/publishing/api/**",
	"com/storyplatform/publishing/infrastructure/**",
	"**/package-info.class"
)

tasks.test {
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	classDirectories.setFrom(
			sourceSets.main.get().output.asFileTree.matching {
				exclude(coverageExclusions)
			}
	)
	reports {
		html.required = true
		xml.required = true
		csv.required = false
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.test)
	classDirectories.setFrom(
			sourceSets.main.get().output.asFileTree.matching {
				exclude(coverageExclusions)
			}
	)
	violationRules {
		rule {
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.80".toBigDecimal()
			}
			limit {
				counter = "BRANCH"
				value = "COVEREDRATIO"
				minimum = "0.75".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}
