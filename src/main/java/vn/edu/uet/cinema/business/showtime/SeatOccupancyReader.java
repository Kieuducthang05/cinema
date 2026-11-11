package vn.edu.uet.cinema.business.showtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface SeatOccupancyReader {

	/** Missing seats have the default status AVAILABLE. */
	Map<UUID, SeatStatus> statuses(UUID showtimeId, Instant now);
}
