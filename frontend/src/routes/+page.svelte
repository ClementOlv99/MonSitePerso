

<h1> Bienvenue sur mon site </h1>

<section class="grid">

<article class="box">
    <h2> <a href="/info"> Plus Sur Moi </a> </h2>
    <h3> Courte description de la boite</h3>
</article>

<article class="box">
    <h2> Titre </h2>
    <h3> Courte description de la boite</h3>
</article>

<article class="box">
    <h2> Titre </h2>
    <h3> Courte description de la boite</h3>
</article>

<article class="box">
    <h2>  <a href="/calendrier_cycliste">Calendrier</a> </h2>
    <h3> Courte description de la boite</h3>
</article>

<article class="box">
    <h2> Titre </h2>
    <h3> Courte description de la boite</h3>
</article>

<article class="box">
    <h2> Titre </h2>
    <h3> Courte description de la boite</h3>
</article>

</section>

<style>

    @import url('https://googleapis.com');

    .grid {
        display:grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px;
        margin-top: 40px;
    }

    .box {
        border:2px  solid  #ffffff;
        padding:3px;
        border-radius:10px;
    }



 h2, h3 {
        text-align:center;
    }


    h1 {
        text-align:center;
        font-size:50px;
        margin-top:40px;
        
    }

</style>
