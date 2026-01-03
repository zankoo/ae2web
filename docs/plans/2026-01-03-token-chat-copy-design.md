# Token Chat Copy + Auto Clipboard Design

**Goal:** When running `/ae2web token`, the player can *copy* the token and personal URL from chat (because Minecraft chat cannot select text), and the token is also copied to the OS clipboard automatically for fast pasting into the browser.

## UX

- `/ae2web token` prints:
  - Token (click to copy)
  - Personal URL (click to copy)
  - Short “Keep this token secret” warning
- Token is automatically copied to clipboard on command execution (best-effort).
- Web Terminal block “status” messages show URLs/token as clickable copy too, but do **not** auto-copy (avoid surprising clipboard changes on a simple right-click).

## Implementation

### Click-to-copy (works even without client-side mod code)

- Use vanilla chat click events:
  - `ClickEvent.Action.COPY_TO_CLIPBOARD`
  - Add a hover tooltip like “Click to copy”.

This works in standard Minecraft chat UX and avoids any need for client code to copy on click.

### Auto-copy (requires client-side mod; safe on dedicated server)

- Add a server→client packet `CopyToClipboardPacket` containing the text to copy (the token).
- On the **client**, handle the packet by calling `Minecraft.getInstance().keyboardHandler.setClipboard(text)` on the main thread.
- Ensure no dedicated-server classloading issues:
  - Packet class stays in common code but delegates clipboard setting through a `DistExecutor` to a **client-only** helper class that imports `net.minecraft.client.*`.

## Testing

- Unit test for a small chat component helper (builds copyable token/url `Component`):
  - Asserts `ClickEvent.Action.COPY_TO_CLIPBOARD` is present and value matches.
- Unit test for packet encode/decode:
  - Round-trip via `FriendlyByteBuf` preserves copied text.

