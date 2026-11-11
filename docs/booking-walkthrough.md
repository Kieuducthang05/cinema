# Booking walkthrough

English | [Tiếng Việt](booking-walkthrough.vi.md)

An Admin builds a small catalogue, then a Customer holds two Seats, pays, and sees them sold. Two optional steps show a seat conflict and a Showtime cancellation.

Follow it in Swagger UI (good for a live demo) or run it as one bash script with `curl`. Either way the service must be running, for example with `docker compose up` from the [README](../README.md#getting-started). Log in as the Admin with the `ADMIN_EMAIL` and `ADMIN_PASSWORD` the service was started with.

## In Swagger UI

Open `/swagger-ui.html` on the running service (locally http://localhost:8080/swagger-ui.html). For each step, expand the operation, click **Try it out**, paste the body, click **Execute**, and copy the `id` you need from the response.

Swagger UI keeps one token at a time. Steps 1 to 2 use the Admin token, steps 3 to 6 the Customer token, and step 8 the Admin token again.

### 1. Admin logs in

`POST /api/auth/login`

```json
{"email": "admin@cinema.local", "password": "<ADMIN_PASSWORD>"}
```

Copy `accessToken`, click **Authorize** at the top of the page, paste it, and close the dialog. Operations with a lock icon now send it.

### 2. Admin builds the catalogue

`POST /api/cinemas` → copy `id` as `CINEMA`

```json
{"name": "Central Cinema", "address": "1 Example Street"}
```

`POST /api/cinemas/{id}/auditoriums` with `id` = `CINEMA` → copy `id` as `AUDITORIUM`. This makes 5 rows of 8 Seats: rows A to C Standard, D VIP, E Couple.

```json
{"name": "Screen 1", "rows": 5, "seatsPerRow": 8, "vipRows": ["D"], "coupleRows": ["E"]}
```

`POST /api/movies` → copy `id` as `MOVIE`

```json
{"title": "The Example", "description": "A story about cinema.", "durationMinutes": 120, "genre": "Drama",
 "ageRating": "T13", "releaseDate": "2026-05-01", "posterUrl": "https://example.com/poster.jpg"}
```

`POST /api/showtimes` → copy `id` as `SHOWTIME`. `startsAt` must be in the future. The response shows `endsAt` = start + 120 min + 15 min cleaning.

```json
{"movieId": "<MOVIE>", "auditoriumId": "<AUDITORIUM>", "startsAt": "2030-01-01T19:30:00+07:00", "basePrice": 100000}
```

### 3. A Customer registers and logs in

`POST /api/auth/register` (returns `409` if the email is already registered; just log in)

```json
{"email": "bob@example.com", "password": "password123", "fullName": "Bob"}
```

`POST /api/auth/login` with the same email and password, then **Authorize** again with the new `accessToken` (log out of the Admin token first).

### 4. The Customer opens the seat map

`GET /api/showtimes/{id}/seats` with `id` = `SHOWTIME`. Every Seat is `AVAILABLE`, with its `label` (`A1`), `type` and `price`: Standard 100,000, VIP 120,000, Couple 210,000. Copy the `id` of `A1` and `A2`.

### 5. The Customer holds two Seats

`POST /api/bookings` → copy `id` as `BOOKING`

```json
{"showtimeId": "<SHOWTIME>", "seatIds": ["<A1 id>", "<A2 id>"]}
```

The response is `201` with `status` `PENDING`, `expiresAt` 10 minutes from now and `totalPrice` 200000. Re-open the seat map: `A1` and `A2` are now `HELD`.

### 6. The Customer pays

`POST /api/bookings/{id}/payment` with `id` = `BOOKING`. The Booking becomes `CONFIRMED`; the seat map shows `A1` and `A2` as `SOLD`. Paying again returns the same Booking (`200`).

### 7. Optional: a seat conflict

Still as the Customer, `POST /api/bookings` with `A2` and `A3`. The response is `409` in `application/problem+json`, listing the id of `A2` as taken. Nothing is held, not even `A3`.

### 8. Optional: cancelling the Showtime

Authorize as the Admin again, then `POST /api/showtimes/{id}/cancellation` with `id` = `SHOWTIME`:

```json
{"reason": "EQUIPMENT_FAILURE", "note": "Projector broke"}
```

The Showtime is `CANCELLED`. Log in as the Customer: `GET /api/bookings/{id}` shows the Booking as `REFUNDED`, and the seat map now returns `409`.

### If something fails

| Response | Likely cause |
|---|---|
| `401` | No token, or it expired after 1 hour: log in and **Authorize** again |
| `403` | Wrong role: catalogue writes need the Admin token, bookings the Customer token |
| `409` on `POST /showtimes` | The time overlaps another Showtime in that Auditorium: pick another `startsAt` |
| `409` on `POST /bookings` | The Customer already has a live Pending Booking: pay or `DELETE` it first |
| `409` on payment | The 10-minute Hold lapsed: book again |

## As a bash script

Paste this into a bash shell from the repository root. It needs `curl` and `jq` and covers steps 1 to 6.

```bash
set -a; source .env; set +a                  # ADMIN_EMAIL and ADMIN_PASSWORD
API=localhost:8080/api
JSON='Content-Type: application/json'

# 1. Admin logs in
ADMIN=$(jq -n --arg email "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" '{email: $email, password: $password}' |
  curl -s -X POST $API/auth/login -H "$JSON" -d @- | jq -r .accessToken)

# 2. Admin creates a cinema, an auditorium (5 rows x 8 seats), a movie and a showtime
CINEMA=$(curl -s -X POST $API/cinemas -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d '{"name":"Central Cinema","address":"1 Example Street"}' | jq -r .id)

AUDITORIUM=$(curl -s -X POST $API/cinemas/$CINEMA/auditoriums -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d '{"name":"Screen 1","rows":5,"seatsPerRow":8,"vipRows":["D"],"coupleRows":["E"]}' | jq -r .id)

MOVIE=$(curl -s -X POST $API/movies -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d '{"title":"The Example","description":"A story about cinema.","durationMinutes":120,"genre":"Drama",
       "ageRating":"T13","releaseDate":"2026-05-01","posterUrl":"https://example.com/poster.jpg"}' | jq -r .id)

# startsAt must be in the future
SHOWTIME=$(curl -s -X POST $API/showtimes -H "$JSON" -H "Authorization: Bearer $ADMIN" \
  -d "{\"movieId\":\"$MOVIE\",\"auditoriumId\":\"$AUDITORIUM\",\"startsAt\":\"2030-01-01T19:30:00+07:00\",\"basePrice\":100000}" |
  jq -r .id)

# 3. A customer registers and logs in (on a second run, registering returns 409 and the rest still works)
curl -s -X POST $API/auth/register -H "$JSON" \
  -d '{"email":"bob@example.com","password":"password123","fullName":"Bob"}' | jq -c .
CUSTOMER=$(curl -s -X POST $API/auth/login -H "$JSON" \
  -d '{"email":"bob@example.com","password":"password123"}' | jq -r .accessToken)

# 4-5. The customer picks two available seats and holds them for 10 minutes
SEATS=$(curl -s $API/showtimes/$SHOWTIME/seats | jq -c '[.[] | select(.status == "AVAILABLE") | .id][:2]')
BOOKING=$(curl -s -X POST $API/bookings -H "$JSON" -H "Authorization: Bearer $CUSTOMER" \
  -d "{\"showtimeId\":\"$SHOWTIME\",\"seatIds\":$SEATS}" | jq -r .id)

# 6. The customer pays; the booking becomes CONFIRMED and the seats SOLD
curl -s -X POST $API/bookings/$BOOKING/payment -H "Authorization: Bearer $CUSTOMER" | jq '{status, totalPrice}'
curl -s $API/showtimes/$SHOWTIME/seats | jq -c '.[] | select(.status != "AVAILABLE") | {label, status}'
```

The payment prints `"status": "CONFIRMED"` and a `totalPrice` of `200000`; the last command prints `A1` and `A2` as `SOLD`. If a variable comes back `null`, rerun that `curl` without `| jq` to see the `problem+json` error.
