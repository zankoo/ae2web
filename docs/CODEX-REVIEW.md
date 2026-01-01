# Codex Code Review: Icon Rendering Issues

Generated: 2026-01-01
Session: 019b76cd-fd97-78d0-a695-0de5350a7650
Tokens used: 127,096

## 1) Root Cause Validation

### Infinite retry loop: mostly correct, but incomplete

✅ **Confirmed**: "Render fails → nothing uploaded → stays missing" is real
- `RenderScheduler` returns `null` on not-found and on exceptions (`src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:203-218`)
- Batch uploader is a no-op on `null` (`src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:122-128`)

✅ **Confirmed**: "No retry cap" is real
- `scheduleRetryCheck()` re-triggers forever while `/missing` is non-empty (`src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:227-310`)

⚠️ **Missing factor**: **"Rendered/Missing" stats can lie**
- `renderedCount` is `existenceCache.size()` (includes NBT variants)
- `totalItems` is registry items+fluids only
- `renderedCount` can exceed `totalItems` and `missingCount` clamps to 0
- Location: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:421-452`
- Matches log pattern in `docs/ISSUE-infinite-retry.md:11-33`

⚠️ **Missing factor**: **Upload failures are treated like success**
- `uploadIcon()` returns `CompletableFuture<Boolean>` but the caller ignores it
- Any HTTP/network failure leaves the item missing and triggers retries
- Locations:
  - `src/main/java/com/kokoda/ae2web/client/network/PrerenderHttpClient.java:205-242`
  - `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:122-128`

⚠️ **Missing factor**: **Server "locked by self" still returns the ID as claimed**
- A single bad key can be re-claimed and re-attempted indefinitely
- Location: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:501-505`

### Missing icons in frontend: cache-key mismatch is correct; translation analysis misses a key bug

✅ **Confirmed**: Cache-key mismatch is real
- Image URL uses `item.iconKey || item.id` but cache uses `item.id`
- Locations:
  - `src/main/resources/web/app.js:220-226`
  - `src/main/resources/web/app.js:312-317` (lazy-loader stores `dataset.iconId` into `loadedIcons`)

⚠️ **Inaccuracy in doc**: "no NBT → `@0`" example is not generally true
- DTO icon keys use `Math.abs(key.hashCode())`
- Locations:
  - `src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java:23-25`
  - `docs/ISSUE-missing-icons-frontend.md:55-69`

❌ **Incomplete**: Translation root-cause analysis
- **Real issue**: Translations are saved under whatever `itemId` was uploaded
- Inventory lookup uses **base** `itemId` without `@hash`
- For NBT items, uploads use `iconKey`
- **Result**: Translations can exist but never be found
- Locations:
  - Save: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:253-257`
  - Lookup: `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java:173-182`
  - Upload: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:122-128`

---

## 2) Solution Ranking

### By priority (highest impact first)

1. **Fix frontend cache key** to use `iconKey` end-to-end
   - Files: `src/main/resources/web/app.js:220-226`, `app.js:312-317`
   - Impact: Fixes "icons invisible despite rendered" for NBT variants immediately

2. **Add retry ceiling + per-iconKey failure tracking** (client-side)
   - File: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:227-310`
   - Impact: Stops infinite loops/log spam and protects performance

3. **Fix translation keying mismatch**
   - Store/lookup consistently by base `itemId`, or lookup by `iconKey`
   - Files:
     - `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:253-257`
     - `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java:173-182`
   - Impact: Restores names for NBT variants

4. **Improve render-failure visibility**
   - debug→warn + include `iconKey`
   - File: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:211-212`
   - Impact: Makes real unrenderables diagnosable

5. **Server-side "unrenderable/fail" marker API** (cross-client)
   - Impact: Most robust long-term, but heavier

### By implementation complexity (lowest effort first)

1. **Frontend cache-key fix** (few lines)
   - `src/main/resources/web/app.js:220-226`, `app.js:312-317`

2. **Log level/message improvements**
   - `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:211-212`

3. **Client retry limit + backoff + avoid spawning unbounded threads**
   - `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:227-310`

4. **Translation key consistency**
   - Touches storage + handlers, potential migration of existing `translations.json`
   - Files:
     - `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:44-45`
     - `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:253-257`

5. **Server "fail marker" API + protocol changes**
   - New endpoint + client changes + UI handling

---

## 3) Issue Dependencies

### Relationship: Related but not blocking each other

- Frontend cache-key mismatch can hide already-rendered icons (pure UI issue)
- Infinite retry indicates genuinely missing/unrenderable/failed-upload keys and can keep the system noisy even after UI is fixed
- Translation symptom crosses both: render failures *and* the current translation key mismatch can produce "missing names"

**Recommendation**: Fix **separately but in the same release window** so you can validate backend correctness with a trustworthy UI.

---

## 4) Implementation Sequence (optimal)

1. **Fix `loadedIcons`** to key by `item.iconKey || item.id` consistently
   - Include `data-icon-id` and the `onload` add
   - Files: `src/main/resources/web/app.js:220-226`, `app.js:312-317`

2. **Fix translation storage/lookup consistency** (choose one):
   - Option A: Store translations under base `itemId` (strip `@hash` on save)
     - File: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:253-257`
   - Option B: Lookup by `iconKey` in `InventoryHandler`
     - Would require DTO/server carrying the correct key into lookup

3. **Add retry caps + per-key suppression**
   - Ideally switch `new Thread` to a scheduler
   - File: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:227-310`

4. **Add server-side fail marker API** (optional)
   - Only if you need cross-client suppression and persistent "known bad" keys

---

## 5) Edge Cases / Side Effects

### NBT hash collisions
- `iconKey` is based on `hashCode()`
- Different NBT variants can collide → wrong icon/name served
- Locations:
  - `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java:55-59`
  - `src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java:23-25`

### Math.abs(Integer.MIN_VALUE) stays negative
- Unlikely but can create odd `@-2147483648` keys
- Still allowed by sanitizer: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:352-370`

### 404 then later rendered
- Frontend currently doesn't retry a failed icon unless the DOM is rebuilt
- `updateItemsWithAnimation()` path won't reload icons
- Files:
  - `src/main/resources/web/app.js:203-209`
  - `src/main/resources/web/app.js:304-324`

### Caching staleness
- Successful icon responses are cached for 1 day
- Re-rendered icons may not refresh quickly without cache-busting/ETag
- File: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java:366-368`

### Thread growth risk
- `scheduleRetryCheck()` spawns a new thread each cycle
- If something causes rapid re-entry, this can become a resource issue
- File: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:231-310`
