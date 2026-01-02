## 【Research Results】

### Core Finding
Use SvelteKit’s Svelte CLI (`npx sv create`) to scaffold a TypeScript app, configure `@sveltejs/adapter-static` for pure static output, and disable SSR via `export const ssr = false` in `src/routes/+layout.ts` to run as a SPA; then wire Gradle’s Node plugin (`com.github.node-gradle.node`) to run `npm ci` + `npm run build` and package `webui/build/**` into the mod’s classpath `web/**`.

### Evidence from Real Code / Official Docs
- **Svelte CLI**: `npx sv create [options] [path]` supports `--template minimal`, `--types ts`, `--add eslint prettier`, and `--install npm`. (Svelte CLI docs: `sv create`)
- **SPA mode**: disable SSR in `src/routes/+layout.js` via `export const ssr = false;`. (SvelteKit docs: Single-page apps)
- **Static output**: configure `@sveltejs/adapter-static` in `svelte.config.js`, default `pages/assets` output is `build/`. (SvelteKit docs: adapter-static)
- **List reorder animation**: Svelte supports `animate:flip` for keyed `{#each}` blocks, which is a built-in FLIP animation best suited to “inventory sort reorder” UI. (Svelte docs: `animate` / `flip`)
- **Gradle Node plugin**: apply `id "com.github.node-gradle.node"` and configure npm install command (can be `ci`) and `nodeProjectDir` for subdirectory frontends. (node-gradle/gradle-node-plugin docs)

### Recommended Approach (Applied To AE2Web)
1. Scaffold `webui/` using `npx sv create` (minimal + TypeScript) and add `eslint`, `prettier`, `vitest`.
2. Switch to `@sveltejs/adapter-static` and set SPA mode with `export const ssr = false;` (and `prerender`/`fallback` only if later adding path routing).
3. Use Vite dev proxy for `/api/*` to the Netty server port (e.g. 8080) for local hot-reload dev.
4. Implement token strategy once (store reads `?token=`), then:
   - fetch: `Authorization: Bearer <token>`
   - SSE/images: append `&token=<token>` query
5. Use Svelte keyed rendering + `animate:flip` for stable DOM and smooth reorder on amount/name sort changes.
6. Integrate Gradle build so `./gradlew clean build` always rebuilds the web UI and packages it into `web/` resources.

### Watch Out For
- **CDN dependencies**: prior UI loaded Google Fonts and pinyin-match from CDN; SPA build must bundle these to keep offline/packaged behavior in the mod JAR.
- **Static server content-types**: ensure Netty content-type mapping includes any new file extensions emitted by Vite/SvelteKit (e.g. `.map` if sourcemaps, `.webmanifest` if added).
- **SPA fallback**: if you later introduce path routing, you’ll need adapter-static `fallback: 'index.html'` and server history fallback; current “single page, no path routing” avoids this.

