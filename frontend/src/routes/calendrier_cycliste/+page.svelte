<script>
	
	import { PUBLIC_API_URL } from '$app/env/public';

	let query = $state('');
	let suggestions = $state([]);
	let timer;
	let calendarOpen = $state(false);




	let { data } = $props();          // Svelte 5. In Svelte 4: export let data;
	let selectedRiders = $state(data.presets);

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
	suggestions = [];
	query = '';
	if (selectedRiders.some((entry) => entry.rider.id === rider.id)) return;

	const res = await fetch(`${PUBLIC_API_URL}/participation/rider/${rider.id}`);
	if (res.ok) {
		selectedRiders.push({ rider, races: await res.json() });
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

	function removeRider(id) {
		selectedRiders = selectedRiders.filter((entry) => entry.rider.id !== id);
	}

    const DAY_MS = 24 * 60 * 60 * 1000;   // milliseconds in one day

    // "2027-02-15" -> 20864 (days since 1970-01-01)
    function toDay(iso) {
    	const [y, m, d] = iso.split('-').map(Number);
    	return Date.UTC(y, m - 1, d) / DAY_MS;
    }

    // today's date as a day number
    function todayDay() {
    	const t = new Date();
    	return Date.UTC(t.getFullYear(), t.getMonth(), t.getDate()) / DAY_MS;
    }

    // 20864 -> "15/02"
    function dayLabel(day) {
    	const d = new Date(day * DAY_MS);
    	const dd = String(d.getUTCDate()).padStart(2, '0');
    	const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
    	return `${dd}/${mm}`;
    }

    function isMonday(day) {
    	return new Date(day * DAY_MS).getUTCDay() === 1;   // 0 = Sunday, 1 = Monday…
    }

    const DAYS = 42;          // width of the window, in days
    const MAIN_LANES = 3;     // number of lines in the top calendar

    // day number of the 1st of the month that contains `day`
    function firstOfMonth(day) {
    	const d = new Date(day * DAY_MS);
    	return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1) / DAY_MS;
    }

    const today = todayDay();
    const seasonStart = data.races.length > 0 ? toDay(data.races[0].startDate) : today;
    const initialStart = firstOfMonth(Math.max(today, seasonStart));

    let start = $state(initialStart);
    let end = $derived(start + DAYS - 1);
    let days = $derived(Array.from({ length: DAYS }, (_, i) => start + i));

    function assignLanes(races, maxLanes = Infinity) {
    	const sorted = [...races].sort((a, b) => a.startDate.localeCompare(b.startDate));
    	const laneEnds = [];   // laneEnds[k] = last day used in lane k
    	const placed = [];
    	let hidden = 0;

    	for (const race of sorted) {
    		const s = toDay(race.startDate);
    		const e = toDay(race.endDate);

    		let lane = laneEnds.findIndex((lastDay) => lastDay < s);
    		if (lane === -1) {
    			lane = laneEnds.length;
    			laneEnds.push(e);
    		} else {
    			laneEnds[lane] = e;
    		}
    		placed.push({ race, lane, s, e });
    	}

    	return { placed, laneCount: Math.max(1, laneEnds.length), hidden };
    }

    let mainLayout = $derived(assignLanes(data.races, MAIN_LANES));

    function col(day) {
	return Math.min(Math.max(day - start, 0), DAYS - 1) + 1;
    }

    function isVisible(p) {
    	return p.e >= start && p.s <= end;
    }

    function categoryClass(category) {
	if (category === 'TDF') return 'tdf';
    if (category === 'GIRO') return 'giro';
    if (category === 'VUELTA') return 'vuelta';
	if (category === 'Monument') return 'monument';
	if (category === 'UWT') return 'uwt';
	return 'pro';
    }

    function categorytogt(category) {
        if (category === 'TDF' || category === 'GIRO' || category === 'VUELTA') return "Grand Tour"
        return category
    }

    let tip = $state(null)

    function showTip(e,race){
        tip = {race, x:e.clientX, y:e.clientY}
    }


    function nextMonday(day) {
            return day + 7 - (day + 3) % 7;
    }

    function prevMonday(day) {
            const w = (day + 3) % 7;
            return day - (w === 0 ? 7 : w);
    }
	
</script>

