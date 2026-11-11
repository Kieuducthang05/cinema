# Load test on Kaggle without Docker

English | [Tiếng Việt](0003-load-test-on-kaggle-without-docker.vi.md)

The baseline load test runs on a Kaggle CPU notebook so phase 2 can repeat the workflow on the same platform. Kaggle notebooks cannot run Docker (no daemon), so on Kaggle the service runs as a plain JAR against a PostgreSQL installed with `apt`, and the load generator (k6) runs in the same notebook against `localhost`. Docker images are for local development and grading only. Compare each run against its recorded `results/env.txt` rather than assuming identical hardware.

The service and k6 share the notebook, so `taskset` keeps them apart: k6, the notebook kernel and its telemetry sampler run on the first CPU, the service and PostgreSQL on the rest (three of Kaggle's four). This costs the system one core, but a step that breaks can then be told apart from k6 starving the service, and k6's own CPU use is recorded to show when k6 is the limit.
