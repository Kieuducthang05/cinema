package vn.edu.uet.cinema.business.auditorium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatLayoutTest {

	@Test
	void generatesRowMajorSeatsWithConfiguredTypes() {
		List<Seat> seats = SeatLayout.generate(3, 2, List.of("B"), List.of("C"));

		assertThat(seats).extracting(Seat::label)
			.containsExactly("A1", "A2", "B1", "B2", "C1", "C2");
		assertThat(seats).extracting(Seat::type)
			.containsExactly(SeatType.STANDARD, SeatType.STANDARD, SeatType.VIP, SeatType.VIP,
				SeatType.COUPLE, SeatType.COUPLE);
	}

	@Test
	void coupleRowHasOneSeatPerPosition() {
		List<Seat> seats = SeatLayout.generate(1, 1, List.of(), List.of("A"));

		assertThat(seats).hasSize(1);
		assertThat(seats.getFirst().type()).isEqualTo(SeatType.COUPLE);
	}

	@Test
	void generatedSeatsHaveDistinctIdsAndReturnedListIsImmutable() {
		List<Seat> seats = SeatLayout.generate(2, 2, List.of(), List.of());

		assertThat(seats).extracting(Seat::id).doesNotHaveDuplicates()
			.allMatch(id -> id != null && id instanceof UUID);
		assertThatThrownBy(() -> seats.add(new Seat(UUID.randomUUID(), "A", 3, SeatType.STANDARD)));
	}

	@Test
	void rejectsLayoutsExceedingGeneratedSeatLimit() {
		assertThatThrownBy(() -> SeatLayout.generate(2, Integer.MAX_VALUE, List.of(), List.of()))
			.isInstanceOf(InvalidSeatLayoutException.class)
			.hasMessage("Seat layout is not allowed")
			.hasMessageNotContaining("10000");
		assertInvalid(() -> SeatLayout.generate(26, 385, List.of(), List.of()));
	}

	@Test
	void rejectsInvalidDimensionsAndRowLists() {
		assertInvalid(() -> SeatLayout.generate(0, 1, List.of(), List.of()));
		assertInvalid(() -> SeatLayout.generate(27, 1, List.of(), List.of()));
		assertInvalid(() -> SeatLayout.generate(1, 0, List.of(), List.of()));
		assertInvalid(() -> SeatLayout.generate(1, 1, null, List.of()));
		assertInvalid(() -> SeatLayout.generate(1, 1, List.of(), null));
		assertInvalid(() -> SeatLayout.generate(2, 1, List.of("A", "A"), List.of()));
		assertInvalid(() -> SeatLayout.generate(2, 1, List.of("A"), List.of("A")));
		assertInvalid(() -> SeatLayout.generate(2, 1, List.of("C"), List.of()));
		assertInvalid(() -> SeatLayout.generate(2, 1, Arrays.asList((String) null), List.of()));
	}

	private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable generation) {
		assertThatThrownBy(generation).isInstanceOf(InvalidSeatLayoutException.class);
	}
}
