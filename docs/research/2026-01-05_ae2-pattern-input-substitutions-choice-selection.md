# Research: AE2 pattern input substitutions vs “selected choice”

**Date**: 2026-01-05  
**Researcher**: Codex CLI (GPT-5.2)  
**Status**: ✅ Complete

---

## ✅ Direct Answer

AE2’s public API does **not** expose a “this crafting job picked choice X for this ingredient slot” value. What you are seeing as `possibleInputs` in `IPatternDetails.IInput.getPossibleInputs()` are **substitutions**: the first entry is the primary ingredient, and the remaining entries are substitutes that can be consumed **if already available**, but **won’t be autocrafted**. That means there often is no single fixed “chosen choice” to report for a job; it can be opportunistic at execution time.

---

## 📚 Official Guidance (AE2 API docs)

### `IPatternDetails.IInput.getPossibleInputs()`

AE2 explicitly documents that `possibleInputs` are “primary + substitutes” and that substitutes won’t be autocrafted:

- https://appliedenergistics.org/javadoc/index-all.html (see `IPatternDetails.IInput#getPossibleInputs()`)

### Pattern encoding supports substitutions

The API includes `PatternDetailsHelper.encodeCraftingPattern(..., boolean allowSubstitutes, boolean allowFluidSubstitutes)` which confirms substitutions are a first-class pattern capability (not a per-job “choice”):

- https://appliedenergistics.org/javadoc/appeng/api/crafting/PatternDetailsHelper.html

---

## 📌 What this means for AE2Web “dependency tree”

### 1) “Which recipe/pattern did the job pick?”

That’s typically **fixed** for a submitted job and can be represented by the set of patterns referenced in the job’s internal “tasks” (your backend already reads this and returns `patterns[]`).

### 2) “Which ingredient substitute did the job pick?”

That is **not guaranteed to be fixed** and may not be represented as a single chosen value in the public API.

Practical UI approach:
- Treat `choices[]` as “substitutes/alternatives”, not “planned concrete edges”.
- Highlight the alternative(s) that are *actually in play right now* by cross-referencing the live `entries[]` counts (stored/waiting/active) for those keys.

---

## 🎯 Recommended UI behavior

- Default graph: show only the **primary** input for each pattern slot.
- Substitutes:
  - hide by default, or render as dashed “optional” edges
  - auto-highlight/show a substitute edge when that AEKey appears with non-zero `waiting/active/stored` in `entries[]`

---

## 🔗 References

- AE2 API Index (`getPossibleInputs` description): https://appliedenergistics.org/javadoc/index-all.html
- Pattern encoding helper (`allowSubstitutes`): https://appliedenergistics.org/javadoc/appeng/api/crafting/PatternDetailsHelper.html

---

**Research completed on**: 2026-01-05

