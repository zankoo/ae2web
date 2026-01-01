# Fix Minor Issues Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all items under `docs/DEEP-LOGIC-REVIEW.md` “Minor Issues” (#2, #6, #7) and verify with a local Gradle build.

**Architecture:** Keep behavior unchanged except for targeted correctness/resource-lifecycle fixes. Avoid backward-compatibility work (project not released) and keep changes small.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1, Netty, vanilla web UI assets.

---

### Task 1: Support `/` in `ResourceLocation` icon paths (Minor #2)

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java`

**Step 1: Verify current behavior**
- Confirm icon storage paths can contain nested directories (e.g. `namespace:path/with/slash@h1_...`).

**Step 2: Implement safe path handling**
- Allow `/` in the `path` component (reject `..` and `\`).
- Ensure on-disk scan uses recursive traversal and produces `namespace:relative/path@hash` keys.

**Step 3: Verification**
- Run: `./gradlew build --offline`

---

### Task 2: Await Netty shutdown termination (Minor #6)

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/web/WebServer.java`

**Step 1: Implement shutdown waiting**
- Close the server channel and wait for close completion.
- `shutdownGracefully(...)` for boss/worker groups and await termination (with timeouts).
- Avoid deadlock if `stop()` is invoked on a Netty event loop thread (offload waiting to a dedicated thread).

**Step 2: Verification**
- Run: `./gradlew build --offline`

---

### Task 3: Preserve terminal identity/name on wrench pickup (Minor #7)

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/block/WebTerminalBlock.java`

**Step 1: Implement NBT preservation**
- When shift+wrench pickup triggers, create the dropped `ItemStack` with `BlockEntityTag` containing:
  - `terminalId`
  - `customName`
- Keep current player-inventory/drop behavior, then remove the block.

**Step 2: Verification**
- Run: `./gradlew build --offline`

---

### Task 4: Update review doc to reflect actual code (Minor Issues section)

**Files:**
- Modify: `docs/DEEP-LOGIC-REVIEW.md`

**Step 1: Update “Implemented” notes**
- Mark Minor #2/#6/#7 as implemented with a short “what changed” note and file pointers.

**Step 2: Verification**
- Run: `./gradlew build --offline`

