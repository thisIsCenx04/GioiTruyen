package com.storyplatform.unit.base;

import com.storyplatform.fixtures.FixedClock;
import com.storyplatform.fixtures.TestDataBuilder;
import com.storyplatform.fixtures.TestIds;
import com.storyplatform.fixtures.TestRandom;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static com.storyplatform.fixtures.MoneyAssertions.assertThatVnd;
import static com.storyplatform.fixtures.MoneyAssertions.assertThatXu;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicFixturesTest {

	@Test
	void fixedClockIsImmutableAndIndependentFromMachineTimezone() {
		FixedClock clock = FixedClock.at("2026-07-23T08:30:00Z");

		assertThat(clock.instant()).isEqualTo(Instant.parse("2026-07-23T08:30:00Z"));
		assertThat(clock.instant()).isEqualTo(clock.instant());
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Z"));
		assertThat(clock.plus(Duration.ofMinutes(5)).instant())
				.isEqualTo(Instant.parse("2026-07-23T08:35:00Z"));
		assertThat(clock.instant()).isEqualTo(Instant.parse("2026-07-23T08:30:00Z"));
	}

	@Test
	void namedIdsAreStableAndSeparatedByNamespaceAndPosition() {
		assertThat(TestIds.uuid("story", 7)).isEqualTo(TestIds.uuid("story", 7));
		assertThat(TestIds.uuid("story", 7))
				.isNotEqualTo(TestIds.uuid("story", 8))
				.isNotEqualTo(TestIds.uuid("chapter", 7));
	}

	@Test
	void invalidIdInputsFailFast() {
		assertThatThrownBy(() -> TestIds.uuid(" ", 1))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TestIds.uuid("story", -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void namedRandomProducesTheSameSequenceForEveryFreshFixture() {
		var first = TestRandom.named("publishing-state-transition");
		var second = TestRandom.named("publishing-state-transition");

		assertThat(first.ints(32).toArray()).containsExactly(second.ints(32).toArray());
	}

	@Test
	void moneyAssertionsKeepXuAndVndExpectationsAsExactIntegers() {
		assertThatVnd(100_000L).isEqualTo(100_000L);
		assertThatXu(90_000L).isEqualTo(90_000L);
	}

	@Test
	void testDataBuilderDefinesTheFixtureConstructionContract() {
		TestDataBuilder<String> builder = () -> "ready";

		assertThat(builder.build()).isEqualTo("ready");
	}
}
