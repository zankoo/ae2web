# Research: Crafting Process dependency tree graph libraries

**Date**: 2026-01-05  
**Researcher**: Codex CLI (GPT-5.2)  
**Status**: Complete

---

## Direct Answer

Use `@xyflow/svelte` (Svelte Flow) as the primary renderer for an interactive node+edge dependency DAG in Svelte 5, paired with a layout engine (ELK.js or Dagre) and a custom edge component to implement “leaf-to-root” flow animation. If you want a more “graphics engine” style toolkit with built-in animation primitives (e.g. running dashed lines), `@antv/g6` is the strongest alternative.

---

## Evidence from Real Code / Repos

### Example 1: XYFlow (Svelte Flow) — custom edges + layout examples

Svelte Flow is actively maintained by the React Flow creators and has first-party docs/examples for custom edges and layouting (Dagre / ELK.js).

```ts
import { SvelteFlow } from '@xyflow/svelte';
import '@xyflow/svelte/dist/style.css';

// edges can be marked animated, and custom edge components are supported
const edges = [{ id: 'e1', source: 'a', target: 'b', animated: true }];
```

Sources:
- Repo: https://github.com/xyflow/xyflow
- Docs: https://svelteflow.dev/
- Custom edges: https://svelteflow.dev/learn/customization/custom-edges
- Layouting: https://svelteflow.dev/learn/layouting/layouting-libraries
- Dagre example: https://svelteflow.dev/examples/layout/dagre
- ELK.js example: https://svelteflow.dev/examples/layout/elkjs
- Utility `getBezierPath()`: https://svelteflow.dev/api-reference/utils/get-bezier-path

### Example 2: AntV G6 — animated edges (running dashed line)

G6 has a documented pattern for animating edges by changing `lineDashOffset` over time.

```ts
import { register, Line, Graph } from '@antv/g6';

class AntLine extends Line {
  onCreate() {
    this.shapeMap.key.animate([{ lineDashOffset: 20 }, { lineDashOffset: 0 }], {
      duration: 500,
      iterations: Infinity
    });
  }
}

register('edge', 'ant-line', AntLine);
```

Sources:
- Docs (example file): https://raw.githubusercontent.com/antvis/G6/main/packages/site/common/manual/core-concept/animation/ant-line.md
- Docs (animation): https://g6.antv.antgroup.com/en/manual/animation/animation
- Repo: https://github.com/antvis/G6

### Example 3: Svelvet — Svelte-native node graphs with custom edges

Svelvet provides a Svelte-first node-graph component model and allows custom edge rendering via slots.

```svelte
<script>
  import { Edge } from 'svelvet';
</script>

<Edge let:path>
  <path d={path} />
</Edge>
```

Sources:
- Repo: https://github.com/open-source-labs/Svelvet
- Docs (edge): https://raw.githubusercontent.com/open-source-labs/Svelvet/main/docs/components/edge.mdx
- Site: https://www.svelvet.io/

---

## Official Guidance (high signal)

- Svelte Flow (XYFlow) explicitly supports:
  - custom node/edge types and custom edge components
  - layout integration patterns for Dagre and ELK.js
  - utility helpers like `getBezierPath()` for edge path generation
- G6 provides a full graph visualization framework, including:
  - multiple layout algorithms (including TreeGraph examples)
  - animation primitives on nodes and edges (including “ant-line” dashed motion)

---

## Recommended Approach (for AE2 Crafting Process)

### Recommendation

Default to `@xyflow/svelte` + ELK.js:
- Your UI is already Svelte-first and needs rich node rendering (icons, counts, badges). Svelte Flow’s HTML nodes make that easy.
- “Flow from leaves to root” is easiest as an SVG custom edge: animate dash offset, gradient, or particles along the edge path.
- Layout is the hard part; ELK.js generally produces nicer DAG layouts than basic tree layouts when you have merges/shared subgraphs.

### Implementation Steps (high level)

1. Convert current dependency model into a DAG: nodes = stacks/patterns/inputs, edges = input → pattern → output (or simplify to stack → stack).
2. Run ELK.js (or Dagre) to compute node positions (re-run only when topology changes, not every poll).
3. Render nodes/edges in Svelte Flow (read-only mode, panning/zoom enabled).
4. Implement a custom edge type:
   - compute SVG path (e.g., `getBezierPath()`), then animate a “carrier” along it
   - encode progress intensity (active/waiting) into animation speed/opacity/particle density

---

## Watch Out For

### Pitfall 1: It’s a DAG, not always a strict tree

Some intermediate items may be shared across branches. Pure tree renderers often duplicate nodes or produce confusing layouts. Prefer DAG-aware layouts (ELK.js/Dagre).

### Pitfall 2: Performance with large graphs

Your current component guards with `maxNodes`/`maxDepth` — keep that idea. Also avoid re-layout on every polling tick; throttle/debounce layout updates.

### Pitfall 3: SvelteKit + SSR / DOM usage

Graph renderers frequently assume `window`/DOM APIs. Ensure the graph mounts client-side only (or guard SSR).

### Pitfall 4: Library maturity and API stability

Svelte Flow is rapidly evolving; keep it version-pinned and expect occasional API adjustments during upgrades.

---

## References

- Svelte Flow: https://svelteflow.dev/
- `@xyflow/svelte` package: https://www.npmjs.com/package/@xyflow/svelte
- XYFlow repo: https://github.com/xyflow/xyflow
- G6 docs: https://g6.antv.antgroup.com/en/
- G6 repo: https://github.com/antvis/G6
- Svelvet repo: https://github.com/open-source-labs/Svelvet

---

## Research Notes

- Apache ECharts has a tree chart, but “flowing line” effects are not a first-class feature of the tree series; you’d likely end up hacking with overlays or switching series types.
- Cytoscape.js can animate dashed edges (e.g., `line-dash-offset`) and supports DAG layouts via plugins, but it’s more imperative/DOM-managed than Svelte-first solutions and is less convenient for rich HTML node content.

---

**Research completed on**: 2026-01-05

