<script>
	import { PUBLIC_API_URL } from '$app/env/public';

	let query = $state('');
	let suggestions = $state([]);
	let selectedRider = $state(null);
	let schedule = $state([]);
	let timer;


	let { data } = $props();          // Svelte 5. In Svelte 4: export let data;

	// A plain JavaScript function, defined in the <script> block
	function formatDate(iso) {
		const [year, month, day] = iso.split('-');   // "2027-01-19" -> ["2027","01","19"]
		return `${day}/${month}`;
	}

	function formatDates(race) {
		return race.startDate === race.endDate
			? formatDate(race.startDate)                                   // one-day race
			: `${formatDate(race.startDate)} - ${formatDate(race.endDate)}`; // stage race
	}

	// "fr" -> 🇫🇷 (each letter becomes a Unicode "regional indicator")
	function flag(code) {
		if (!code) return '';
		return code
			.toUpperCase()
			.replace(/./g, (c) => String.fromCodePoint(127397 + c.charCodeAt(0)));
	}

	async function onSearchInput() {
		clearTimeout(timer);
		const q = query.trim();
		if (q.length < 2) {
			suggestions = [];
			return;
		}
		timer = setTimeout(async () => {
			const res = await fetch(`${PUBLIC_API_URL}/rider/search?q=${encodeURIComponent(q)}`);
			if (res.ok) {
				suggestions = await res.json();
			}
		}, 250);
	}

	async function selectRider(rider) {
		
	}
</script>



<div class="layout">
	<section class = "calendar">
		<h1>Season calendar</h1>

		<table>
			<thead>
				<tr><th>Dates</th><th>Course</th><th>Classe</th></tr>
			</thead>
			<tbody>
				{#each data.races as race (race.id)}
					<tr>
						<!-- Calling a function inside the markup: put the call between { } -->
						<td>{formatDates(race)}</td>
						<td>{flag(race.country)} {race.name}</td>
						<td>{race.category}</td>
					</tr>
				{:else}
					<tr><td colspan="3">No races yet: run the scraper first.</td></tr>
				{/each}
			</tbody>
		</table>
	</section>

	<aside class="rider-panel">
	<h1>
		Calendrier d'un coureur specifique
	</h1>
		<div class="search">
			<input bind:value={query} oninput={onSearchInput}
			placeholder="Search a rider…" />
		</div>

		{#if suggestions.length > 0}
			<ul>
				{#each suggestions as rider (rider.id)}
					<li>
						<button type="button">
							{flag(rider.country)} {rider.prenom} {rider.nom}
						</button>
					</li>
				{/each}
			</ul>
		{/if}
	</aside>
</div>

<style>
	.layout {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 2rem;
		align-items: start;
	}

	.search {
	position: relative;
}

.search input {
	width: 50%;
	padding: 6px 10px;
	box-sizing: border-box;
}

.search ul {
	position: absolute;
	top: 100%;
	left: 0;
	right: 0;
	z-index: 10;
	margin: 0;
	padding: 0;
	list-style: none;
	background: white;
	border: 1px solid #ccc;
	max-height: 300px;
	overflow-y: auto;
}

.search button {
	width: 100%;
	text-align: left;
	padding: 6px 10px;
	border: none;
	background: none;
	cursor: pointer;
}

.search button:hover {
	background: #eee;
}

</style>
