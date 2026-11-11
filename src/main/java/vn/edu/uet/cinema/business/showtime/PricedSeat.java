package vn.edu.uet.cinema.business.showtime;

import vn.edu.uet.cinema.business.auditorium.Seat;

/** A Seat with its price for one Showtime. */
public record PricedSeat(Seat seat, long price) {
}
