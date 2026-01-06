import svelteConfig from '@sveltejs/eslint-config';

export default [
	...svelteConfig,
	{
		ignores: ['build/**', '.svelte-kit/**']
	},
	{
		files: ['**/*.svelte'],
		rules: {
			// Svelte 5 runes like `$props()` update variables behind the scenes.
			// ESLint can't see that, so `prefer-const` becomes pure noise in .svelte files.
			'prefer-const': 'off'
		}
	}
];
