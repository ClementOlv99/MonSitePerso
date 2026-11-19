<script>
  import { page } from '$app/state'; // SvelteKit ≥ 2.12; on older versions use '$app/stores' and $page
</script>

{#if page.status === 404}
  <h1>404 Cette Page n'existe pas</h1>

  <a href="/">Back to home</a>

  <a href="/secret-game">Tu as trouvé mon jeu secret !</a>
{:else}
  <h1>{page.status}</h1>
  <p>{page.error?.message}</p>
{/if}

<style>

  h1 {
    margin-left:30px;
  }
 
    a {
        margin-top: 100px;
        margin-left: 30px;
        display: block;
    }
</style>