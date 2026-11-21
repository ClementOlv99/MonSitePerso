
<script>

import haikyuu from '#lib/assets/haikyuu.jpg';

import Konosuba from '#lib/assets/Konosuba.jpg'
import anohana from '#lib/assets/anohana.jpg'
import sg from '#lib/assets/sg.jpg'
import sc from '#lib/assets/samurai.jpg'
import toradora from "#lib/assets/toradora.jpg"

</script>

<h1 class=bigtitle> Mes Recommendations : </h1>
<h1> Anime : </h1>

<section class="grid"> 


<a href="https://myanimelist.net/anime/9989/Ano_Hi_Mita_Hana_no_Namae_wo_Bokutachi_wa_Mada_Shiranai" target="_blank" rel="noopener noreferrer">
<article class="box">


    <img class=poster src={anohana} alt=anohana>

    <h2> Anohana </h2>

    <h3>  lorem ipsum dolor feur faut que vous regardiez </h3>

    <h4> 9.5/10 </h4>

</article>
</a>

<a href="https://myanimelist.net/anime/20583/Haikyuu" target="_blank" rel="noopener noreferrer">
<article class="box">


    <img class=poster src={sg} alt=sg>

    <h2> Titre </h2>

    <h3>  lorem ipsum dolor feur faut que vous regardiez </h3>

    <h4> note </h4>

    
</article>
</a>

<a href="https://myanimelist.net/anime/20583/Haikyuu" target="_blank" rel="noopener noreferrer">
<article class="box">


    <img class=poster src={sc} alt=sc>

    <h2> Titre </h2>

    <h3>  lorem ipsum dolor feur faut que vous regardiez </h3>

    <h4> note </h4>

</article>
</a>

<a href="https://myanimelist.net/anime/20583/Haikyuu" target="_blank" rel="noopener noreferrer">
<article class="box">


    <img class=poster src={toradora} alt=toradora>

    <h2> Titre </h2>

    <h3>  lorem ipsum dolor feur faut que vous regardiez </h3>

    <h4> note </h4>

</article>
</a>

<a href="https://myanimelist.net/anime/20583/Haikyuu" target="_blank" rel="noopener noreferrer">
<article class="box">

    <img class=poster src={Konosuba} alt=konosuba>

    <h2> Titre </h2>

    <h3>  lorem ipsum dolor feur faut que vous regardiez </h3>

    <h4> note </h4>


</article>
</a>

<a href="https://myanimelist.net/anime/20583/Haikyuu" target="_blank" rel="noopener noreferrer">
<article class="box">

    <img class=poster src={Konosuba} alt=konosuba>

    <h2> Nana </h2>

    <h3>  lorem ipsum dolor feur faut que vous regardiez </h3>

    <h4> note </h4>


</article>
</a>

<a href="https://myanimelist.net/anime/20583/Haikyuu" target="_blank" rel="noopener noreferrer">
<article class="box">

    <img class=poster src={Konosuba} alt=konosuba>

    <h2> Keep your hands of Eizouken </h2>

    <h3>  lorem ipsum dolor feur faut que vous regardiez </h3>

    <h4> note </h4>


</article>
</a>



</section>


<h1> Films : </h1>
<h1> Jeu Vidéo : </h1>
<h1> Autres : </h1>



<style>
    h1, h2 {
        text-align: center;
    }

    h1.bigtitle {
        font-size:50px;
        margin-bottom:40px;
    }

    .grid {
        display:grid;
        grid-template-columns: 1fr 1fr 1fr 1fr ;
        gap: 12px;
        margin-top: 40px;
        margin-left: 40px;
        margin-right: 40px;
        max-width: 1300px;
        margin: 40px auto 0;
        padding: 0 40px;
    }
    .box {
        border:2px  solid  #734F96;
        padding:10px;
        border-radius:10px;
        background: #4B3B61 ;
        position:relative;
        min-height: 600px;
        transition: transform 0.2s ease, border-color 0.5s ease;
        
        }


    .poster {
        display: block;
        margin: 0 auto;
        margin-top:10px;
        height:150px;
        width:auto;
        border-radius:10px;
        border-style: solid;
        border-width: 2px;
        border-color: #734F96;
        transition: border-color 0.5s ease;

    }

    
    .box:hover {
        transform: translateY(-8px);
        border-color: white ; 
    }

    .box:hover .poster {
    border-color:  white;
    }


    .grid a {
    color: #ffffff;
    text-decoration: none;
}

.box h4 {
    position: absolute;
    bottom: 10px;
    left: 10px;
    right: 0;
    margin: 0;
}

.grid a:hover {
    color: #ffffff;
}
</style>