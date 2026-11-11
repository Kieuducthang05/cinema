// Book scenario: each VU is its own Customer and every iteration holds then pays its own Showtime/Seat pair.
import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import { LOAD, MAX_VUS, arrivalRate, handleSummary, summaryOptions } from './lib/summary.js';
import { classifyConflict, validBooking } from './lib/conflicts.js';
import { savedTokens } from './lib/customers.js';
import { SEATS_PER_SHOWTIME, slotAt } from './lib/seat-allocation.js';
import { RESERVED_SHOWTIMES, seededShowtimes } from './lib/showtimes.js';

export { handleSummary };

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
// A busy 409 is the service asking for a retry: counted, not an error.
const HOLD_STATUSES = http.expectedStatuses(201, 409);
// Slots the earlier steps of this capacity run may have used; the notebook passes it with -e.
const SLOT_OFFSET = Number(__ENV.SLOT_OFFSET || 0);
const tokens = savedTokens();
// Iterations that found no unused Seat: the step ran out of seed, it did not break.
const seatsExhausted = new Counter('seats_exhausted');
const busyConflicts = new Counter('busy_conflicts');

export const options = {
	...summaryOptions,
	scenarios: { book: arrivalRate(Math.min(tokens.length, MAX_VUS)) },
};

export function setup() {
	// Keep the first Showtimes free for the contention and race steps.
	const showtimes = seededShowtimes().slice(RESERVED_SHOWTIMES)
		.map((showtime) => ({ id: showtime.id, auditoriumId: showtime.auditoriumId }));
	if (showtimes.length === 0) fail('no seeded Showtimes left after the contention and race ones');
	// All Showtimes of an Auditorium share its Seats, so one seat map per Auditorium is enough.
	const firsts = [...new Map(showtimes.map((showtime) => [showtime.auditoriumId, showtime])).values()];
	const maps = http.batch(firsts.map((showtime) => ['GET', `${BASE_URL}/api/showtimes/${showtime.id}/seats`]));
	const seatsByAuditorium = {};
	maps.forEach((res, i) => {
		if (res.status !== 200) fail(`GET seats for ${firsts[i].id}: HTTP ${res.status} ${res.body}`);
		const seats = res.json();
		if (!Array.isArray(seats) || seats.length !== SEATS_PER_SHOWTIME) fail(`invalid seat map for ${firsts[i].id}`);
		seatsByAuditorium[firsts[i].auditoriumId] = seats.map((seat) => seat.id);
	});
	return { showtimes, seatsByAuditorium };
}

export default function ({ showtimes, seatsByAuditorium }) {
	// A VU runs one iteration at a time, so its Customer never has two Pending Bookings.
	const token = tokens[__VU - 1];
	if (!token) fail(`VU ${__VU} has no Customer`);
	const slot = slotAt(SLOT_OFFSET + exec.scenario.iterationInTest, showtimes, seatsByAuditorium);
	if (!slot) {
		seatsExhausted.add(1);
		return;
	}
	const { showtimeId, seatId } = slot;
	const headers = { ...JSON_HEADERS, Authorization: `Bearer ${token}` };
	const hold = http.post(`${BASE_URL}/api/bookings`, JSON.stringify({ showtimeId, seatIds: [seatId] }),
		{ ...LOAD, headers, responseCallback: HOLD_STATUSES });
	if (hold.status === 409 && classifyConflict(hold, seatId) === 'busy') {
		busyConflicts.add(1);
		return;
	}
	const held = check(hold, { 'hold creates one Pending Booking': (res) =>
		validBooking(res, 201, 'PENDING', showtimeId, seatId) });
	if (!held) fail(`hold failed for Showtime ${showtimeId}, Seat ${seatId}: HTTP ${hold.status} ${hold.body}`);

	const bookingId = hold.json('id');
	const payment = http.post(`${BASE_URL}/api/bookings/${bookingId}/payment`, '',
		{ ...LOAD, headers: { Authorization: headers.Authorization } });
	const paid = check(payment, { 'payment confirms the held Booking': (res) =>
		validBooking(res, 200, 'CONFIRMED', showtimeId, seatId, bookingId) });
	if (!paid) fail(`payment failed for Booking ${bookingId}: HTTP ${payment.status} ${payment.body}`);
}
