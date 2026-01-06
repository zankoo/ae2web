<script lang="ts">
	import { onMount, onDestroy, tick } from 'svelte';
	import type { InventoryItem } from '$lib/api/types';
	import {
		deriveRateSeries,
		formatProductionRate,
		type DataPoint
	} from '$lib/utils/productionStats';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';
	import IconImage from '$lib/components/IconImage.svelte';

	let {
		open = false,
		item = null,
		getPoints = null,
		getRate = null,
		onClose
	}: {
		open?: boolean;
		item?: InventoryItem | null;
		getPoints?: ((uniqueId: string) => DataPoint[] | null) | null;
		getRate?: ((uniqueId: string) => number | null) | null;
		onClose: () => void;
	} = $props();

	let dialogEl = $state<HTMLDialogElement | null>(null);
	let chartContainerEl = $state<HTMLDivElement | null>(null);
	let echarts: typeof import('echarts') | null = null;
	let chartInstance: import('echarts').ECharts | null = null;
	let refreshTimer: ReturnType<typeof setInterval> | null = null;

	const REFRESH_INTERVAL_MS = 500;

	$effect(() => {
		if (dialogEl) {
			if (open) {
				if (!dialogEl.open) {
					dialogEl.showModal();
				}
				// Wait for DOM update before loading chart (chartContainerEl needs to be bound)
				void tick().then(() => loadChartAndData());
			} else {
				if (dialogEl.open) {
					dialogEl.close();
				}
				stopRefreshTimer();
			}
		}
	});

	function displayNameFor(it: InventoryItem): string {
		const name = it?.name;
		if (typeof name === 'string' && name.trim()) {
			return name;
		}
		if (it.id.includes(':')) {
			return it.id.split(':', 2)[1] || it.id;
		}
		return it.id;
	}

	async function loadChartAndData(): Promise<void> {
		if (!chartContainerEl || !item) return;

		// Lazy load ECharts
		if (!echarts) {
			echarts = await import('echarts');
		}

		// Initialize chart if needed
		if (!chartInstance) {
			chartInstance = echarts.init(chartContainerEl, undefined, {
				renderer: 'canvas'
			});
		}

		updateChart();
		startRefreshTimer();
	}

	function formatChartRate(rate: number, isFluid: boolean): string {
		if (rate === 0) return '0';

		const sign = rate > 0 ? '+' : '';

		if (isFluid) {
			// Internal rate is mB/s; display as B/s or B/m
			const rateInBuckets = rate / 1000;
			const absRateInBuckets = Math.abs(rateInBuckets);

			if (absRateInBuckets >= 0.1) {
				return `${sign}${rateInBuckets.toFixed(2)}B/s`;
			}

			return `${sign}${(rateInBuckets * 60).toFixed(2)}B/m`;
		}

		const absRate = Math.abs(rate);
		if (absRate >= 1) {
			if (absRate >= 1000) {
				return `${sign}${(rate / 1000).toFixed(1)}K/s`;
			}
			return `${sign}${rate.toFixed(2)}/s`;
		}

		return `${sign}${(rate * 60).toFixed(2)}/m`;
	}

	function updateChart(): void {
		if (!chartInstance || !item || !getPoints) return;

		const points = getPoints(item.uniqueId);
		const seriesPoints = points ? deriveRateSeries(points) : [];
		if (!seriesPoints || seriesPoints.length === 0) {
			chartInstance.setOption({
				graphic: {
					type: 'text',
					left: 'center',
					top: 'center',
					style: {
						text: 'No production data yet',
						fontSize: 14,
						fill: '#64748B'
					}
				}
			});
			return;
		}

		const isFluid = item.type === 'fluid';
		const data = seriesPoints.map((p) => [p.timestamp, p.rate]);

		const option: import('echarts').EChartsOption = {
			animation: false,
			graphic: [],
			backgroundColor: 'transparent',
			grid: {
				left: 60,
				right: 24,
				top: 40,
				bottom: 40
			},
			tooltip: {
				trigger: 'axis',
				backgroundColor: 'rgba(15, 23, 42, 0.95)',
				borderColor: 'var(--color-border)',
				borderWidth: 1,
				textStyle: {
					color: '#fff',
					fontFamily: 'var(--font-sans)'
				},
				formatter: (params: any) => {
					const point = params[0];
					if (!point) return '';
					const time = new Date(point.value[0]).toLocaleTimeString();
					const rate = formatChartRate(point.value[1], isFluid);
					return `<div style="font-family: var(--font-mono); font-size: 12px;">
						<div style="color: #94A3B8; margin-bottom: 4px;">${time}</div>
						<div style="font-weight: 600;">${rate}</div>
					</div>`;
				}
			},
			xAxis: {
				type: 'time',
				axisLine: {
					lineStyle: { color: '#E2E8F0' }
				},
				axisTick: {
					lineStyle: { color: '#E2E8F0' }
				},
				axisLabel: {
					color: '#64748B',
					fontFamily: 'var(--font-mono)',
					fontSize: 10,
					formatter: (value: number) => {
						const d = new Date(value);
						return `${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
					}
				},
				splitLine: {
					show: false
				}
			},
			yAxis: {
				type: 'value',
				axisLine: {
					show: false
				},
				axisTick: {
					show: false
				},
				axisLabel: {
					color: '#64748B',
					fontFamily: 'var(--font-mono)',
					fontSize: 10,
					formatter: (value: number) => {
						if (isFluid) {
							const bps = value / 1000;
							const absBps = Math.abs(bps);
							if (absBps >= 1000) {
								return `${(bps / 1000).toFixed(1)}K B/s`;
							}
							if (absBps >= 1) {
								return `${bps.toFixed(1)} B/s`;
							}
							return `${bps.toFixed(2)} B/s`;
						}
						const absValue = Math.abs(value);
						if (absValue >= 1000) {
							return `${(value / 1000).toFixed(1)}K/s`;
						}
						if (absValue >= 1) {
							return `${value.toFixed(1)}/s`;
						}
						return `${value.toFixed(2)}/s`;
					}
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
					symbol: 'circle',
					symbolSize: 4,
					showSymbol: data.length < 50,
					lineStyle: {
						color: 'var(--color-accent)',
						width: 2
					},
					itemStyle: {
						color: 'var(--color-accent)'
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

	function startRefreshTimer(): void {
		stopRefreshTimer();
		refreshTimer = setInterval(() => {
			if (open && item) {
				updateChart();
			}
		}, REFRESH_INTERVAL_MS);
	}

	function stopRefreshTimer(): void {
		if (refreshTimer) {
			clearInterval(refreshTimer);
			refreshTimer = null;
		}
	}

	function handleDialogClick(e: MouseEvent): void {
		if (e.target === dialogEl) {
			onClose();
		}
	}

	function handleResize(): void {
		chartInstance?.resize();
	}

	onMount(() => {
		window.addEventListener('resize', handleResize);
	});

	onDestroy(() => {
		stopRefreshTimer();
		chartInstance?.dispose();
		chartInstance = null;
		window.removeEventListener('resize', handleResize);
	});

	let currentRate = $derived(item && getRate ? getRate(item.uniqueId) : null);
	let rateText = $derived(item ? formatProductionRate(currentRate, item.type === 'fluid') : null);
	let currentAmount = $derived(
		item
			? item.type === 'fluid'
				? formatFluidAmount(Number(item.amount))
				: formatAmount(Number(item.amount))
			: ''
	);
</script>

<dialog
	bind:this={dialogEl}
	class="modal"
	onclick={handleDialogClick}
	onkeydown={(e) => e.key === 'Escape' && onClose()}
>
	<div
		class="modal-box max-w-2xl bg-card border border-border shadow-xl rounded-2xl p-0 overflow-hidden"
	>
		<!-- Header -->
		<div class="flex items-center gap-4 p-5 border-b border-border bg-muted/30">
			<div
				class="w-12 h-12 rounded-xl bg-card border border-border flex items-center justify-center shadow-sm"
			>
				{#if item?.iconKey}
					<IconImage
						iconKey={item.iconKey}
						alt={item ? displayNameFor(item) : ''}
						className="w-8 h-8"
					/>
				{:else}
					<div class="w-8 h-8 bg-muted rounded"></div>
				{/if}
			</div>
			<div class="flex-1 min-w-0">
				<h3 class="font-display text-lg font-normal text-foreground truncate">
					{item ? displayNameFor(item) : 'Production Chart'}
				</h3>
				<div class="flex items-center gap-3 mt-1">
					<span class="text-sm text-muted-foreground font-mono">
						{currentAmount}
					</span>
					{#if rateText}
						<span
							class="text-xs font-mono font-bold px-2 py-0.5 rounded {currentRate && currentRate > 0
								? 'text-emerald-600 bg-emerald-500/10'
								: 'text-red-600 bg-red-500/10'}"
						>
							{rateText}
						</span>
					{/if}
				</div>
			</div>
			<button
				type="button"
				class="btn btn-ghost btn-sm btn-square rounded-full"
				aria-label="Close"
				onclick={onClose}
			>
				<svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M18 6L6 18M6 6l12 12" />
				</svg>
			</button>
		</div>

		<!-- Chart -->
		<div class="p-5">
			<div class="text-xs uppercase tracking-widest text-muted-foreground font-bold mb-3">
				Production Rate (Last 30s)
			</div>
			<div
				bind:this={chartContainerEl}
				class="w-full h-64 rounded-xl bg-muted/20 border border-border/50"
			></div>
		</div>

		<!-- Footer -->
		<div class="px-5 pb-5 flex justify-end">
			<button type="button" class="btn btn-ghost btn-sm" onclick={onClose}> Close </button>
		</div>
	</div>
	<form method="dialog" class="modal-backdrop bg-black/30 backdrop-blur-sm">
		<button type="button" onclick={onClose}>close</button>
	</form>
</dialog>
