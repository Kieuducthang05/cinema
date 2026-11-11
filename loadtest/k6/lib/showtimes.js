import http from 'k6/http';
import { fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// Must match the days seed.sql creates.
export const SEED_DAYS = 75;
// The first Showtimes are kept out of book: contention and race each take a fresh one per step.
export const STEP_SHOWTIMES = 20;
export const RESERVED_SHOWTIMES = 2 * STEP_SHOWTIMES;

// The seed creates showtimes for the SEED_DAYS VN-local dates starting the day after
// it runs. Scan days 0..SEED_DAYS+1 from one clock capture, so a VN midnight that passes
// between seeding and this run neither loses a day nor mixes two dates.
// The API orders each day by start time and id, so every scenario sees the same list.
export function seededShowtimes() {
	const now = Date.now();
	const showtimes = [];
	for (let day = 0; day <= SEED_DAYS + 1; day++) {
		const date = new Date(now + (7 + day * 24) * 3600 * 1000).toISOString().slice(0, 10);
		const res = http.get(`${BASE_URL}/api/showtimes?date=${date}`);
		if (res.status !== 200) fail(`GET /api/showtimes?date=${date}: HTTP ${res.status} ${res.body}`);
		const daily = res.json();
		if (!Array.isArray(daily)) fail(`GET /api/showtimes?date=${date} returned a non-list`);
		showtimes.push(...daily);
	}
	if (showtimes.length === 0) fail('no seeded showtimes in the days around today');
	return showtimes;
}

// The fresh Showtime of one contention or race step; offset is 0 for contention, STEP_SHOWTIMES for race.
export function stepShowtime(offset) {
	const step = Number(__ENV.STEP || 0);
	if (!Number.isInteger(step) || step < 0 || step >= STEP_SHOWTIMES) {
		fail(`STEP must be 0..${STEP_SHOWTIMES - 1}; got ${__ENV.STEP}`);
	}
	return seededShowtimes()[offset + step];
}
