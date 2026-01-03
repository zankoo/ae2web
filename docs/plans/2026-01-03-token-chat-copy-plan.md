# Token Chat Copy + Auto Clipboard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Make `/ae2web token` and Web Terminal status messages support click-to-copy for token/URL, and auto-copy the token to clipboard on command execution.

**Architecture:** Add a reusable chat component helper (copy-to-clipboard click events + hover), add a server→client `CopyToClipboardPacket`, and wire it into `/ae2web token`.

**Tech Stack:** Forge 1.20.1 networking (SimpleChannel), vanilla chat components (`ClickEvent`), client clipboard via `KeyboardHandler#setClipboard`.

---

## Task 1: Add chat copy helper

**Files:**
- Create: `src/main/java/com/kokoda/ae2web/util/ChatComponentUtils.java`
- Test: `src/test/java/com/kokoda/ae2web/util/ChatComponentUtilsTest.java`

**Step 1: Write failing test**
- `copyToClipboardText("abc")` sets click action to `COPY_TO_CLIPBOARD` with value `"abc"`.

**Step 2: Run test (expect fail)**
- `./gradlew test --tests '*ChatComponentUtilsTest'`

**Step 3: Minimal implementation**
- Build `Component` with click + hover style.

**Step 4: Run test (expect pass)**

---

## Task 2: Add server→client clipboard packet

**Files:**
- Create: `src/main/java/com/kokoda/ae2web/network/CopyToClipboardPacket.java`
- Create: `src/main/java/com/kokoda/ae2web/client/ClientClipboard.java`
- Modify: `src/main/java/com/kokoda/ae2web/network/PacketHandler.java`
- Test: `src/test/java/com/kokoda/ae2web/network/CopyToClipboardPacketTest.java`

**Step 1: Write failing test**
- Encode+decode round-trip preserves text.

**Step 2: Run test (expect fail)**
- `./gradlew test --tests '*CopyToClipboardPacketTest'`

**Step 3: Minimal implementation**
- Register packet in `PacketHandler.init()`.
- Add helper `PacketHandler.sendToPlayer(ServerPlayer, Object)`.
- Client handler uses `KeyboardHandler#setClipboard` via `DistExecutor` + client-only helper.

**Step 4: Run test (expect pass)**

---

## Task 3: Wire into `/ae2web token` and block status

**Files:**
- Modify: `src/main/java/com/kokoda/ae2web/server/command/TokenCommand.java`
- Modify: `src/main/java/com/kokoda/ae2web/block/WebTerminalBlock.java`

**Behavior:**
- Token and Personal URL components are clickable copy.
- `/ae2web token` also sends `CopyToClipboardPacket(token)` to the player.
- Block right-click status shows clickable copy but does not auto-copy.

---

## Final verification

- `./gradlew test`
- (Optional) `./gradlew build` to confirm jar builds.

