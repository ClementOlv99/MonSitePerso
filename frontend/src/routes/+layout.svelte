<script>
	import qmark from '#lib/assets/qmark.png';
  import '../app.css';
	let { children } = $props();
  import '@fontsource-variable/asap';
  import '@fontsource/varela-round';
  import '@fontsource/lato'; // 400 (Regular)
  import '@fontsource/lato/700.css'; // 700 (Bold)
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



<main>
{@render children()}
</main>



<footer class="bottom-bar">
  <p>© {new Date().getFullYear()} Louis-Clément Olivier</p>
  <nav>
    <a href="https://github.com/ClementOlv99/MonSitePerso" target="_blank" rel="noopener noreferrer">Site entierement fait par moi</a>
  </nav>
</footer>

<style>
        :global(body) {
                background-color: #3B3B3B;
                color: white;
                margin: 0;  
                font-family: 'Lato', sans-serif;
                min-height: 100vh;
                display: flex;
                flex-direction: column;
        }

        :global(a) {
                color: #8ab4f8;  
                transition: color 0.2s ease;
        }
        :global(a:hover) {
          color:#734F96;
        }

        .bottom-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  margin-top: 75px;
  background-color: #4B3B61;   

}

main {
  flex: 1;              
}

.bottom-bar p {
  margin: 0;
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


</style>