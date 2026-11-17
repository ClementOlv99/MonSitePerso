import { defineEnvVars } from '@sveltejs/kit/env';

// Every environment variable must be declared here (SvelteKit 3).
// Values come from .env (or the real environment when deployed).
export const variables = defineEnvVars({
	// Server-only: import { API_URL } from '$app/env/private'
	API_URL: {
		description: 'Spring Boot API base URL, used by server-side load functions',
		schema: (value) => value ?? 'http://localhost:8080/api'
	},

	// Sent to the browser: import { PUBLIC_API_URL } from '$app/env/public'
	PUBLIC_API_URL: {
		public: true,
		description: 'Spring Boot API base URL, used by browser-side fetches',
		schema: (value) => value ?? 'http://localhost:8080/api'
	}
});
