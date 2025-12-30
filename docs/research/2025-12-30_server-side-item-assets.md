# Server-Side Minecraft Item Assets Solution

**Date**: 2025-12-30
**Topic**: How to serve item icons and translations on dedicated servers without client assets

## Problem Statement

Dedicated Minecraft servers don't have client-side assets (textures, lang files). The AE2 Web Terminal mod needs to display:
1. Minecraft vanilla item icons and translations
2. GTCEu material item icons (dynamically generated) and translations

## Research Findings

### 1. External CDN Solutions for Minecraft Assets

**PrismarineJS/minecraft-assets** (npm package)
- URL: https://github.com/PrismarineJS/minecraft-assets
- CDN: https://unpkg.com/minecraft-assets@1.15.0/minecraft-assets/data/1.20.2/
- Structure:
  - `items/` - Item texture PNGs
  - `items_textures.json` - Item texture mappings
  - `blocks/` - Block textures
- Supports MC versions: 1.8.8 through 1.21.x

**mcasset.cloud**
- URL: https://mcasset.cloud/
- GitHub: https://github.com/InventivetalentDev/minecraft-assets
- Provides browsable Minecraft assets for all versions
- Can access textures via: `https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/{version}/assets/minecraft/textures/item/{item}.png`

### 2. GTCEu Material Texture Generation

GTCEu materials use a **color overlay system**:
1. Base texture template (e.g., `dust.png`, `ingot.png`) - grayscale mask
2. Material color RGB value from `material.getMaterialRGB()`
3. Runtime composition: base texture * material color

**Key APIs**:
```java
Material gold = material('gold');
int color = gold.getMaterialRGB(); // 0xFFE650
```

Translation keys use special format: `tagprefix.dust`, `material.gtceu.xxx`

### 3. Solution Architecture

#### Option A: Frontend CDN Fallback (Recommended)
- Server returns item ID and metadata (namespace, path, color info if applicable)
- Frontend JavaScript fetches icons from external CDN when server returns no icon
- CDN URL pattern: `https://unpkg.com/minecraft-assets@1.15.0/minecraft-assets/data/1.20.2/items/{item}.png`

**Pros**: No server changes, works immediately, no copyright issues
**Cons**: Requires internet access from browser, slight latency

#### Option B: Server Proxy/Cache
- Server fetches and caches icons from external CDN
- Serves cached icons to clients

**Pros**: Works offline after caching
**Cons**: More complex, potential rate limiting

#### Option C: Bundle Essential Assets
- Include commonly used Minecraft translations in mod JAR
- Use fallback placeholder icons

**Pros**: Works completely offline
**Cons**: JAR size increase, potential licensing issues

### 4. GTCEu Material Icon Solution

Since GTCEu textures are generated dynamically:

1. **Extract base textures from GTCEu JAR** (e.g., `tagprefix/dust.png`)
2. **Return material color in API response**
3. **Frontend Canvas rendering**:
   ```javascript
   // Load base texture
   const img = new Image();
   img.src = '/api/icon/gtceu:dust_template';

   // Apply color overlay
   ctx.drawImage(img, 0, 0);
   ctx.globalCompositeOperation = 'multiply';
   ctx.fillStyle = `#${materialColor}`;
   ctx.fillRect(0, 0, 16, 16);
   ```

## Recommended Implementation

### Phase 1: Frontend CDN Fallback for Minecraft Items
1. Modify `/api/items` to include `iconAvailable: boolean` field
2. Frontend checks if icon available, else fetches from external CDN
3. CDN URL: `https://unpkg.com/minecraft-assets@1.15.0/minecraft-assets/data/1.20.2/items/{path}.png`

### Phase 2: GTCEu Material Color Support
1. Add `materialColor: string` field to ItemStackDTO for GTCEu items
2. Extract GTCEu base textures (tagprefix templates) to web resources
3. Frontend applies color overlay using Canvas

### Phase 3: Translation Fallback
1. Bundle Minecraft `en_us.json` translations in mod resources
2. Add more mod namespaces to `KNOWN_NAMESPACES` as needed

## CDN URL Examples

```
# Minecraft vanilla items
https://unpkg.com/minecraft-assets@1.15.0/minecraft-assets/data/1.20.2/items/diamond.png
https://unpkg.com/minecraft-assets@1.15.0/minecraft-assets/data/1.20.2/items/iron_ingot.png

# Alternative (mcasset.cloud via GitHub)
https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20/assets/minecraft/textures/item/diamond.png
```

---

## Similar Projects Analysis (2025-12-30 Update)

### AppliedWebTerminal (ZhuRuoLing)
**URL:** https://github.com/ZhuRuoLing/AppliedWebTerminal

**Architecture:** Pre-baked client-side rendering

