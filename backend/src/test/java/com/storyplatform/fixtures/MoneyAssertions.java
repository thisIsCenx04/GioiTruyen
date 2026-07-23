package com.storyplatform.fixtures;

import org.assertj.core.api.AbstractLongAssert;

import static org.assertj.core.api.Assertions.assertThat;

public final class MoneyAssertions {

	private MoneyAssertions() {
	}

	public static AbstractLongAssert<?> assertThatXu(long actualXu) {
		return assertThat(actualXu).as("XU amount");
	}

	public static AbstractLongAssert<?> assertThatVnd(long actualVnd) {
		return assertThat(actualVnd).as("VND amount");
	}
}
