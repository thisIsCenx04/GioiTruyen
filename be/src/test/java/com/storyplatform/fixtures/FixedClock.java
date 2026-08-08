package com.storyplatform.fixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

public final class FixedClock extends Clock {

	public static final Instant DEFAULT_INSTANT = Instant.parse("2026-01-15T08:30:00Z");

	private final Instant fixedInstant;
	private final ZoneId zone;

	private FixedClock(Instant fixedInstant, ZoneId zone) {
		this.fixedInstant = Objects.requireNonNull(fixedInstant, "fixedInstant");
		this.zone = Objects.requireNonNull(zone, "zone");
	}

	public static FixedClock defaultClock() {
		return at(DEFAULT_INSTANT);
	}

	public static FixedClock at(Instant instant) {
		return new FixedClock(instant, ZoneOffset.UTC);
	}

	public static FixedClock at(String instant) {
		return at(Instant.parse(instant));
	}

	public FixedClock plus(Duration duration) {
		return new FixedClock(fixedInstant.plus(duration), zone);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this.zone.equals(zone) ? this : new FixedClock(fixedInstant, zone);
	}

	@Override
	public Instant instant() {
		return fixedInstant;
	}
}
