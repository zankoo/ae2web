import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

export default defineConfig(({ mode }) => ({
	plugins: [tailwindcss(), sveltekit()],
	build: {
		sourcemap: false
	},
	resolve: mode === 'test' ? { conditions: ['browser'] } : undefined,
	server: {
		proxy: {
			'/api': {
				target: 'http://127.0.0.1:8080',
				changeOrigin: true,
				// Sends X-Forwarded-Host so the backend's same-origin check matches the dev server's Origin.
				xfwd: true
			}
		}
	},
	test: {
		environment: 'jsdom',
		environmentOptions: {
			jsdom: {
				url: 'http://localhost/'
			}
		},
		setupFiles: ['./src/tests/setup.ts']
	}
}));
