package vn.edu.uet.cinema.business.showtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.edu.uet.cinema.business.auditorium.SeatType;

class SeatPricingTest {

	@Test
	void pricesEverySeatTypeInVnd() {
		assertThat(SeatPricing.price(SeatType.STANDARD, 100_000L)).isEqualTo(100_000L);
		assertThat(SeatPricing.price(SeatType.VIP, 100_000L)).isEqualTo(120_000L);
		assertThat(SeatPricing.price(SeatType.COUPLE, 100_000L)).isEqualTo(210_000L);
	}

	@Test
	void rejectsPricingOverflow() {
		assertThatThrownBy(() -> SeatPricing.price(SeatType.VIP, Long.MAX_VALUE))
			.isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> SeatPricing.price(SeatType.COUPLE, Long.MAX_VALUE / 2 + 1))
			.isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> SeatPricing.price(SeatType.COUPLE, Long.MAX_VALUE / 2))
			.isInstanceOf(ArithmeticException.class);
	}
}
