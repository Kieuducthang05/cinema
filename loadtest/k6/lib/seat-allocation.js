// Every seeded Auditorium has 10 rows of 12 Seats.
export const SEATS_PER_SHOWTIME = 120;

// Book gives every iteration of the whole capacity run its own Showtime/Seat pair, so holds never conflict.
// Slot n is Seat n % 120 of Showtime n / 120; null once the seed runs out.
export function slotAt(slot, showtimes, seatsByAuditorium) {
	const showtime = showtimes[Math.floor(slot / SEATS_PER_SHOWTIME)];
	if (!showtime) return null;
	return { showtimeId: showtime.id, seatId: seatsByAuditorium[showtime.auditoriumId][slot % SEATS_PER_SHOWTIME] };
}
