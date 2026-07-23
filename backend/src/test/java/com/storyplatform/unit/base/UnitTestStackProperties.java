package com.storyplatform.unit.base;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTestStackProperties {

	@Property(tries = 100)
	void integerTextRoundTripPreservesValue(@ForAll int value) {
		String serialized = Integer.toString(value);

		assertThat(Integer.parseInt(serialized)).isEqualTo(value);
	}
}
