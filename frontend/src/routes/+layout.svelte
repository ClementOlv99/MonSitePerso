<script>
	import favicon from '#lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

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

</style>