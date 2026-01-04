<script lang="ts">
	import { onMount } from 'svelte';

	let canvas: HTMLCanvasElement;

	// Simple canvas animation for the abstract graphic
	onMount(() => {
		if (!canvas) return;
		const ctx = canvas.getContext('2d');
		if (!ctx) return;

		let width = (canvas.width = canvas.parentElement?.clientWidth ?? 300);
		let height = (canvas.height = canvas.parentElement?.clientHeight ?? 300);

		let frame = 0;
		let animationId: number;

		const resize = () => {
			if (!canvas) return;
			width = canvas.width = canvas.parentElement?.clientWidth ?? 300;
			height = canvas.height = canvas.parentElement?.clientHeight ?? 300;
		};
		window.addEventListener('resize', resize);

		const draw = () => {
			frame++;
			ctx.clearRect(0, 0, width, height);

			// Center
			const cx = width / 2;
			const cy = height / 2;

			// Rotating Ring
			ctx.save();
			ctx.translate(cx, cy);
			ctx.rotate(frame * 0.002);
			ctx.beginPath();
			ctx.arc(0, 0, 100, 0, Math.PI * 2);
			ctx.strokeStyle = 'rgba(0, 82, 255, 0.1)';
			ctx.lineWidth = 1;
			ctx.setLineDash([10, 10]);
			ctx.stroke();
			ctx.restore();

			// Floating Shape 1
			const y1 = Math.sin(frame * 0.01) * 10;
			ctx.fillStyle = 'rgba(77, 124, 255, 0.1)';
			ctx.beginPath();
			ctx.arc(cx + 40, cy - 40 + y1, 20, 0, Math.PI * 2);
			ctx.fill();

			// Floating Shape 2
			const y2 = Math.cos(frame * 0.015) * 8;
			ctx.fillStyle = 'rgba(0, 82, 255, 0.05)';
			ctx.beginPath();
			ctx.rect(cx - 60, cy + 20 + y2, 40, 40);
			ctx.fill();

			animationId = requestAnimationFrame(draw);
		};

		draw();

		return () => {
			window.removeEventListener('resize', resize);
			cancelAnimationFrame(animationId);
		};
	});
</script>

<div
	class="relative w-full h-full overflow-hidden opacity-50 pointer-events-none"
	aria-hidden="true"
>
	<canvas bind:this={canvas} class="absolute inset-0 w-full h-full"></canvas>
	<!-- Dot Grid Overlay -->
	<div class="absolute inset-0 texture-dots opacity-30"></div>
</div>
