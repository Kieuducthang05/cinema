package vn.edu.uet.cinema.business.showtime;

/** One Seat of a seat map: its price and its status at the time asked. */
public record SeatMapEntry(PricedSeat seat, SeatStatus status) {
}
