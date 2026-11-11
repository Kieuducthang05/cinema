package vn.edu.uet.cinema.business.showtime;

/** Why a Showtime was Cancelled. The note is optional, except for {@link CancellationReason#OTHER}. */
public record Cancellation(CancellationReason reason, String note) {
}
