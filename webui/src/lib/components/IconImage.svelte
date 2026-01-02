<script lang="ts" context="module">
const loadedIcons = new Set<string>();

let iconObserver: IntersectionObserver | null = null;
const observed = new WeakSet<HTMLImageElement>();
const lazyHandlers = new WeakMap<HTMLImageElement, (src: string) => void>();

function ensureObserver(): void {
	if (iconObserver) return;
	iconObserver = new IntersectionObserver((entries, obs) => {
		for (const entry of entries) {
			if (!entry.isIntersecting) continue;
			const img = entry.target as HTMLImageElement | null;
			if (!img) {
				obs.unobserve(entry.target);
				continue;
			}
			const src = img.dataset.src;
			if (!src) {
				obs.unobserve(img);
				continue;
			}
			const apply = lazyHandlers.get(img);
			if (apply) {
				apply(src);
			} else {
				img.src = src;
			}
			delete img.dataset.src;
			obs.unobserve(img);
		}
	});
}
</script>

<script lang="ts">
	import { onDestroy, onMount } from 'svelte';

	import { iconUrl } from '$lib/api/prerender';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { nextIconRetry } from '$lib/utils/iconRetry';

	export let iconKey: string | null = null;
	export let alt = '';
	export let className = '';
	export let pixelated = true;
	export let lazy = true;
	export let hideOnError = false;

	const ICON_RETRY_BASE_MS = 1500;
	const ICON_RETRY_MAX_MS = 30000;
	const ICON_RETRY_MAX_ATTEMPTS = 6;

	let imgEl: HTMLImageElement | null = null;
	let currentSrc = PLACEHOLDER_ICON;
	let hidden = false;

	let retryAttempts = 0;
	let retryTimer: ReturnType<typeof setTimeout> | null = null;

	let prevIconKey: string | null = null;

	function scheduleRetry(): void {
		if (!iconKey || !imgEl) {
			return;
		}
		if (loadedIcons.has(iconKey)) {
			return;
		}

		const nextRetry = nextIconRetry(retryAttempts, {
			baseMs: ICON_RETRY_BASE_MS,
			maxMs: ICON_RETRY_MAX_MS,
			maxAttempts: ICON_RETRY_MAX_ATTEMPTS
		});
		retryAttempts = nextRetry.attempts;

		if (retryTimer) {
			clearTimeout(retryTimer);
		}
		retryTimer = setTimeout(() => {
			retryTimer = null;
			if (!imgEl?.isConnected) return;
			if (!iconKey) return;
			if (loadedIcons.has(iconKey)) return;
			currentSrc = iconUrl(iconKey);
		}, nextRetry.delayMs);
	}

	function resetForIconKey(next: string | null): void {
		if (retryTimer) {
			clearTimeout(retryTimer);
			retryTimer = null;
		}
		retryAttempts = 0;
		hidden = false;

		if (!next) {
			currentSrc = PLACEHOLDER_ICON;
			return;
		}

		const desired = iconUrl(next);
		if (loadedIcons.has(next)) {
			currentSrc = desired;
			return;
		}

		currentSrc = PLACEHOLDER_ICON;
		if (imgEl && lazy) {
			ensureObserver();
			imgEl.dataset.src = desired;
			lazyHandlers.set(imgEl, (src) => {
				currentSrc = src;
			});
			if (!observed.has(imgEl)) {
				observed.add(imgEl);
				iconObserver?.observe(imgEl);
			}
		} else {
			currentSrc = desired;
		}
	}

	$: if (iconKey !== prevIconKey) {
		prevIconKey = iconKey;
		resetForIconKey(iconKey);
	}

	function onLoad(): void {
		if (!iconKey || !imgEl) return;
		const desiredAbs = new URL(iconUrl(iconKey), window.location.href).href;
		const currentAbs = imgEl.currentSrc || imgEl.src || '';
		if (currentAbs !== desiredAbs) return;
		loadedIcons.add(iconKey);
		retryAttempts = 0;
	}

	function onError(): void {
		if (hideOnError) {
			hidden = true;
			return;
		}
		currentSrc = PLACEHOLDER_ICON;
		scheduleRetry();
	}

	onMount(() => resetForIconKey(iconKey));

	onDestroy(() => {
		if (retryTimer) {
			clearTimeout(retryTimer);
		}
		if (imgEl && iconObserver) {
			lazyHandlers.delete(imgEl);
			try {
				iconObserver.unobserve(imgEl);
			} catch {
				// ignore
			}
		}
	});
</script>

{#if !hidden}
	<img
		bind:this={imgEl}
		class={className}
		alt={alt}
		src={currentSrc}
		loading={lazy ? 'lazy' : undefined}
		style:image-rendering={pixelated ? 'pixelated' : undefined}
		on:load={onLoad}
		on:error={onError}
	/>
{/if}
