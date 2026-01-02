# WebUI SvelteKit SPA Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan.

**Goal:** Replace `src/main/resources/web/` vanilla JS UI with a SvelteKit + TypeScript SPA built into `web/` static assets inside the mod JAR, without losing any existing UI features.

**Architecture:** Create a SvelteKit app under `webui/` (static adapter output to `webui/build/`). Gradle runs `npm ci` + `npm run build` and packages `webui/build/**` into classpath `web/**` (served by Netty). The UI remains a single page (no path routing); all view changes are internal component state.

**Tech Stack:** SvelteKit (SPA), TypeScript, Vite proxy for `/api/*`, `@sveltejs/adapter-static`, `lodash-es` (debounce), `pinyin-match` (pinyin search), Vitest + Testing Library for unit tests, Gradle Node plugin for build integration.

---

### Task 1: Scaffold `webui/` SvelteKit project

**Files:**
- Create: `webui/` (SvelteKit project)

**Steps:**
1. Create project: `npx sv create --template minimal --types ts --no-add-ons --install npm webui`
2. Add tooling add-ons: `npx sv add --cwd webui eslint prettier vitest`
3. Install static adapter + deps: `npm -C webui i -D @sveltejs/adapter-static` and runtime deps `lodash-es pinyin-match`
4. Verify dev server starts: `npm -C webui run dev -- --host`

**Expected:** `webui/package.json` exists; `npm -C webui run dev` works.

---

### Task 2: Configure SPA/static output and dev proxy

**Files:**
- Modify: `webui/svelte.config.js` (use `@sveltejs/adapter-static`, output to `build/`)
- Modify: `webui/src/routes/+layout.ts` (set `ssr = false` for SPA)
- Modify: `webui/vite.config.ts` (proxy `/api` → Netty)

**Steps:**
1. Switch adapter to static and ensure build output directory is `build/`.
2. Set SPA mode: `export const ssr = false;` (and `prerender` as needed by adapter-static).
3. Add Vite proxy for `/api` to `http://127.0.0.1:8080`.
4. Verify: `npm -C webui run build` outputs `webui/build/index.html`.

---

### Task 3: Port styling + assets (no CDN)

**Files:**
- Create/Modify: `webui/src/app.css` (global CSS tokens/styles, migrated from `src/main/resources/web/style.css`)
- Create: `webui/static/logo.svg`
- Modify: `webui/src/app.html` (title/meta only; no external scripts/fonts)
- Add deps: local fonts via `@fontsource/*` OR use system fonts

**Steps:**
1. Copy CSS variables + layout rules to `webui/src/app.css` (keep classnames to preserve look).
2. Copy `src/main/resources/web/logo.svg` → `webui/static/logo.svg`.
3. Remove all external CDN usage (Google Fonts, jsdelivr); bundle dependencies instead.
4. Verify UI renders similarly in `npm -C webui run dev`.

---

### Task 4: Implement API client + DTO types + token strategy

**Files:**
- Create: `webui/src/lib/api/client.ts` (Authorization header + `withTokenQuery`)
- Create: `webui/src/lib/stores/session.ts` (token from URL query)
- Create: `webui/src/lib/api/*.ts` (status/terminals/inventory/cpus/craft/prerender)
- Test: `webui/src/lib/api/client.test.ts`, `webui/src/lib/stores/session.test.ts`

**Steps:**
1. Implement `apiJson<T>(path, init)` that injects `Authorization: Bearer <token>` when present.
2. Implement `withTokenQuery(url)` for `<img>` and `EventSource`.
3. Add DTO types aligned with backend JSON.
4. Add unit tests for header injection + token query behavior.

---

### Task 5: Implement Terminal selector + connection status

**Files:**
- Create: `webui/src/lib/stores/terminal.ts`
- Create: `webui/src/lib/components/TerminalSelect/TerminalSelect.svelte`
- Modify: `webui/src/routes/+page.svelte`

**Steps:**
1. Poll `/api/status` every 5s, render ONLINE/OFFLINE.
2. Poll `/api/terminals` every 10s, select first connected by default, preserve selection if still present.
3. On terminal change: refresh inventory + CPUs and restart inventory SSE.

---

### Task 6: Implement Inventory view (search/filter/sort/SSE/icons/tooltip)

**Files:**
- Create: `webui/src/lib/stores/inventory.ts`
- Create: `webui/src/lib/components/Inventory/*`
- Create: `webui/src/lib/utils/*` (formatting, pinyin match, MC tooltip text rendering)
- Test: unit tests for pinyin match + formatting helpers

**Steps:**
1. Fetch `/api/items?type=...&terminalId=...` (full list).
2. Implement search (lodash debounce), filter (items/fluids/all), sort (amount/name).
3. Implement inventory SSE `/api/events/inventory` with debounce reload and version de-dupe.
4. Implement icon component: placeholder + lazy load + retry (bounded) + loaded cache.
5. Implement tooltip: fetch `/api/prerender/tooltip?id=...`, cache results, render MC-style color codes.
6. Keep UI structure and classnames consistent with existing HTML/CSS.

---

### Task 7: Implement CPUs panel + Craft modal + Toasts

**Files:**
- Create: `webui/src/lib/stores/cpus.ts`
- Create: `webui/src/lib/components/Cpus/CpusPanel.svelte`
- Create: `webui/src/lib/components/Craft/CraftModal.svelte`
- Create: `webui/src/lib/components/Toast/ToastHost.svelte`

**Steps:**
1. Poll `/api/cpus?terminalId=...` every 2s; render list, busy/idle, progress bar + counts.
2. Implement craft modal opened by clicking craftable item slot.
3. Submit `/api/craft` POST with `{ id, type, amount, cpuName?, terminalId? }`.
4. Show toast feedback (success/error) and refresh CPUs on success.

---

### Task 8: Gradle build integration (Node build → `web/` resources)

**Files:**
- Modify: `build.gradle` (apply `com.github.node-gradle.node`, add tasks)
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java` (content-type additions if needed)

**Steps:**
1. Configure Node plugin to run in `webui/` with pinned Node version.
2. Create `webuiBuild` Gradle task: `npm ci` + `npm run build`.
3. Package `webui/build/**` into classpath `web/**` during `processResources`.
4. Ensure `./gradlew clean build` produces a JAR that serves the SvelteKit output.

---

### Task 9: Verification

**Commands:**
- Frontend: `npm -C webui test` (or `npm -C webui run test:unit`)
- Full build: `./gradlew clean build`

**Expected:** Build succeeds and UI provides: terminal selection, inventory (search/filter/sort), SSE refresh, tooltip + icons, craft modal, CPUs.

