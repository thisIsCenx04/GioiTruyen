plugins {
	java
	jacoco
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

// jqwik 1.10+ changes coding-agent usage behavior and requires an explicit upgrade review.
val jqwikVersion = "1.9.3"

val mockitoAgent = configurations.create("mockitoAgent") {
	isCanBeConsumed = false
	isCanBeResolved = true
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	testImplementation("com.tngtech.archunit:archunit:1.4.2")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")
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
	testImplementation("org.testcontainers:testcontainers-mongodb:2.0.5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	mockitoAgent("org.mockito:mockito-core") {
		isTransitive = false
	}
}

jacoco {
	toolVersion = "0.8.14"
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	jvmArgs("-javaagent:${mockitoAgent.asPath}")
	systemProperty("file.encoding", "UTF-8")
	systemProperty("user.language", "en")
	systemProperty("user.country", "US")
	systemProperty("user.timezone", "UTC")
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