**How it works:**
1. User runs `/appwebterminal resources render` command in single-player world
2. Command iterates ALL registered items via `ForgeRegistries.ITEMS.map { AEItemKey.of(it) }`
3. Each item is rendered using Minecraft's `ItemRenderer` to a `FrameBuffer`
4. Rendered image extracted via `NativeImage` and saved to `./aeKeyResources/{keyType}/{itemId}.png`
5. For servers: user uploads the `aeKeyResources` folder to server
6. Server serves images via HTTP endpoint `/aeResource/{keyType}/{key...}`

**Key Components:**
- `AEKeyRenderer.kt` - Manages framebuffer rendering
- `AEKeyImageProvider` - Interface for different key types (items, fluids, chemicals)
- `FrontendSupportRouting.kt` - Serves pre-rendered PNGs

**Pros:**
- Perfect pixel-accurate icons (uses actual game renderer)
- Works with ALL mods (any item that renders in-game)
- Handles dynamic textures, overlays, GTCEu materials correctly
- No CDN dependencies

**Cons:**
- Requires manual step (run command, upload folder)
- Large storage (thousands of PNG files)
- Must re-render when mods change
- Only works on client (command must run in single-player)

**Code Example:**
```kotlin
// AEKeyRenderer.kt - Core rendering logic
fun submitRenderTasks(basePath: Path, progressListener: RenderProgressListener) {
    KeyImageProviderRegistry.values.toList().forEach { prov ->
        val entries = prov.getAllEntries().toList()
        renderTasks += {
            entries.forEach { key ->
                render(key, basePath / keyType / "$keyId.png")
            }
        }
    }
}

// FrontendSupportRouting.kt - Serving endpoint
get("/aeResource/{keyType}/{key...}") {
    val filePath = Path("./aeKeyResources") / keyType / "$key.png"
    call.respondFile(filePath.toFile())
}
```

**Registry-based Provider System:**
AppliedWebTerminal uses a registry for extensibility:
```kotlin
interface AEKeyImageProvider<T : AEKey> {
    val keyType: AEKeyType
    fun getAllEntries(): Iterable<T>
    fun renderImage(aeKey: T, poseStack: PoseStack, bufferSource: BufferSource, ...)
}
```

This allows mod compatibility via `@ImageProvider` annotation:
- `AEItemKeyImageProvider` - Vanilla items
- `AEFluidKeyImageProvider` - Fluids
- `ChemicalKeyImageProvider` - Mekanism chemicals
- `ManaKeyImageProvider` - Botania mana
- `SourceKeyImageProvider` - Ars Nouveau source

---

### GTNH-OC-AE-Controller (SmileYik)
**URL:** https://github.com/SmileYik/GTNH-OC-AE-Controller

**Architecture:** In-game OpenComputers integration (not web-based)

This project is for GTNH (GregTech New Horizons) modpack with OpenComputers, providing AE controller functionality within Minecraft itself. The architecture is fundamentally different - it uses OC screens and in-game rendering rather than a web browser.

---

## Comparison of Approaches

| Approach | Icon Quality | Setup Complexity | Mod Compatibility | Offline Support |
|----------|-------------|------------------|-------------------|-----------------|
| CDN Fallback (Current) | Medium | Zero config | Vanilla only | No |
| Pre-baked Rendering | Perfect | Manual step | All mods | Yes |
| Server JAR Extraction | Low | Zero config | Some mods | Yes |

---

## Recommendations

### Short-term (Current v1.3.x)
Keep CDN fallback approach with improvements:
- Use `assets.mcasset.cloud` for item AND block textures
- Colored placeholder for GTCEu materials via `materialColor`

### Medium-term (Future v1.4.x)
Implement optional pre-rendering similar to AppliedWebTerminal:
1. Add `/ae2web render` client command
2. Save to `config/ae2web/icons/` folder
3. Serve from this folder, fallback to CDN

### Long-term
If icon quality is critical:
1. Port AppliedWebTerminal's rendering approach (Kotlin → Java)
2. Iterate all registries (`ForgeRegistries.ITEMS`, fluids, etc.)
3. Use `FrameBuffer` + `NativeImage` for rendering
4. Provide upload mechanism for server deployments

---

## References

- PrismarineJS/minecraft-assets: https://github.com/PrismarineJS/minecraft-assets
- mcasset.cloud: https://mcasset.cloud/
- assets.mcasset.cloud: https://assets.mcasset.cloud/1.20.1/assets/minecraft/textures/
- aternosorg/renderchest: https://github.com/aternosorg/renderchest
- GTCEu Material API: https://cleanroommc.com/zh/groovy-script/mods/gtceu/material
- AppliedWebTerminal: https://github.com/ZhuRuoLing/AppliedWebTerminal
- GTNH-OC-AE-Controller: https://github.com/SmileYik/GTNH-OC-AE-Controller
