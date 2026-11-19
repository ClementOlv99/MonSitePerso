<script>
	
	import { PUBLIC_API_URL } from '$app/env/public';

	let query = $state('');
	let suggestions = $state([]);
	let selectedRiders = $state([]);
	let timer;
	let calendarOpen = $state(false);


	let { data } = $props();          // Svelte 5. In Svelte 4: export let data;


	function formatDate(iso) {
		const [year, month, day] = iso.split('-');   // "2027-01-19" -> ["2027","01","19"]
		return `${day}/${month}`;
	}

	function formatDates(race) {
		return race.startDate === race.endDate
			? formatDate(race.startDate)                                   // one-day race
			: `${formatDate(race.startDate)} - ${formatDate(race.endDate)}`; // stage race
	}

	
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
		suggestions = []
		query = '';
		if (selectedRiders.some((entry) => entry[0].id === rider.id)) return;

		const res = await fetch(`${PUBLIC_API_URL}/participation/rider/${rider.id}`);
		if (res.ok) {
			selectedRiders.push([rider, await res.json()]);
		}
		
	}

	function isOngoing(race) {
		const today = new Date();
		var todaygoodformat = (today.getFullYear().toString() + "-" + (today.getMonth() + 1).toString().padStart(2, '0') + "-" + (today.getDate()).toString().padStart(2, '0'))
		return race.startDate <= todaygoodformat && todaygoodformat <= race.endDate
	}

	function isThisWeek(race) {

		const today = new Date()
		const offset = (today.getDay() + 6) % 7
		var todaystring = (today.getFullYear().toString() + "-" + (today.getMonth() + 1).toString().padStart(2, '0') + "-" + (today.getDate()).toString().padStart(2, '0'))


		const sunday = new Date(today.setDate(today.getDate() + (6 - offset)))
		const sundaystring =  (sunday.getFullYear().toString() + "-" + (sunday.getMonth() + 1).toString().padStart(2, '0') + "-" + (sunday.getDate()).toString().padStart(2, '0'))

		return (todaystring <= race.startDate && race.startDate <= sundaystring && !isOngoing(race))
	}

	function removeRider(rider){
		selectedRiders = selectedRiders.filter((entry) => entry[0].id !== rider.id) 
	}

	
</script>



<div class="layout">
	<section class = "calendar" class:collapsed={!calendarOpen}>
		<h1>Season calendar</h1>
		<button class="toggle" onclick={() => calendarOpen = !calendarOpen}>
        	{calendarOpen ? ' ⏷ Fermer calendrier' : '⏶ Ouvrir calendrier'}
		</button>
		<table>
			<thead>
				<tr><th>Dates</th><th>Course</th><th>Classe</th></tr>
			</thead>
			<tbody>
				{#each data.races as race (race.id)}
					<tr class:ongoing={isOngoing(race)}
						class:isthisweek={isThisWeek(race)}>
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
			<input bind:value={query} 
			oninput={onSearchInput} 
			onkeydown={(e) => { if (e.key === 'Escape') suggestions = []; }} 
			onblur={() => setTimeout(() => (suggestions = []), 150)} 
			placeholder="Search a rider…"
			/>
			{#if suggestions.length > 0}
				<ul class="dropdown">
					{#each suggestions as rider (rider.id)}
						<li>
							<button type="button" onclick={() => selectRider(rider)}>
								{flag(rider.country)} {rider.prenom} {rider.nom}
							</button>
						</li>
					{/each}
				</ul>
			{/if}
		</div>


				
		{#each selectedRiders as entry (entry[0].id)}
			<h2>
				{flag(entry[0].country)} {entry[0].prenom} {entry[0].nom}
				<button
                	type="button"
                	class="close"
                	aria-label="Fermer le calendrier de {entry[0].prenom} {entry[0].nom}"
                	onclick={() => removeRider(entry[0])}>
					×
				</button>
			</h2>
			<table>
				<thead>
					<tr><th>Dates</th><th>Course</th><th>Classe</th></tr>
				</thead>
				<tbody>
					{#each entry[1] as race (race.id)}
						<tr class:ongoing={isOngoing(race)}>
							<td>{formatDates(race)}</td>
							<td>{flag(race.country)} {race.name}</td>
							<td>{race.category}</td>
						</tr>
					{:else}
						<tr><td colspan="3">No races yet: run the scraper first.</td></tr>
					{/each}
				</tbody>
			</table>
		{/each}


		
	</aside>
</div>

<style>
.layout {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 2rem;
		align-items: start;
		padding-inline: 2rem;
		font-size:20px

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
	width: 50%;
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



tr.ongoing {
	color: rgb(179, 29, 156)
}

tr.isthisweek {
	color: rgb(21, 112, 70)
}

.rider-panel h2 {
	display: flex;
	align-items: center;
	gap: 0.5rem;
}

th {

        padding: 8px 12px;  
		border-bottom: 1px solid #444; 
		text-align: left;  
}

td {
        padding: 8px 12px;  
		border-bottom: 1px solid #444;   
}

table {
        border-collapse: collapse;  
}

.dropdown {
	position: absolute; 
	top: 100%;
	right:0;
	left:0;
}




.toggle { display: none; }

@media (max-width: 768px) {
        .layout {
                grid-template-columns: 1fr;
                padding-inline: 1rem;
        }
        .toggle { display: block;
				  border : none;
				  background : transparent;
				  color : #ffffff; 
				  font-size : 18px}
        .calendar.collapsed table { display: none; }
		.rider-panel h1 {
                margin-top: -1rem;     
        }

}




</style>
