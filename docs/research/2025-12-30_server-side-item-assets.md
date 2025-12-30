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

## References

- PrismarineJS/minecraft-assets: https://github.com/PrismarineJS/minecraft-assets
- mcasset.cloud: https://mcasset.cloud/
- aternosorg/renderchest: https://github.com/aternosorg/renderchest
- GTCEu Material API: https://cleanroommc.com/zh/groovy-script/mods/gtceu/material
