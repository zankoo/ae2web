# Player Login + Per-Terminal Inventory Grouping Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Add per-player authentication (long-lived token) and per-player/per-terminal inventory filter + tag-based grouping (collapsible sections) stored under `config/ae2web/`.

**Architecture:** The server issues a unique token per player UUID and authenticates HTTP requests via `Authorization: Bearer <token>` or `?token=<token>`. Player settings are stored on disk as JSON keyed by UUID and contain per-`terminalId` inventory rules (whitelist/blacklist tag regex + ordered category rules + collapsed state). The web UI requires a token to show the page, loads/saves the active terminal’s settings, and renders inventory as grouped sections (with a dedicated Fluids section in “All” view).

**Tech Stack:** Minecraft Forge 1.20.1 + Netty HTTP + Gson (server), SvelteKit SPA + TypeScript + Vitest (webui).

---

## Data Model

### Server-side files

- Player profile dir: `config/ae2web/players/`
- One file per player: `config/ae2web/players/<uuid>.json`

Example shape:

```json
{
  "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "token": "base64url...",
  "terminals": {
    "terminal-1": {
      "whitelist": ["^forge:ores/.*$"],
      "blacklist": ["^forge:ores/uranium.*$"],
      "categories": [
        { "name": "Ores", "tagRegex": "^forge:ores/.*$" },
        { "name": "Dusts", "tagRegex": "^forge:dusts/.*$" }
      ],
      "collapsed": {
        "Ores|^forge:ores/.*$": false,
        "Dusts|^forge:dusts/.*$": true,
        "Other": false,
        "Fluids": false
      }
    }
  }
}
```

### Frontend config object (per terminalId)

```ts
type InventoryViewConfig = {
  whitelist: string[];
  blacklist: string[];
  categories: { name: string; tagRegex: string }[];
  collapsed: Record<string, boolean>;
};
```

**Category assignment rule:** scheme 1 (first match wins), in the order listed in `categories`.

---

## Task 1: Add player token service (server)

**Files:**
- Create: `src/main/java/com/kokoda/ae2web/web/auth/PlayerAuthService.java`
- Test: `src/test/java/com/kokoda/ae2web/web/auth/PlayerAuthServiceTest.java`

**Step 1: Write failing test**
- Ensure `getOrCreateToken(uuid)` is stable across calls
- Ensure `authenticate(token)` returns the correct UUID

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*PlayerAuthServiceTest'`
Expected: FAIL (class not found / methods missing)

**Step 3: Minimal implementation**
- Store under `config/ae2web/players/<uuid>.json`
- On init, scan directory and build token->uuid index
- Generate tokens using `SecureRandom` + `Base64.getUrlEncoder().withoutPadding()`

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*PlayerAuthServiceTest'`
Expected: PASS

---

## Task 2: Wire auth into HTTP router (server)

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java`
- Modify/Create: `src/main/java/com/kokoda/ae2web/web/handlers/HandlerUtils.java` (helper to read authenticated UUID from channel attr)

**Behavior:**
- For `/api/*`: extract token from `Authorization` or `?token=`
- If token valid: attach UUID to channel attribute (e.g. `ae2web.userUuid`)
- If auth enabled and token missing/invalid: 401
- If auth disabled: allow requests but UUID may be null (UI will still require token)

---

## Task 3: Add `/api/user/inventoryConfig` (server)

**Files:**
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/UserConfigHandler.java`
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/UserConfigHandlerTest.java` (unit test config read/write service methods; avoid full Netty pipeline)

**Endpoints:**
- `GET /api/user/inventoryConfig?terminalId=<id>` → config or defaults
- `POST /api/user/inventoryConfig?terminalId=<id>` body `{ whitelist, blacklist, categories, collapsed }`

**Auth requirement:**
- Must have authenticated UUID (per-player token). Return 401 otherwise.

---

## Task 4: Add item tag payloads (server)

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java` (add `tags: List<String>`)
- Modify: `src/main/java/com/kokoda/ae2web/web/dto/InventoryItemDTO.java` (add `tags: List<String>`; only set for items)
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java` (include tags on item DTOs)
- Modify: `src/main/java/com/kokoda/ae2web/web/WebServer.java` (include tags on SSE added/snapshot DTOs)
- Test: `src/test/java/com/kokoda/ae2web/web/dto/ItemStackDTOTest.java` (assert `Items.OAK_LOG` includes `minecraft:logs`)

---

## Task 5: Web UI token store + login gate

**Files:**
- Modify: `webui/src/lib/stores/session.ts` (writable store; init from URL; persist in localStorage)
- Modify: `webui/src/lib/api/client.ts` (no behavior change; token now mutable)
- Modify: `webui/src/routes/+page.svelte` (login overlay; don’t start polling/SSE until token present)
- Test: `webui/src/lib/stores/session.test.ts` (token persistence)

---

## Task 6: Web UI per-terminal settings + grouped inventory rendering

**Files:**
- Create: `webui/src/lib/api/userConfig.ts`
- Modify: `webui/src/lib/api/types.ts` (add `tags?: string[]` to InventoryItem)
- Modify: `webui/src/routes/+page.svelte`:
  - gear button(s) near terminal selector (and/or in dropdown options)
  - settings modal UI: whitelist/blacklist regex + category rules list (name + regex)
  - rendering: when `filter !== 'fluids'` and categories configured, render sections:
    - In `All`: item categories + `Other` + `Fluids` section
    - In `Items`: item categories + `Other`
    - In `Fluids`: keep current flat grid; ignore rules
  - collapse toggles: +/- in header; persisted via debounced `POST /api/user/inventoryConfig`
- Test: `webui/src/tests/page.test.ts` (new test: grouping headers rendered; collapse hides items; fluids view ignores grouping)

---

## Final verification

- Backend: `./gradlew test`
- Frontend: `mkdir -p webui/.tmp && TMPDIR=webui/.tmp npm -C webui test`
- Frontend types: `TMPDIR=webui/.tmp npm -C webui run check`

