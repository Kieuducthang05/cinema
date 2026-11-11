// Contention scenario: all VUs compete for the same ten Seats of one Showtime, which stay held,
// so after the first ten winners nearly every attempt meets a held Seat and gets a 409.
import { STEP_SHOWTIMES } from './lib/showtimes.js';
import { attempt, contentionOptions, contentionSetup, contentionSummary } from './lib/contention.js';

export const options = contentionOptions('contention');
export const handleSummary = contentionSummary;

export function setup() {
	return contentionSetup(0 * STEP_SHOWTIMES);
}

export default function (data) {
	attempt(data, false);
}
