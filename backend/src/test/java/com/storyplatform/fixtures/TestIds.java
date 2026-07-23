package com.storyplatform.fixtures;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class TestIds {

	public static final UUID USER_ID = uuid("user", 1);
	public static final UUID TEAM_ID = uuid("team", 1);
	public static final UUID STORY_ID = uuid("story", 1);
	public static final UUID CHAPTER_ID = uuid("chapter", 1);
	public static final UUID WALLET_ID = uuid("wallet", 1);
	public static final UUID TRANSACTION_ID = uuid("transaction", 1);

	private TestIds() {
	}

	public static UUID uuid(String namespace, long position) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("namespace must not be blank");
		}
		if (position < 0) {
			throw new IllegalArgumentException("position must be non-negative");
		}

		String stableName = namespace + ":" + position;
		return UUID.nameUUIDFromBytes(stableName.getBytes(StandardCharsets.UTF_8));
	}
}
