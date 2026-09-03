# Regenerating the OpenAPI contract

`openapi.json` in this directory is the committed API contract. It is not
hand-written: `OpenApiSpecGeneratorTest` boots the application, fetches
`/api/v3/api-docs` and writes the file with sorted keys, so the same code always
produces byte-identical output and any diff means the API actually changed.

From the repository root (Docker must be running — the test uses
Testcontainers; on Windows use `mvnw.cmd`):

```bash
./mvnw verify -Pintegration -DskipPmd=true \
      -Dfailsafe.includes='**/OpenApiSpecGeneratorTest.java'
```

The JDK must be 21 — the build enforces `[21,22)`.

## The rest of the loop

The frontend keeps its own copy of this file and generates its typed API client
from it, so a contract change surfaces there as a compile error instead of a
runtime surprise. After regenerating here, run the frontend's cycle: it copies
the spec across, regenerates the client, type-checks, and rebuilds the BDD step
definitions.

```bash
cd ../<frontend-repo> && npm run openapi:cycle:fast
```

Commit the results as separate slices — the backend change, the spec diff, then
the regenerated client.
