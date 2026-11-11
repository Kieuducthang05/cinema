// Shared by contention and race: all VUs try to hold one of the same ten Seats of a fresh Showtime per step.
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import { LOAD, MAX_VUS, arrivalRate, handleSummary as sharedHandleSummary, summaryOptions } from './summary.js';
import { classifyConflict, validBooking } from './conflicts.js';
import { savedTokens } from './customers.js';
import { stepShowtime } from './showtimes.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RESULTS_DIR = __ENV.RESULTS_DIR || 'loadtest/results';
const EXPECTED_STATUSES = http.expectedStatuses(201, 409);
const tokens = savedTokens();
const bookingsCreated = new Counter('bookings_created');
const bookingsAbandoned = new Counter('bookings_abandoned');
const bookingConflicts = new Counter('booking_conflicts');
const seatConflicts = new Counter('seat_conflicts');
const pendingCustomerConflicts = new Counter('pending_customer_conflicts');
const busyConflicts = new Counter('busy_conflicts');
const freeAgainConflicts = new Counter('free_again_conflicts');

export function contentionOptions(name) {
	return {
		...summaryOptions,
		scenarios: { [name]: arrivalRate(Math.min(tokens.length, MAX_VUS)) },
	};
}

// offset picks contention's or race's block of reserved Showtimes.
export function contentionSetup(offset) {
	const showtime = stepShowtime(offset);
	const res = http.get(`${BASE_URL}/api/showtimes/${showtime.id}/seats`);
	if (res.status !== 200) fail(`GET seats for ${showtime.id}: HTTP ${res.status} ${res.body}`);
	const seats = res.json();
	if (!Array.isArray(seats)) fail(`seat map for ${showtime.id} is not a list`);
	const seatIds = seats.filter((seat) => seat.status === 'AVAILABLE').slice(0, 10).map((seat) => seat.id);
	if (seatIds.length !== 10) fail(`Showtime ${showtime.id} has only ${seatIds.length} available Seats; need 10`);
	return { showtimeId: showtime.id, seatIds };
}

export function contentionSummary(data) {
	const showtimeId = data.setup_data && data.setup_data.showtimeId;
	return {
		...sharedHandleSummary(data),
		[`${RESULTS_DIR}/contention.json`]: JSON.stringify({ showtimeId }, null, 2),
	};
}

// One measured hold attempt. With abandon, a winner gives its Seat back at once (unmeasured),
// so the next attempts race for a free Seat instead of meeting a held one.
export function attempt({ showtimeId, seatIds }, abandon) {
	const token = tokens[__VU - 1];
	if (!token) fail(`VU ${__VU} has no Customer`);
	const seatId = seatIds[__ITER % seatIds.length];
	const authorization = `Bearer ${token}`;
	const res = http.post(`${BASE_URL}/api/bookings`, JSON.stringify({ showtimeId, seatIds: [seatId] }), {
		...LOAD,
		headers: { 'Content-Type': 'application/json', Authorization: authorization },
		responseCallback: EXPECTED_STATUSES,
	});
	if (res.status === 201) {
		bookingsCreated.add(1);
		const ok = check(res, { '201 creates a one-seat Pending Booking': (response) =>
			validBooking(response, 201, 'PENDING', showtimeId, seatId) });
		if (!ok) fail(`invalid 201 response for Showtime ${showtimeId}, Seat ${seatId}: ${res.body}`);
		if (abandon) {
			const gone = http.del(`${BASE_URL}/api/bookings/${res.json('id')}`, null,
				{ headers: { Authorization: authorization } });
			if (check(gone, { 'abandon releases the Seat': (response) => response.status === 204 })) {
				bookingsAbandoned.add(1);
			}
		}
	} else if (res.status === 409) {
		const reason = classifyConflict(res, seatId);
		const ok = check(res, { '409 is an expected conflict': () => reason !== null });
		if (!ok) fail(`unexpected 409 reason: ${res.body}`);
		bookingConflicts.add(1);
		if (reason === 'seat') seatConflicts.add(1);
		else if (reason === 'busy') busyConflicts.add(1);
		else if (reason === 'free_again') freeAgainConflicts.add(1);
		else pendingCustomerConflicts.add(1);
	} else {
		check(res, { 'response is 201 or expected 409': () => false });
		fail(`unexpected booking response: HTTP ${res.status} ${res.body}`);
	}
}
