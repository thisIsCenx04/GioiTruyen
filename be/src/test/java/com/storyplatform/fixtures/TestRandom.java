package com.storyplatform.fixtures;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

public final class TestRandom {

	private TestRandom() {
	}

	public static Random seeded(long seed) {
		return new Random(seed);
	}

	public static Random named(String stableTestName) {
		if (stableTestName == null || stableTestName.isBlank()) {
			throw new IllegalArgumentException("stableTestName must not be blank");
		}

		UUID seedSource = UUID.nameUUIDFromBytes(stableTestName.getBytes(StandardCharsets.UTF_8));
		return seeded(seedSource.getMostSignificantBits() ^ seedSource.getLeastSignificantBits());
	}
}
