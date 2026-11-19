<script>
	import qmark from '#lib/assets/qmark.png';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={qmark} />
</svelte:head>

<header class="top-bar">
  <a href="/" class="logo">Accueil</a>

  <e class="Name"> Site de Louis-Clément Olivier </e>
  <nav>
    <a href="/contact">Contact</a>
  </nav>
</header>

{@render children()}

<style>
        :global(body) {
                background-color: #282828;
                color: white;
                margin: 0;  
        }

        :global(a) {
                color: #8ab4f8;  
        }

        
  .top-bar {
    position: sticky;
    top: 0;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 1rem 2rem;
    background-color: #4B3B61;
    color: white;
    z-index: 50;
  }

    nav {
    display: flex;
    gap: 1.5rem;
  }

  a {
    color: inherit;
    text-decoration: none;
  }

</style>