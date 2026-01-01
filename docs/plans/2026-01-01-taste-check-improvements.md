# Taste-Check Improvements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Apply repo-wide “taste-check” improvements: fix interrupt handling in retries, remove string-based DOM building hot spots, split the web UI into smaller files, and split oversized server handlers while keeping behavior the same.

**Architecture:** Prefer simpler data flow and fewer special cases: generic static file serving (so web assets aren’t hardcoded), “fail fast” on interrupts, and smaller handler/JS modules with single responsibilities.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1, Netty HTTP, vanilla browser JS/CSS.

---

### Task 1: Serve all `web/` static assets generically (remove special cases)

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java`

**Step 1: Implement generic static routing**
- For any request not starting with `/api/`, map `/` to `index.html`, otherwise map `/<path>` → `web/<path>`.
- Reject `..` and backslashes in the mapped path.

**Step 2: Implement binary-safe serving**
- Read resources as `byte[]` (not UTF-8 `String`) and use `HandlerUtils.sendBinaryResponse(...)`.
- Set content-type by extension (`.html/.css/.js/.svg/.png/.woff2/.json`, fallback `application/octet-stream`).

**Step 3: Verify**
- Run: `./gradlew build --offline`

---

### Task 2: Fix retry backoff interrupt handling (don’t swallow `InterruptedException`)

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/client/network/PrerenderHttpClient.java`

**Step 1: Replace `catch (InterruptedException ignored)`**
- On interrupt: `Thread.currentThread().interrupt()` and return early (fail fast).

**Step 2: Verify**
- Run: `./gradlew build --offline`

---

### Task 3: Remove `innerHTML +=` hot loop (CPU select/options)

**Files:**
- Modify: `src/main/resources/web/app.js`

**Step 1: Replace incremental HTML string building**
- Build `<option>` elements via DOM (`document.createElement('option')`, `appendChild`) instead of `innerHTML +=`.

**Step 2: Verify**
- Run: `./gradlew build --offline`

---

### Task 4: Split `app.js` into smaller files (modules)

**Files:**
- Modify: `src/main/resources/web/index.html`
- Create: `src/main/resources/web/js/app.js`
- Create: `src/main/resources/web/js/api.js`
- Create: `src/main/resources/web/js/inventory.js`
- Create: `src/main/resources/web/js/cpus.js`
- Create: `src/main/resources/web/js/craft.js`
- (Optionally) delete or shrink: `src/main/resources/web/app.js`

**Step 1: Move code into modules**
- Keep behavior identical; export a single `init()` function from `js/app.js`.

**Step 2: Update index.html script tags**
- Load `js/app.js` with `type="module"` and remove the old single-file include.

**Step 3: Verify**
- Run: `./gradlew build --offline`

---

### Task 5: Split oversized handlers (Prerender/Crafting) into smaller classes

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java`
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/CraftingHandler.java`
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderMissingHandler.java`
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderClaimHandler.java`
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderUploadHandler.java`
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderImageHandler.java`
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/crafting/CpuHandler.java`
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/crafting/CraftRequestHandler.java`

**Step 1: Move endpoint-specific code into dedicated handlers**
- Keep route signatures stable and just delegate.
- Aim: each new handler file < ~200 lines.

**Step 2: Verify**
- Run: `./gradlew build --offline`

---

### Task 6: Update `docs/TODO.md` and re-run build

**Files:**
- Modify: `docs/TODO.md`

**Step 1: Record completed “taste-check” items**
- Add completed entries for: static serving, interrupt handling, app.js split, handler splits.

**Step 2: Verify**
- Run: `./gradlew build --offline`

