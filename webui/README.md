# AE2Web Web UI (SvelteKit SPA)

This folder (`webui/`) contains the source for the in-mod Web UI.
The Minecraft mod packages the built static site into the JAR under classpath `web/` and serves it from the embedded Netty HTTP server.

## Key Paths

- Source code: `webui/src/`
- Build output (static site): `webui/build/`
- Packaged location in JAR: `web/**`

## Dev Workflow

1. Start the mod (Netty backend):

```bash
./gradlew runClient
```

2. Start Vite dev server (with `/api` proxy to the mod backend):

```bash
npm -C webui ci
npm -C webui run dev
```

If your backend is not on `127.0.0.1:8080`, update the proxy target in `webui/vite.config.ts`.

## Build / Package

- Build only the Web UI:
  - `./gradlew webuiBuild` (uses Gradle-pinned Node + `npm ci` + `npm run build`)
- Full mod build (includes Web UI build + packaging):
  - `./gradlew clean build`

## Tests

- Run unit tests:
  - `npm -C webui test`
- Tests are colocated next to the code under `webui/src/lib/**/*.test.ts`
- Test setup lives in `webui/src/tests/setup.ts`