{#if tip}
    <div class="tooltip" style="left: {tip.x + 12}px; top: {tip.y + 12}px">
        <strong>{tip.race.name}</strong>
        <div>{formatDates(tip.race)} ·  {categorytogt(tip.race.category)}</div>
    </div>
{/if}

<div class="planning">
	<div class="row">
		<div class="dates" style="--days: {DAYS}">
			{#each days as day, i (day)}
				{#if isMonday(day)}
					<span style="grid-column: {i + 1}">{dayLabel(day)}</span>
				{/if}
			{/each}
		</div>
        <div class="label">Calendrier : </div>
        <button type="button" class="arrow" onclick={() => (start = prevMonday(start))}>‹</button>
	    {@render timeline(mainLayout, MAIN_LANES)}
        <button type="button" class="arrow" onclick={() => (start = nextMonday(start))}>›</button>
	</div>
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
	{#each selectedRiders as entry (entry.rider.id)}
	{@const layout = assignLanes(entry.races)}
	<div class="row rider-row">
		<div class="label">
			<button
				type="button"
				class="close"
				aria-label="Retirer {entry.rider.prenom} {entry.rider.nom}"
				onclick={() => removeRider(entry.rider.id)}
			>
				×
			</button>
			<span class="rider-name">{flag(entry.rider.country)} {entry.rider.prenom} {entry.rider.nom}</span>
		</div>
		{@render timeline(layout, 1)}
	</div>
{/each}

</div>





{#snippet timeline(layout, rows)}
	<div class="timeline" style="--days: {DAYS}; --rows: {rows}">
		{#each days as day, i (day)}
			<div
				class="day"
				class:monday={isMonday(day)}
				class:today={day === today}
				style="grid-column: {i + 1}"
			></div>
		{/each}

		{#each layout.placed as p (p.race.id)}
			{#if isVisible(p)}
				<div
					class="race {categoryClass(p.race.category)}"
					style="grid-column: {col(p.s)} / {col(p.e) + 1}; grid-row: {p.lane + 1}"
                    onmouseenter={(e) => showTip(e,p.race)}
                    onmousemove={(e) => showTip(e,p.race)}
                    onmouseleave={(e) => tip=null}
                    aria-hidden=true
				>
					<span class="flag">{flag(p.race.country)}</span>
                    {#if Math.min(p.e, end) - Math.max(p.s, start) + 1 >= 3}
                        <span class="name">{p.race.name}</span>
                    {/if}
				</div>
			{/if}
		{/each}
	</div>
{/snippet}



<style>
    .planning {
    margin-top: 35px;
	padding-inline: 2rem;
}

.row {
	display: grid;
	grid-template-columns: 300px 2rem 1fr 2rem;
	column-gap: 0.5rem;
	align-items: stretch;
}

.dates {
	grid-column: 3;
	display: grid;
	grid-template-columns: repeat(var(--days), minmax(0, 1fr));
	font-weight: bold;
	padding-bottom: 4px;
}

.dates span {
	white-space: nowrap;
}

.label {
	grid-column: 1;
	display: flex;
	align-items: center;
	gap: 0.4rem;
	font-weight: bold;
    font-size:30px;
}

.timeline {
        grid-column: 3;
        display: grid;
        grid-template-columns: repeat(var(--days), minmax(0, 1fr));
        grid-template-rows: repeat(var(--rows), 28px);
        background: #262b31;
        background-image: repeating-linear-gradient(
                to bottom,
                transparent 0 27px,
                #ffffff12 27px 28px
        );
        border: 1px solid #555;
}

.day {
	grid-row: 1 / -1;
}

.day.monday {
	border-left: 1px solid #ffffff40;
}

.day.today {
	background: #ffffff18;
}

.race {
	z-index: 1;
	margin: 2px 0;
	padding: 0 4px;
	background: #3d5a8c;
	font-size: 0.8rem;
	line-height: 24px;
	white-space: nowrap;
	overflow: hidden;
    border-right: 3px solid #e0a526;
}

.tooltip {
    position:fixed;
    background:#4B3B61;;
    padding:4px 8px;
    border : 1px solid #555;
    border-radius: 5px;
    box-shadow: 0 4px 12px #0008;
    z-index: 100;
}

.arrow {
	align-self: center;
	width: 2rem;
	height: 2rem;
	border-radius: 50%;
	border: 1px solid #e0a526;
	background: none;
	color: #e0a526;
	font-size: 1.2rem;
	line-height: 1;
	cursor: pointer;
}

.arrow:hover {
	background: #e0a52622;
}


.race.uwt      { background: #5072ac; }
.race.pro      { background: #5b7a2b; }
.race.monument { background: #3d4e9c; }
.race.tdf       { background: #FFD100;
                  color:black; }
.race.vuelta    { background: #FF0000; }
.race.giro     { background: #FF286E; }

.search-row {
	margin-top: 1.5rem;
	margin-bottom: 0.5rem;
}

.search {
	grid-column: 1;
	position: relative;
}

.search input {
	width: 100%;
	padding: 6px 10px;
	box-sizing: border-box;
}

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
	margin-bottom: 30px;
}

.search input {
	margin-top:30px;
	width: 15%;
	padding: 6px 10px;
	box-sizing: border-box;
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


.search ul {
	width: 15%;
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

.rider-row {
	margin: 0;
}

.rider-row + .rider-row .timeline {
	border-top: none;
}

.rider-row .label {
	font-weight: normal;
	border-bottom: 1px solid #ffffff12;
}

.rider-name {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 20px;
}

.close {
	flex-shrink: 0;
	width: 1.4rem;
	padding: 0;
	border: none;
	background: none;
	color: #e0a526;
	font-size: 1.3rem;
	line-height: 1;
	cursor: pointer;
}

.close:hover {
	color: #ff6b6b;
}

.hint {
	color: #888;
	font-weight: normal;
}

@media (max-width: 800px) {
	.planning {
		padding-inline: 1rem;
		overflow-x: auto;
	}

	.row {
		min-width: 800px;
		grid-template-columns: 150px 2rem 1fr 2rem;
	}
}



</style>
