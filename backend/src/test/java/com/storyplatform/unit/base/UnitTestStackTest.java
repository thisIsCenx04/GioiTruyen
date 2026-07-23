package com.storyplatform.unit.base;

import org.junit.jupiter.api.Test;

import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnitTestStackTest {

	@Test
	void junitAssertJAndMockitoAreAvailableToUnitTests() {
		IntSupplier port = mock(IntSupplier.class);
		when(port.getAsInt()).thenReturn(42);

		int result = port.getAsInt();

		assertThat(result).isEqualTo(42);
		verify(port).getAsInt();
	}
}
