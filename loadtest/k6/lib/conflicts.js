// Checks booking responses without importing k6, so Node can test them.
// A seat conflict is decided by the root `seatIds` property, never by the human message.
const PROBLEM_MEDIA_TYPE = 'application/problem+json';
const PENDING_CUSTOMER_PREFIX = 'Customer already has a Pending Booking';
// A Booking that lost a deadlock twice (ADR 0004); the client may retry.
const BUSY_PREFIX = 'Seats are being booked by others right now';

function isProblemJson(contentType) {
	if (typeof contentType !== 'string') return false;
	// Parameters are not the classifier's concern: quotes may contain ';' or '='.
	return contentType.split(';', 1)[0].trim().toLowerCase() === PROBLEM_MEDIA_TYPE;
}

// The expected booking 409s: seat, free_again (the winner abandoned before the loser re-read,
// so no Seat is listed and a retry may win), pending_customer or busy; null for anything else.
export function conflictReason(status, contentType, problem, seatId) {
	if (status !== 409 || typeof seatId !== 'string' || !isProblemJson(contentType)) return null;
	if (problem === null || typeof problem !== 'object' || problem.status !== 409) return null;
	if ('seatIds' in problem) {
		if (!Array.isArray(problem.seatIds)) return null;
		if (problem.seatIds.length === 0) return 'free_again';
		return problem.seatIds.includes(seatId) ? 'seat' : null;
	}
	if (typeof problem.detail === 'string' && problem.detail.startsWith(PENDING_CUSTOMER_PREFIX)) {
		return 'pending_customer';
	}
	if (typeof problem.detail === 'string' && problem.detail.startsWith(BUSY_PREFIX)) return 'busy';
	return null;
}

// The conflictReason of a k6 response.
export function classifyConflict(res, seatId) {
	let problem = null;
	try {
		problem = res.json();
	} catch (_) {
		// A 409 without a problem body is unexpected: null.
	}
	return conflictReason(res.status, res.headers['Content-Type'], problem, seatId);
}

// A one-Seat Booking response with the given status; bookingId, when given, must match too.
export function validBooking(res, status, bookingStatus, showtimeId, seatId, bookingId) {
	if (res.status !== status) return false;
	try {
		const booking = res.json();
		return typeof booking.id === 'string' && (!bookingId || booking.id === bookingId)
			&& booking.showtimeId === showtimeId && booking.status === bookingStatus
			&& Array.isArray(booking.seats) && booking.seats.length === 1
			&& booking.seats[0].seatId === seatId && booking.totalPrice > 0;
	} catch (_) {
		return false;
	}
}
