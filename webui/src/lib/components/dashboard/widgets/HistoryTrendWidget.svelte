<script lang="ts">
	import { onMount, onDestroy, tick } from 'svelte';
	import type { HistoryTrendOptions, InventoryItem } from '$lib/api/types';
	import {
		deriveRateSeries,
		type DataPoint,
		type ProductionStatsTracker,
		type RatePoint
	} from '$lib/utils/productionStats';
	import { fetchHistory, type HistoryDataPoint } from '$lib/api/history';
	import IconImage from '$lib/components/IconImage.svelte';

	interface Props {
		options: HistoryTrendOptions;
		terminalId: string;
		inventoryItems: InventoryItem[];
		productionStats: ProductionStatsTracker | null;
	}

	let { options, terminalId, inventoryItems, productionStats }: Props = $props();

	let chartContainerEl: HTMLDivElement | null = $state(null);
	let echarts: typeof import('echarts') | null = $state(null);
	let chartInstance: import('echarts').ECharts | null = $state(null);
	let refreshTimer: ReturnType<typeof setInterval> | null = null;
	let historyLoadTimer: ReturnType<typeof setInterval> | null = null;

	let historicalPoints: HistoryDataPoint[] = $state([]);
	let loading = $state(false);

	const REFRESH_INTERVAL_MS = 1000;
	const HISTORY_REFRESH_INTERVAL_MS = 60000; // Refresh history every minute

	// Time ranges in milliseconds - must match HistoryTrendOptions['timeRange']
	const TIME_RANGES: Record<HistoryTrendOptions['timeRange'], number> = {
		'1h': 60 * 60 * 1000,
		'6h': 6 * 60 * 60 * 1000,
		'24h': 24 * 60 * 60 * 1000,
		'7d': 7 * 24 * 60 * 60 * 1000
	};

	let trackedItem = $derived(
		inventoryItems.find((item) => item.uniqueId === options.itemId) ?? null
	);

	function displayNameFor(item: InventoryItem): string {
		const name = item?.name;
		if (typeof name === 'string' && name.trim()) {
			return name;
		}
		if (item.id.includes(':')) {
			return item.id.split(':', 2)[1] || item.id;
		}
		return item.id;
	}

	async function loadHistoricalData(): Promise<void> {
		if (!terminalId || !options.itemId) return;

		const timeRangeMs = TIME_RANGES[options.timeRange] || TIME_RANGES['1h'];
		const from = Date.now() - timeRangeMs;

		try {
			loading = true;
			const response = await fetchHistory(terminalId, options.itemId, from);
			historicalPoints = response.points || [];
		} catch (e) {
			console.error('Failed to load history data', e);
			historicalPoints = [];
		} finally {
			loading = false;
		}
	}

	async function loadChartAndData(): Promise<void> {
		if (!chartContainerEl) return;

		if (!echarts) {
			echarts = await import('echarts');
		}

		if (!chartInstance) {
			chartInstance = echarts.init(chartContainerEl, undefined, {
				renderer: 'canvas'
			});
		}

		await loadHistoricalData();
		updateChart();
		startRefreshTimer();
		startHistoryLoadTimer();
	}

	function computeChartData(): RatePoint[] {
		const timeRangeMs = TIME_RANGES[options.timeRange] || TIME_RANGES['1h'];
		const now = Date.now();
		const cutoff = now - timeRangeMs;

		// Combine historical data with real-time data
		const allPoints: DataPoint[] = [];

		// Add historical points
		for (const p of historicalPoints) {
			if (p.timestamp >= cutoff) {
				allPoints.push({ timestamp: p.timestamp, amount: Number(p.amount) });
			}
		}

		// Add real-time points from ProductionStatsTracker
		if (productionStats && terminalId && options.itemId) {
			const realtimePoints = productionStats.getPoints(terminalId, options.itemId);
			if (realtimePoints) {
				for (const p of realtimePoints) {
					if (p.timestamp >= cutoff) {
						// Only add if not already present (avoid duplicates)
						const exists = allPoints.some(
							(existing) => Math.abs(existing.timestamp - p.timestamp) < 1000
						);
						if (!exists) {
							allPoints.push(p);
						}
					}
				}
			}
		}

		// Sort by timestamp
		allPoints.sort((a, b) => a.timestamp - b.timestamp);

		// Derive rate series
		// Use a window proportional to time range for smoother display
		const windowMs = Math.min(timeRangeMs / 10, 5 * 60 * 1000); // Max 5 minutes
		return deriveRateSeries(allPoints, windowMs);
	}

	function updateChart(): void {
		if (!chartInstance || !options.itemId) return;

		const seriesPoints = computeChartData();

		if (!seriesPoints || seriesPoints.length === 0) {
			// Use notMerge to clear any old series data
			chartInstance.setOption(
				{
					series: [],
					xAxis: { show: false },
					yAxis: { show: false },
					grid: { show: false },
					graphic: {
						type: 'text',
						left: 'center',
						top: 'center',
						style: {
							text: loading ? 'Loading...' : 'No data yet',
							fontSize: 12,
							fill: '#64748B'
						}
					}
				},
				{ notMerge: true }
			);
			return;
		}

		const isFluid = trackedItem?.type === 'fluid';
		const data = seriesPoints.map((p) => [p.timestamp, p.rate]);

		const option: import('echarts').EChartsOption = {
			animation: false,
			graphic: [],
			backgroundColor: 'transparent',
			grid: {
				left: 50,
				right: 12,
				top: 12,
				bottom: 24
			},
			tooltip: {
				trigger: 'axis',
				backgroundColor: 'rgba(15, 23, 42, 0.95)',
				borderColor: 'var(--color-border)',
				borderWidth: 1,
				textStyle: {
					color: '#fff',
					fontFamily: 'var(--font-sans)',
					fontSize: 11
				},
				formatter: (params: unknown) => {
					const p = Array.isArray(params) ? params[0] : params;
					if (!p || typeof p !== 'object') return '';
					const { value } = p as { value: [number, number] };
					if (!Array.isArray(value)) return '';
					const [ts, rate] = value;
					const date = new Date(ts);
					const timeStr = date.toLocaleTimeString();
					const rateStr = formatRate(rate, isFluid);
					return `${timeStr}<br/>${rateStr}`;
				}
			},
			xAxis: {
				type: 'time',
				axisLine: { show: false },
				axisTick: { show: false },
				axisLabel: {
					color: '#64748B',
					fontFamily: 'var(--font-mono)',
					fontSize: 9
				},
				splitLine: { show: false }
			},
			yAxis: {
				type: 'value',
				axisLine: { show: false },
				axisTick: { show: false },
				axisLabel: {
					color: '#64748B',
					fontFamily: 'var(--font-mono)',
					fontSize: 9,
					formatter: (value: number) => formatRate(value, isFluid)
				},
				min: (value) => Math.min(0, value.min),
				max: (value) => Math.max(0, value.max),
				splitLine: {
					lineStyle: {
						color: '#F1F5F9',
						type: 'dashed'
					}
				}
			},
			series: [
				{
					type: 'line',
					data,
					smooth: 0.3,
					symbol: 'none',
					lineStyle: {
						color: 'var(--color-accent)',
						width: 2
					},
					areaStyle: {
						color: {
							type: 'linear',
							x: 0,
							y: 0,
							x2: 0,
							y2: 1,
							colorStops: [
								{ offset: 0, color: 'rgba(0, 82, 255, 0.15)' },
								{ offset: 1, color: 'rgba(0, 82, 255, 0.02)' }
							]
						}
					}
				}
			]
		};

		chartInstance.setOption(option, { notMerge: true });
	}

	function formatRate(rate: number, isFluid: boolean): string {
		if (isFluid) {
			const rateInBuckets = rate / 1000;
			if (Math.abs(rateInBuckets) >= 1) {
				return `${rateInBuckets.toFixed(1)}B/s`;
			}
			return `${(rateInBuckets * 60).toFixed(1)}B/m`;
		}
		if (Math.abs(rate) >= 1000) {
			return `${(rate / 1000).toFixed(1)}K/s`;
		}
		if (Math.abs(rate) >= 1) {
			return `${rate.toFixed(1)}/s`;
		}
		return `${(rate * 60).toFixed(1)}/m`;
	}

	function startRefreshTimer(): void {
		stopRefreshTimer();
		refreshTimer = setInterval(() => {
			updateChart();
		}, REFRESH_INTERVAL_MS);
	}

	function stopRefreshTimer(): void {
		if (refreshTimer) {
			clearInterval(refreshTimer);
			refreshTimer = null;
		}
	}

	function startHistoryLoadTimer(): void {
		stopHistoryLoadTimer();
		historyLoadTimer = setInterval(() => {
			void loadHistoricalData();
		}, HISTORY_REFRESH_INTERVAL_MS);
	}

	function stopHistoryLoadTimer(): void {
		if (historyLoadTimer) {
			clearInterval(historyLoadTimer);
			historyLoadTimer = null;
		}
	}

	function handleResize(): void {
		chartInstance?.resize();
	}

	// Reload history when time range changes
	$effect(() => {
		if (options.timeRange && terminalId && options.itemId) {
			void loadHistoricalData();
		}
	});

	onMount(() => {
		window.addEventListener('resize', handleResize);
		void tick().then(() => loadChartAndData());
	});

	onDestroy(() => {
		stopRefreshTimer();
		stopHistoryLoadTimer();
		chartInstance?.dispose();
		chartInstance = null;
		window.removeEventListener('resize', handleResize);
	});
