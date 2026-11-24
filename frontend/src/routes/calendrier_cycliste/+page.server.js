import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';

// Reads API_URL from your .env file if present, otherwise uses the local default
const API_URL = env.API_URL ?? 'http://localhost:8080/api';

// Riders loaded automatically on the calendar page. Edit this list freely.
// Use the riderUrl (see scraper/Startlist.json), not the id: ids change every season.
const PRESET_RIDERS = [
	'rider/jai-hindley',
	'rider/laurence-pithie',
];

async function loadPresets(fetch) {
	if (PRESET_RIDERS.length === 0) return [];

	try {
		const params = new URLSearchParams();
		for (const url of PRESET_RIDERS) params.append('urls', url);

		const res = await fetch(`${API_URL}/rider/by-urls?${params}`);
		if (!res.ok) return [];
		const riders = await res.json();

		// keep the order of PRESET_RIDERS (the database returns them in any order)
		riders.sort((a, b) => PRESET_RIDERS.indexOf(a.riderUrl) - PRESET_RIDERS.indexOf(b.riderUrl));

		return await Promise.all(
			riders.map(async (rider) => {
				const r = await fetch(`${API_URL}/participation/rider/${rider.id}`);
				return { rider, races: r.ok ? await r.json() : [] };
			})
		);
	} catch {
		return [];
	}
}


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
	const presets = await loadPresets(fetch);
	return { races, presets };

}