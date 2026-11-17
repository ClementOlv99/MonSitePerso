import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';

// Reads API_URL from your .env file if present, otherwise uses the local default
const API_URL = env.API_URL ?? 'http://localhost:8080/api';

export async function load({ fetch }) {
	let response;

	try {
		response = await fetch(`${API_URL}/races`);
	} catch {
		// Network failure: Spring Boot not started, wrong port...
		error(503, 'Backend unreachable: is Spring Boot running?');
	}

	if (!response.ok) {
		error(response.status, `Backend error (${response.status})`);
	}

	const races = await response.json();
	return { races };
}