# PostgreSQL over SQLite

English | [Tiếng Việt](0002-postgresql-over-sqlite.vi.md)

We use PostgreSQL, managed with Flyway migrations. SQLite was considered for its zero setup but rejected: it serialises all writes behind one database-wide lock, so a load test of concurrent bookings would measure SQLite's lock rather than our architecture, and its file-based nature rules out the obvious phase 2 moves (multiple app instances, read replicas, connection pooling). Hibernate also only supports it through a community dialect.
