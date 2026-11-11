# Framework-free business layer

English | [Tiếng Việt](0001-framework-free-business-layer.vi.md)

The assignment requires that the business layer imports no web framework and no DB library. We read that strictly: the business layer depends only on `java.*` and its own code, with no Spring annotations (not even `@Service` or `@Transactional`). Business models are plain records/classes separate from JPA entities; the business layer declares the repository interfaces it needs and the persistence layer implements them with Spring Data JPA. Wiring happens with `@Bean` methods in a `config` package. An ArchUnit test fails the build if the rule is broken.

## Consequences

- Two model sets (business model and JPA entity) and hand-written mappers between them. This is the price of the rule, not accidental duplication; don't "fix" it by annotating business classes.
