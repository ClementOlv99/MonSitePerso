import adapter from '@sveltejs/adapter-auto';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},

			// adapter-auto only supports some environments, see https://svelte.dev/docs/kit/adapter-auto for a list.
			// If your environment is not supported, or you settled on a specific environment, switch out the adapter.
			// See https://svelte.dev/docs/kit/adapters for more information about adapters.
			adapter: adapter()
		})
	],

	server: {
		host: true, // écoute sur toutes les interfaces (0.0.0.0) : accessible via 192.168.1.17
		port: 5173,
		strictPort: true, // échoue au lieu de passer silencieusement sur 5174
		proxy: {
			// Le navigateur appelle /api/... sur le port 5173, Vite transmet à Spring sur la Pi
			'/api': {
				target: 'http://127.0.0.1:8080',
				// Seules les lectures passent : les POST du scraper restent internes à la Pi
				bypass: (req) => (req.method === 'GET' ? undefined : false)
			}
		}
	}
});