</script>

<div class="history-trend-widget">
	{#if !options.itemId}
		<div class="empty-state">
			<svg
				class="w-8 h-8 text-muted-foreground/30 mb-2"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="1.5"
			>
				<path
					stroke-linecap="round"
					stroke-linejoin="round"
					d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5M9 11.25v1.5M12 9v3.75m3-6v6"
				/>
			</svg>
			<p class="text-xs text-muted-foreground/50">No item selected</p>
			<p class="text-xs text-muted-foreground/30 mt-1">Edit this widget to choose an item</p>
		</div>
	{:else}
		{#if trackedItem}
			<div class="item-header">
				<IconImage
					iconKey={trackedItem.iconKey}
					alt={displayNameFor(trackedItem)}
					className="w-5 h-5"
				/>
				<span class="item-name">{displayNameFor(trackedItem)}</span>
				<span class="time-range badge badge-sm badge-ghost">{options.timeRange}</span>
			</div>
		{/if}
		<div class="chart-container" bind:this={chartContainerEl}></div>
	{/if}
</div>

<style>
	.history-trend-widget {
		display: flex;
		flex-direction: column;
		height: 100%;
	}

	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		flex: 1;
		text-align: center;
	}

	.item-header {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 0.5rem;
	}

	.item-header :global(img) {
		image-rendering: pixelated;
	}

	.item-name {
		flex: 1;
		font-size: 0.8rem;
		color: var(--color-foreground);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.chart-container {
		flex: 1;
		min-height: 120px;
		border-radius: 0.5rem;
		background: color-mix(in srgb, var(--color-muted) 20%, transparent);
	}
</style>
