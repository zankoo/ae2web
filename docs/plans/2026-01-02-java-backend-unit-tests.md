# Java Backend Unit Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Add comprehensive JUnit-based unit tests for the Java backend (web server + handlers + prerender + crafting parsing) and keep `./gradlew test` green after each task.

**Architecture:** Prefer unit tests over integration tests by extracting pure logic into small utility classes where needed, then test those with JUnit + AssertJ. For Netty handlers and SSE output, use Netty `EmbeddedChannel` and Mockito to validate responses without binding sockets.

**Tech Stack:** JUnit 5, Mockito, AssertJ (optionally Awaitility/WireMock/Testcontainers when needed).

---

### Task 1: Establish test dependencies and conventions

**Files:**
- Modify: `build.gradle`
- Modify: `AGENTS.md`

**Step 1: Add dependencies**
- Add `testImplementation` for:
  - JUnit Jupiter (via BOM)
  - Mockito
  - AssertJ
- Enable JUnit Platform: `tasks.named('test') { useJUnitPlatform() }`

**Step 2: Define conventions**
- Ensure `AGENTS.md` documents required frameworks:
  - JUnit + Mockito required
  - Prefer AssertJ
  - Awaitility for async tests (avoid `Thread.sleep()`)
  - WireMock/MockWebServer for HTTP client tests
  - Testcontainers for real deps
- Require `./gradlew test` after each task, `./gradlew clean build` before commit.

**Step 3: Verify**
- Run: `./gradlew test`
- Expected: PASS

---

### Task 2: Test shared handler utilities

**Files:**
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/HandlerUtilsTest.java`

**Step 1: Write failing tests**
- `getFirstParam()` null/empty handling
- `parseIntParam()` default, blank, out-of-range, invalid number
- `errorJson()` output includes `"error"`

**Step 2: Run tests (RED)**
- Run: `./gradlew test --tests '*HandlerUtilsTest'`
- Expected: FAIL (until tests exist)

**Step 3: Implement minimal code (GREEN)**
- Prefer not to change production unless testability requires it.

**Step 4: Verify**
- Run: `./gradlew test --tests '*HandlerUtilsTest'`
- Expected: PASS

---

### Task 3: Test WebServer subscriber bookkeeping + SSE payloads

**Files:**
- Test: `src/test/java/com/kokoda/ae2web/web/WebServerTest.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/SseHandlerTest.java`

**Step 1: Write failing tests**
- `subscribeInventory()/unsubscribeInventory()` add/remove behavior
- `notifyInventoryChanged()` writes `event: inventory` only to subscribed channels
- `unregisterTerminal()` closes subscriber channels

**Step 2: Test implementation approach**
- Use Netty `EmbeddedChannel`
- Set `SseHandler.SUBSCRIBED_TERMINAL` attribute
- Call `WebServer.getInstance()` methods; call `channel.runPendingTasks()` to execute event loop tasks

**Step 3: Verify**
- Run: `./gradlew test --tests '*WebServerTest' --tests '*SseHandlerTest'`
- Expected: PASS

---

### Task 4: Test prerender endpoints (validation + happy path where possible)

**Files:**
- Modify (testability): `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java`
- Test: `src/test/java/com/kokoda/ae2web/web/prerender/PrerenderStorageServiceTest.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderClaimHandlerTest.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderUploadHandlerTest.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderImageHandlerTest.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/prerender/PrerenderTooltipHandlerTest.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/PrerenderHandlerTest.java` (for `/api/prerender/check`)

**Step 1: Write failing tests**
- Storage: `saveIcon()` rejects invalid PNG, accepts valid minimal PNG, stores translation/tooltip, `hasIcon/getIcon`
- Claim: invalid body/JSON/keys → 400; valid claim → 200 with `claimedCount`
- Upload: invalid base64/id → 400; valid upload → 200 and icon retrievable
- Image/Tooltip: missing/invalid id → 400; missing icon → 404; stored icon/tooltip → 200
- Check: invalid iconKey format → 400; existing/missing split is correct

**Step 2: Test isolation**
- Ensure prerender storage uses a test-only root under `build/` to avoid polluting workspace files.
- Call `PrerenderStorageService.getInstance().clearAll()` in `@BeforeEach`.

**Step 3: Async testing**
- Use Awaitility when waiting for handler async responses (do not use `Thread.sleep()`).

**Step 4: Verify**
- Run: `./gradlew test --tests '*Prerender*Test'`
- Expected: PASS

---

### Task 5: Test crafting request parsing and error mapping

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/crafting/CraftRequestHandler.java` (extract pure parsing)
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/crafting/CraftRequestParser.java`
- Test: `src/test/java/com/kokoda/ae2web/web/handlers/crafting/CraftRequestParserTest.java`

**Step 1: Write failing tests**
- Valid JSON parses: `type`, `id`, default `amount=1`
- Invalid cases: blank body, invalid JSON, missing fields, invalid type, invalid amount, amount > max
- Optional fields: `cpuName`/`terminalId` blank → null

**Step 2: Implement minimal extraction**
- Move `parseCraftRequest` logic into `CraftRequestParser.parse(String)` returning a small record DTO.
- Keep handler behavior identical, just delegate.

**Step 3: Verify**
- Run: `./gradlew test --tests '*CraftRequestParserTest'`
- Expected: PASS

---

### Task 6: Full verification

**Step 1: Run full build**
- Run: `./gradlew clean build`
- Expected: BUILD SUCCESSFUL

**Step 2: Coverage sanity**
- Run: `./gradlew test` and inspect reports under `build/reports/tests/test/`

