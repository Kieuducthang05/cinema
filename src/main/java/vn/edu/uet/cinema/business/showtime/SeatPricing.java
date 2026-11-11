package vn.edu.uet.cinema.business.showtime;

import vn.edu.uet.cinema.business.auditorium.SeatType;

public final class SeatPricing {

	private SeatPricing() {
	}

	public static long price(SeatType type, long basePrice) {
		return switch (type) {
			case STANDARD -> basePrice;
			case VIP -> Math.addExact(basePrice, 20_000L);
			case COUPLE -> Math.addExact(Math.multiplyExact(basePrice, 2L), 10_000L);
		};
	}
}
