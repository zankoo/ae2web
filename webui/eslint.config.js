import svelteConfig from '@sveltejs/eslint-config';

export default [
	...svelteConfig,
	{
		ignores: ['build/**', '.svelte-kit/**']
	}
];

