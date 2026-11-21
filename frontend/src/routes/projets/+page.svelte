<script>
    let {data} = $props();

    const rtf = new Intl.RelativeTimeFormat('fr', {numeric : 'auto'})

    function timeAgo(iso) {
	    const days = Math.round((new Date(iso) - Date.now()) / 86_400_000);
	    if (days > -30) return rtf.format(days, 'day');
	    if (days > -365) return rtf.format(Math.round(days / 30), 'month');
	    return rtf.format(Math.round(days / 365), 'year');
}
</script>

<main class="projets">
	<h1> Mes projets récents </h1>
    <div class="grid">
        {#each data.projects as project (project.id)}
        <a class="card" href={project.url} target="_blank" rel="noopener noreferrer">
			<h2>{project.name}</h2>
			<p class="description">{project.description ?? 'Pas de description'}</p>
            <div class="meta">
	            {#if project.language}
		            <span>{project.language}</span>
	            {/if}
                |
	            <span class="date">{timeAgo(project.pushedAt)}</span>
</div>
		</a>
        {:else}
            {#if data.failed}
	            <p class="hint">Impossible de charger les projets depuis GitHub pour le moment.</p>
            {/if}
        {/each}
    </div>
    <h3> <a href="https://github.com/ClementOlv99?tab=repositories">Si vous voulez voir le reste des projets</a> </h3>
</main>




<style>
	.projets {
		max-width: 1100px;
		margin: 0 auto;
		padding: 2rem;
	}

    .grid {
        margin-top: 50px;
        display:grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: 1rem
    }

    .card {

	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	padding: 1rem 1.25rem;
	border: 2px solid #734F96;
	border-radius: 8px;
	background: #4B3B61;
	color: inherit;
	text-decoration: none;
	transition: border-color 0.15s, transform 0.15s;
    min-height: 150px;
}

h1 {
    margin-top: 25px;
    text-align: center;
    font-size:50px;
}

.card:hover {
	border-color: white;
	transform: translateY(-4px);
}

.card h2 {
	margin: 0;
	font-size: 1.1rem;
	color: #8ab4f8;
	overflow-wrap: anywhere;
}

h2, p {
    text-align: center;
}

h3 {
    margin-top:50px;
    text-align:center;
    font-size:150%;
}

.description {
	margin: 0;
	color: #ccc;
	flex-grow: 1;
}
</style>