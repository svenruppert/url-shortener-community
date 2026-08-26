# URL-Shortener Community Edition

Open-core community edition of the URL shortener: core domain, REST server,
Java client SDK, and the Vaadin admin UI.

| Module | Artifact | Content |
|---|---|---|
| `urlshortener-core` | `urlshortener-core` | domain, DTOs, validation, JSON |
| `urlshortener-server` | `urlshortener-server` | REST admin API + redirect server (JDK `HttpServer`, jSentinel security, EclipseStore persistence) |
| `urlshortener-client` | `urlshortener-client` | Java SDK for the REST API |
| `urlshortener-ui` | `urlshortener-ui` (war + `classes` jar) | Vaadin admin UI |

## Build & test

```bash
./mvnw clean verify          # full reactor incl. the no-mocks E2E/security suites
```

The UI production bundle is built with `-Pvaadin-production` (release
artifacts only; the dev build suffices locally). Tests write all persistent
state below `target/` — `mvn clean` resets everything.

## Open-core plug-in seams (V00.18.00)

Enterprise features plug in via `ServiceLoader` — community sources are never
changed for a new commercial feature:

- **UI SPI** `com.svenruppert.urlshortener.ui.vaadin.extension`
  (`FeatureContribution` → routes, drawer entries, navbar components, link
  create-form sections and details-dialog sections; contributed routes are
  registered dynamically under `MainLayout`, community views keep their
  static `@Route`).
- **Server SPI** `com.svenruppert.urlshortener.api.extension`
  (`HandlerContribution` + `ServerContext`; contributed endpoints run behind
  the standard security pipeline, duplicate paths fail fast at startup).
  Since P085 the context also exposes `LinkMetaStorage` (raw persistent maps
  for the link-meta plug-in family) and `LinkLifecycleContribution` hooks
  (create-time metadata from the raw request JSON, delete cascades, list
  filters).
- A **boundary test** in server and ui fails the build if community sources
  ever reference `urlshortener.enterprise`.

The war additionally attaches a `urlshortener-ui-<version>-classes.jar` (classifier
`classes`) so plug-ins can compile against the UI SPI with scope `provided`.

## Release chain (concept §8)

1. Every deploy goes to the **Gitea Maven registry** first
   (`https://git.jsentinel.eu/api/packages/sven.ruppert/maven`, server id
   `gitea` in your `settings.xml`):
   ```bash
   ./mvnw deploy -DskipTests -DaltDeploymentRepository=gitea::https://git.jsentinel.eu/api/packages/sven.ruppert/maven
   ```
2. On release close, the community artifacts additionally go to
   **Maven Central** (namespace onboarding is a separate operational step).
3. Enterprise artifacts never touch Central — see the enterprise repository.

## License

The Community Edition is licensed under the **European Union Public Licence
v1.2 (EUPL-1.2)**.

**Dual licensing:** jSentinel Ltd additionally offers this code base and the
Enterprise Edition (plug-in modules such as the MCP server and the CLI) under
**commercial license terms**. Contact: jSentinel Ltd — https://jsentinel.eu

Contributions are accepted under the DCO plus an additional license grant —
see [CONTRIBUTING.md](CONTRIBUTING.md).
