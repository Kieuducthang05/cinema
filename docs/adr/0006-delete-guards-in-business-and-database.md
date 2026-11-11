# Delete guards in both the business layer and the database

English | [Tiếng Việt](0006-delete-guards-in-business-and-database.vi.md)

"A Cinema, Auditorium or Movie with dependants cannot be deleted" is checked twice: the service asks its repository first (`existsByCinemaId`, `existsByAuditoriumId`, `existsByMovieId`), and the adapter also translates the foreign key violation into the same `409`. This is deliberate, not duplication to remove. The rule is a business rule from the spec, so the framework-free business layer ([ADR 0001](0001-framework-free-business-layer.md)) has to state it where a reader of the business layer finds it. The foreign key only covers the race where a dependant is created between the check and the delete.

## Consequences

- Each of these rules has two copies to keep in step. The overlap rule follows the same pattern, and `ShowtimePersistenceTest` tests its adapter copy with the service bypassed.
- An architecture review that finds the pre-checks redundant with the constraints should not propose deleting them.
