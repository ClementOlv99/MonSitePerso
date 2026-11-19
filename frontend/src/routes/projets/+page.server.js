import { error } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';

const GITHUB_USER = 'ClementOlv99';
const URL = `https://api.github.com/users/${GITHUB_USER}/repos?sort=pushed&per_page=30`;

export async function load({ fetch }) {
	let response;

	try {
		response = await fetch(URL, {
			headers: { Accept: 'application/vnd.github+json' }
		});
	} catch (err) {
		// Network failure: no internet, DNS…
		console.error('GitHub unreachable:', err);
		return { projects: [], failed: true };
	}

	if (!response.ok) {
		// 403/429 = rate limit, 404 = wrong username
		console.error('GitHub error', response.status, await response.text());
		return { projects: [], failed: true };
	}

	const all  = await response.json();

    const projects = all
    	.filter((project) => !project.fork && !project.archived)
    	.slice(0, 9)
    	.map((project) => ({
    		id: project.id,
    		name: project.name,
    		description: project.description,
    		url: project.html_url,
    		language: project.language,
    		stars: project.stargazers_count,
    		topics: project.topics ?? [],
    		pushedAt: project.pushed_at
    	}));
    	return { projects, failed: false };
}


