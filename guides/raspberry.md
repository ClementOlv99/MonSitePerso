# Intégrer « Guess the MTG Card » dans MonSitePerso (onglet `/cardguesser`)

Ce guide porte le jeu de `guess-the-mtg-card/web/` (HTML + JS + Supabase, hébergé sur GitHub Pages)
dans ton site SvelteKit + Spring Boot, avec la base H2 à la place de Supabase.

---

## 0. Ce qui change, et pourquoi

| | Avant (GitHub Pages) | Après (ton site) |
|---|---|---|
| Liste des cartes | `cardlist.json` (10 Mo, 29 743 lignes) **téléchargé en entier** par chaque navigateur | Chargé une fois dans H2 au démarrage de Spring. Le navigateur demande **une carte à la fois** (~300 octets) |
| Leaderboard | Table `scores` sur Supabase, appelée depuis le navigateur avec la clé publique | Table `mtg_scores` dans H2, via `GET/POST /api/scores` |
| Seed | Numéro de ligne dans `cardlist.json` | Identique : colonne `seed` = numéro de ligne. **Les anciens seeds restent valides** |
| Page | `web/index.html` + `game.js` + `style.css` | Un seul composant `frontend/src/routes/cardguesser/+page.svelte` |
| Images du cadre | `../assets/frame.png`, `../assets/ncframe.jpg` | `frontend/src/lib/assets/mtg/`, importées comme `qmark.png` dans ton layout |

La logique du jeu (indices, score `100 + 10 × indices restants`, série de victoires, envoi du score
à la défaite) est **inchangée**.

Arborescence finale des fichiers ajoutés :

```
backend/src/main/java/Siteweb/backend/
├── card/
│   ├── CardEntity.java       table mtg_cards
│   ├── CardRepository.java
│   ├── CardDTO.java          JSON envoyé au navigateur
│   ├── CardLine.java         une ligne de cardlist.json
│   ├── CardService.java
│   ├── CardLoader.java       import du fichier au démarrage
│   └── CardController.java   GET /api/cards/...
└── score/
    ├── ScoreEntity.java      table mtg_scores
    ├── ScoreRepository.java
    ├── ScoreDTO.java
    ├── ScoreService.java     validation
    └── ScoreController.java  GET/POST /api/scores
backend/src/main/resources/mtg/cardlist.json

frontend/src/lib/assets/mtg/frame.png
frontend/src/lib/assets/mtg/ncframe.jpg
frontend/src/routes/cardguesser/+page.svelte   (remplace le fichier vide)
frontend/vite.config.js                         (modifié : autoriser POST /api/scores)
```

> **Statut des tests.** Le frontend a été testé de bout en bout dans Chrome (chargement, lettres,
> devinette, série, envoi du score, leaderboard, seed, mobile, build de production) contre un faux
> backend qui expose exactement la même API. Le code Java a été vérifié à la compilation (syntaxe et
> types), mais **pas lancé avec le vrai Spring Boot 4** : je n'avais pas accès aux dépendances Maven.
> Le point le plus susceptible de casser est l'import Jackson, voir l'encadré de l'étape B6.

---

# Partie A — Préparer les fichiers

## A1. Copier la liste des cartes dans le backend

Depuis le dossier qui contient les deux dépôts :

```bash
mkdir -p MonSitePerso/backend/src/main/resources/mtg
cp guess-the-mtg-card/cardlist.json MonSitePerso/backend/src/main/resources/mtg/
```

Tout ce qui est dans `src/main/resources/` est embarqué dans l'application Spring : le fichier sera
lisible depuis le code avec le chemin `mtg/cardlist.json`.

> Le nom est trompeur : ce n'est pas un tableau JSON mais du **JSONL** (un objet JSON par ligne),
> produit par ton `process_cardlist.py`. Le code d'import en tient compte.

## A2. Copier les images du cadre dans le frontend

```bash
mkdir -p MonSitePerso/frontend/src/lib/assets/mtg
cp guess-the-mtg-card/assets/frame.png guess-the-mtg-card/assets/ncframe.jpg \
   MonSitePerso/frontend/src/lib/assets/mtg/
```

---

# Partie B — Backend (Spring Boot + H2)

Même organisation que tes packages `race`, `rider` et `participation` : Entity → Repository →
Service → Controller.

## B1. L'entité carte

`backend/src/main/java/Siteweb/backend/card/CardEntity.java`

```java
package Siteweb.backend.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mtg_cards")
public class CardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Line number in cardlist.json (0, 1, 2...): this is the "seed" shown in the game
    @Column(nullable = false, unique = true)
    private Integer seed;

    @Column(nullable = false)
    private String name;

    private String manaCost;

    @Column(nullable = false)
    private String typeLine;

    // Some rules texts are longer than the default 255 characters (longest: ~630)
    @Column(length = 1000)
    private String oracleText;

    private String power;       // null for non-creatures
    private String toughness;   // null for non-creatures
    private String rarity;

    protected CardEntity() {
    }

    public CardEntity(int seed, String name, String manaCost, String typeLine, String oracleText,
                      String power, String toughness, String rarity) {
        this.seed = seed;
        this.name = name;
        this.manaCost = manaCost;
        this.typeLine = typeLine;
        this.oracleText = oracleText;
        this.power = power;
        this.toughness = toughness;
        this.rarity = rarity;
    }

    public Long getId() { return id; }
    public Integer getSeed() { return seed; }
    public String getName() { return name; }
    public String getManaCost() { return manaCost; }
    public String getTypeLine() { return typeLine; }
    public String getOracleText() { return oracleText; }
    public String getPower() { return power; }
    public String getToughness() { return toughness; }
    public String getRarity() { return rarity; }
}
```

- On garde un `id` auto-généré comme dans `RaceEntity`, et le **seed** dans une colonne à part,
  `unique` (ce qui crée un index : la recherche par seed est instantanée).
- `@Column(length = 1000)` sur `oracleText` : par défaut une colonne `String` fait 255 caractères
  dans H2, et le texte de règles le plus long de ta liste en fait environ 630. Sans ça, l'import
  échoue avec `Value too long for column`.

## B2. Le repository

`backend/src/main/java/Siteweb/backend/card/CardRepository.java`

```java
package Siteweb.backend.card;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardRepository extends JpaRepository<CardEntity, Long> {

    Optional<CardEntity> findBySeed(int seed);
}
```

`findBySeed` est une requête dérivée : Spring écrit le SQL tout seul à partir du nom de la méthode,
comme pour `findAllByOrderByStartDateAsc` dans `RaceRepository`.

## B3. Le DTO envoyé au navigateur

`backend/src/main/java/Siteweb/backend/card/CardDTO.java`

```java
package Siteweb.backend.card;

// What the frontend receives: GET /api/cards/random, GET /api/cards/{seed}
public record CardDTO(
        int seed,
        String name,
        String manaCost,
        String typeLine,
        String oracleText,
        String power,
        String toughness,
        String rarity) {

    public static CardDTO from(CardEntity card) {
        return new CardDTO(card.getSeed(), card.getName(), card.getManaCost(), card.getTypeLine(),
                card.getOracleText(), card.getPower(), card.getToughness(), card.getRarity());
    }
}
```

Spring transforme ce record en JSON avec les noms des champs :
`{"seed": 5, "name": "Walking Sponge", "manaCost": "{1}{U}", "typeLine": "Creature — Sponge", ...}`.

## B4. Le format d'une ligne de `cardlist.json`

`backend/src/main/java/Siteweb/backend/card/CardLine.java`

```java
package Siteweb.backend.card;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// One line of cardlist.json, e.g.
// {"name": "Storm Crow", "mana_cost": "{1}{U}", "type_line": "Creature — Bird", ...}
// The file uses snake_case, @JsonProperty maps it to Java names.
// "legalities" is in the file but not needed: ignoreUnknown skips it.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardLine(
        String name,
        @JsonProperty("mana_cost") String manaCost,
        @JsonProperty("type_line") String typeLine,
        @JsonProperty("oracle_text") String oracleText,
        String power,
        String toughness,
        String rarity) {
}
```

Le fichier utilise `mana_cost`, `type_line`, etc. `@JsonProperty` fait la correspondance avec des
noms Java en camelCase.

## B5. Le service

`backend/src/main/java/Siteweb/backend/card/CardService.java`

```java
package Siteweb.backend.card;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.transaction.Transactional;

@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public long count() {
        return cardRepository.count();
    }

    // Seeds go from 0 to count-1 without gaps (see CardLoader), so any number in that range exists
    public CardDTO random() {
        long count = cardRepository.count();
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No cards loaded");
        }
        int seed = ThreadLocalRandom.current().nextInt((int) count);
        return bySeed(seed);
    }

    public CardDTO bySeed(int seed) {
        return cardRepository.findBySeed(seed)
                .map(CardDTO::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No card with seed " + seed));
    }

    // Delete + insert in one transaction: if the insert fails, the old cards are kept
    @Transactional
    public void replaceAll(List<CardEntity> cards) {
        cardRepository.deleteAllInBatch();
        cardRepository.saveAll(cards);
    }
}
```

- **Tirage aléatoire** : on prend un nombre entre `0` et `count - 1`, puis la carte qui a ce seed.
  Ça marche parce que l'import numérote les cartes sans trou.
- `ResponseStatusException` : Spring la transforme en vraie réponse HTTP (404, 503) au lieu d'une
  erreur 500.
- `replaceAll` est `@Transactional` (même annotation que dans `RaceService`) : si l'insertion plante
  au milieu, la suppression est annulée aussi, et tu ne te retrouves pas avec une table vide.

## B6. L'import au démarrage

`backend/src/main/java/Siteweb/backend/card/CardLoader.java`

```java
package Siteweb.backend.card;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Runs once at every backend start: copies src/main/resources/mtg/cardlist.json
 * (one JSON card per line) into the mtg_cards table.
 * If the table already holds as many cards as the file has lines, nothing is done.
 */
@Component
public class CardLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CardLoader.class);
    private static final String CARDLIST = "mtg/cardlist.json";

    private final CardRepository cardRepository;
    private final CardService cardService;
    private final ObjectMapper objectMapper;

    // ObjectMapper is the JSON reader Spring already uses for the REST API
    public CardLoader(CardRepository cardRepository, CardService cardService, ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.cardService = cardService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClassPathResource file = new ClassPathResource(CARDLIST);
        if (!file.exists()) {
            log.warn("{} not found: the card game will have no cards", CARDLIST);
            return;
        }

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(line -> !line.isBlank()).toList();
        } catch (IOException e) {
            log.error("Could not read {}", CARDLIST, e);
            return;
        }

        if (cardRepository.count() == lines.size()) {
            log.info("{} cards already in the database", lines.size());
            return;
        }

        List<CardEntity> cards = new ArrayList<>(lines.size());
        for (int seed = 0; seed < lines.size(); seed++) {
            CardLine c = objectMapper.readValue(lines.get(seed), CardLine.class);
            cards.add(new CardEntity(seed, c.name(), c.manaCost(), c.typeLine(), c.oracleText(),
                    c.power(), c.toughness(), c.rarity()));
        }

        log.info("Importing {} cards, this takes a moment the first time...", cards.size());
        cardService.replaceAll(cards);
        log.info("{} cards imported", cards.size());
    }
}
```

- Un `ApplicationRunner` est exécuté automatiquement par Spring **une fois l'application démarrée**.
  Pas besoin de script Python comme pour les courses.
- Si la table contient déjà autant de cartes que le fichier a de lignes, il ne fait rien : les
  démarrages suivants sont instantanés.
- Si le fichier est absent, il écrit un avertissement et le reste du site démarre quand même.

> ⚠️ **Jackson 2 ou 3.** Spring Boot 4 (tu es en 4.1.2) utilise Jackson 3, dont le package est
> `tools.jackson.databind`. Si l'import ci-dessus est souligné en rouge
> (`package tools.jackson.databind does not exist`), ton projet est en Jackson 2 : remplace la ligne par
>
> ```java
> import com.fasterxml.jackson.databind.ObjectMapper;
> ```
>
> et, comme `readValue` lève alors une exception vérifiée, ajoute `throws Exception` à la méthode :
> `public void run(ApplicationArguments args) throws Exception {`.
> Les annotations de `CardLine` (`com.fasterxml.jackson.annotation`) sont les mêmes dans les deux
> versions.

> **Premier démarrage.** L'import des 29 743 cartes prend quelques secondes à quelques dizaines de
> secondes sur la Pi. Comme `spring.jpa.show-sql=true` est activé, `backend.log` va recevoir ~30 000
> lignes `insert into mtg_cards...` cette fois-là. C'est normal et ça n'arrive qu'une fois.

## B7. Le contrôleur cartes

`backend/src/main/java/Siteweb/backend/card/CardController.java`

```java
package Siteweb.backend.card;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    // GET http://localhost:8080/api/cards/random
    @GetMapping("/random")
    public CardDTO random() {
        return cardService.random();
    }

    // GET http://localhost:8080/api/cards/count  ->  {"count": 29743}
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", cardService.count());
    }

    // GET http://localhost:8080/api/cards/1234  ("Set Card by Seed" button)
    @GetMapping("/{seed}")
    public CardDTO bySeed(@PathVariable int seed) {
        return cardService.bySeed(seed);
    }
}
```

`/random` et `/count` ne sont pas confondus avec `/{seed}` : Spring privilégie toujours un chemin
fixe à un chemin avec variable. `/api/cards/abc` renvoie 400 (pas un nombre).

Pas de `@CrossOrigin` : comme pour `RaceController`, tout passe par le proxy Vite, donc même origine.

## B8. Le leaderboard : entité, repository, DTO

`backend/src/main/java/Siteweb/backend/score/ScoreEntity.java`

```java
package Siteweb.backend.score;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Replaces the "scores" table that was on Supabase
@Entity
@Table(name = "mtg_scores")
public class ScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String username;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private Instant createdAt;

    protected ScoreEntity() {
    }

    public ScoreEntity(String username, int score) {
        this.username = username;
        this.score = score;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public int getScore() { return score; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`backend/src/main/java/Siteweb/backend/score/ScoreRepository.java`

```java
package Siteweb.backend.score;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScoreRepository extends JpaRepository<ScoreEntity, Long> {

    // 10 best scores; on a tie, the oldest one comes first
    List<ScoreEntity> findTop10ByOrderByScoreDescCreatedAtAsc();
}
```

Équivalent de la requête Supabase `.order('score', { ascending: false }).limit(10)`.

`backend/src/main/java/Siteweb/backend/score/ScoreDTO.java`

```java
package Siteweb.backend.score;

// Sent by the game (POST /api/scores) and returned in the leaderboard (GET /api/scores)
public record ScoreDTO(String username, int score) {
}
```

Mêmes noms de champs (`username`, `score`) que l'ancienne table Supabase.

## B9. Le service et le contrôleur scores

`backend/src/main/java/Siteweb/backend/score/ScoreService.java`

```java
package Siteweb.backend.score;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScoreService {

    private static final int MAX_NAME_LENGTH = 24;     // same as maxlength in the page
    private static final int MAX_SCORE = 1_000_000;    // just a sanity limit

    private final ScoreRepository scoreRepository;

    public ScoreService(ScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    public List<ScoreDTO> leaderboard() {
        return scoreRepository.findTop10ByOrderByScoreDescCreatedAtAsc().stream()
                .map(s -> new ScoreDTO(s.getUsername(), s.getScore()))
                .toList();
    }

    public ScoreDTO save(ScoreDTO dto) {
        String username = dto.username() == null ? "" : dto.username().trim();

        if (username.isEmpty() || username.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "username must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        // The game only gives 100 + 10 * hints left per card: always a positive multiple of 10
        if (dto.score() <= 0 || dto.score() > MAX_SCORE || dto.score() % 10 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid score");
        }

        ScoreEntity saved = scoreRepository.save(new ScoreEntity(username, dto.score()));
        return new ScoreDTO(saved.getUsername(), saved.getScore());
    }
}
```

`backend/src/main/java/Siteweb/backend/score/ScoreController.java`

```java
package Siteweb.backend.score;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    // GET http://localhost:8080/api/scores  ->  top 10
    @GetMapping
    public List<ScoreDTO> leaderboard() {
        return scoreService.leaderboard();
    }

    // POST http://localhost:8080/api/scores  with  {"username": "Clément", "score": 320}
    @PostMapping
    public ResponseEntity<ScoreDTO> save(@RequestBody ScoreDTO score) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scoreService.save(score));
    }
}
```

> **Sur la triche.** La validation refuse les données absurdes (nom vide, score négatif ou qui
> n'est pas un multiple de 10). Elle n'empêche pas quelqu'un d'envoyer un faux score à la main avec
> `curl`. C'était déjà possible avec Supabase et la clé publique dans `game.js` : le score est
> calculé dans le navigateur, donc le serveur ne peut pas le vérifier. L'empêcher vraiment
> demanderait de faire tourner la partie côté serveur (le serveur garde la carte et compte les
> lettres). C'est un chantier à part, pas nécessaire pour un jeu entre amis.

## B10. Tester le backend seul

```bash
make backend_noscrape
```

Dans `backend.log` (ou le terminal), au premier démarrage :

```
Importing 29743 cards, this takes a moment the first time...
29743 cards imported
```

Puis dans un autre terminal :

```bash
curl http://127.0.0.1:8080/api/cards/count          # {"count":29743}
curl http://127.0.0.1:8080/api/cards/5              # Walking Sponge
curl http://127.0.0.1:8080/api/cards/random
curl http://127.0.0.1:8080/api/scores               # []
curl -X POST -H "Content-Type: application/json" \
     -d '{"username":"test","score":210}' http://127.0.0.1:8080/api/scores
curl http://127.0.0.1:8080/api/scores               # [{"username":"test","score":210}]
curl -i -X POST -H "Content-Type: application/json" \
     -d '{"username":"test","score":7}' http://127.0.0.1:8080/api/scores   # 400
```

> Le score `test` restera dans le leaderboard. Pour l'enlever, voir « Remettre le leaderboard à
> zéro » à la fin.

---

# Partie C — Frontend (SvelteKit)

## C1. Laisser passer l'envoi des scores dans le proxy Vite

Avec la configuration du guide précédent, **toute requête autre que `GET` est bloquée** par le proxy
(pour protéger les `POST` du scraper). L'envoi d'un score étant un `POST /api/scores`, il faut
l'autoriser, et seulement lui.

Dans `frontend/vite.config.js`, remplace la ligne `bypass` par :

```js
				// Lectures autorisées partout ; en écriture, seul l'envoi de score du jeu passe.
				// Les POST du scraper (/api/races, /api/participation) restent internes à la Pi.
				bypass: (req) => {
					if (req.method === 'GET') return undefined;
					if (req.method === 'POST' && req.url === '/api/scores') return undefined;
					return false; // false = Vite répond 404
				}
```

Pour la mise en ligne publique plus tard, la même règle devra être recopiée dans le reverse proxy
(Caddy ou nginx) : `GET /api/*` et `POST /api/scores` autorisés, le reste refusé.

## C2. La page du jeu

Remplace le contenu (vide) de `frontend/src/routes/cardguesser/+page.svelte` par le code ci-dessous.
Le fichier complet est aussi fourni à part (`+page.svelte`) pour éviter les erreurs de copier-coller.

Correspondance avec l'ancien code :

| `game.js` / `index.html` | `+page.svelte` |
|---|---|
| `window.supabase.createClient(...)` + script CDN | supprimés |
| `supabaseClient.from('scores').insert(...)` | `fetch('/api/scores', { method: 'POST' ... })` |
| `supabaseClient.from('scores').select(...)` | `fetch('/api/scores')` |
| `fetch('../cardlist.json')` + `split('\n')` | `fetch('/api/cards/random')`, `/api/cards/{seed}`, `/api/cards/count` |
| `document.getElementById(...)` et `.textContent = ...` | variables `$state`, affichées avec `{...}` dans le HTML |
| appels manuels à `drawCard()` après chaque changement | un `$effect` qui redessine dès que `card` ou `revealed` change |
| `guessModal.style.display = 'flex'` | `{#if guessOpen}` |
| `init()` sur `DOMContentLoaded` | `onMount` (s'exécute uniquement dans le navigateur, nécessaire pour `localStorage` et le canvas) |
| `../assets/frame.png` | `import frameSrc from '#lib/assets/mtg/frame.png'` |
| `style.css` global (`body`, `button`, `* { margin: 0 }`) | `<style>` du composant : Svelte le limite à cette page, donc ça ne casse pas le reste du site |
| leaderboard en `position: fixed`, overlay de chargement plein écran | grille dans la page, overlay limité à la carte (sinon ils passaient par-dessus ta barre du haut) |

### Le script

```svelte
<script>
	import { onMount, tick } from 'svelte';
	import { PUBLIC_API_URL } from '$app/env/public';
	import frameSrc from '#lib/assets/mtg/frame.png';
	import ncFrameSrc from '#lib/assets/mtg/ncframe.jpg';

	// ── Constants ─────────────────────────────────────────────────────────────

	const HINTS_MAX = 11;
	const PLAYER_KEY = 'mtg-player-name';
	const FIELDS = ['name', 'manacost', 'type', 'power', 'toughness', 'ruletext'];

	// Where each text goes on the frame, as a fraction of the image size
	const C = {
		creature: { nameY: 0.085, typeY: 0.6, oraY: 0.64, oraBotY: 0.942, ptX: 0.875, ptY: 0.92 },
		nonCreature: { nameY: 0.085, typeY: 0.595, oraY: 0.638, oraBotY: 0.938 },
		textLeft: 0.092,
		textRight: 0.908
	};

	// ── State ─────────────────────────────────────────────────────────────────

	let canvas = $state(); // <canvas> element (bind:this)
	let letterInput = $state(); // <input> element (bind:this)
	let frames = $state(null); // { creature, nonCreature } once both images are loaded

	let card = $state(null);
	let revealed = $state({}); // same fields as card, as arrays of characters ('_' = hidden)
	let hintsLeft = $state(HINTS_MAX);
	let score = $state(0);
	let over = $state(false);
	let won = $state(false);
	let letters = $state(''); // content of the letter input
	let usedLetters = new Set(); // not displayed: no need for $state
	let runSaved = false;

	let cardCount = $state(0);
	let loading = $state(true);
	let loadError = $state('');

	let guessOpen = $state(false);
	let guessText = $state('');
	let result = $state(null); // null | 'correct' | 'wrong'

	let playerName = $state('');
	let nameInput = $state('');
	let leaderboard = $state([]);

	// ── API (Spring Boot, through the Vite proxy) ─────────────────────────────

	async function getJson(path, options) {
		const res = await fetch(`${PUBLIC_API_URL}${path}`, options);
		if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
		return res.json();
	}

	// CardDTO from Java -> the object the game works with
	function toCard(dto) {
		const type = dto.typeLine ?? '';
		return {
			id: dto.seed,
			name: dto.name ?? '',
			manacost: dto.manaCost ?? '',
			type,
			ruletext: dto.oracleText ?? '',
			power: dto.power ?? '',
			toughness: dto.toughness ?? '',
			rarity: dto.rarity ?? '',
			url: 'https://scryfall.com/search?q=' + encodeURIComponent(dto.name ?? ''),
			isCreature: type.includes('Creature')
		};
	}

	async function loadRandom() {
		return toCard(await getJson('/cards/random'));
	}

	async function loadBySeed(seed) {
		const res = await fetch(`${PUBLIC_API_URL}/cards/${seed}`);
		if (res.status === 404) return null;
		if (!res.ok) throw new Error(`/cards/${seed}: HTTP ${res.status}`);
		return toCard(await res.json());
	}

	async function loadLeaderboard() {
		try {
			leaderboard = await getJson('/scores');
		} catch (err) {
			console.error(err);
		}
	}

	async function saveScore(value) {
		if (!playerName || value <= 0) return;
		try {
			await getJson('/scores', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ username: playerName, score: value })
			});
			await loadLeaderboard();
		} catch (err) {
			console.error(err);
		}
	}

	function finalizeRunIfNeeded() {
		if (!runSaved && score > 0) {
			runSaved = true;
			void saveScore(score);
		}
	}

	function savePlayerName() {
		const value = nameInput.trim();
		if (!value) return;
		playerName = value;
		localStorage.setItem(PLAYER_KEY, playerName);
	}

	// ── Game logic ────────────────────────────────────────────────────────────

	function hide(text) {
		return Array.from(text).map((c) => (/[a-zA-Z0-9]/.test(c) ? '_' : c));
	}

	async function startCard(c) {
		card = c;
		hintsLeft = HINTS_MAX;
		over = false;
		usedLetters = new Set();
		runSaved = false;
		letters = '';
		revealed = Object.fromEntries(FIELDS.map((f) => [f, hide(c[f])]));
		await tick(); // wait for the input to be enabled again before focusing it
		letterInput?.focus();
	}

	function endGame(correct) {
		if (over) return;
		over = true;
		won = correct;
		if (correct) score += 100 + 10 * hintsLeft;
		hintsLeft = 0;
		revealed = Object.fromEntries(FIELDS.map((f) => [f, Array.from(card[f])]));
	}

	function revealLetter(ch) {
		if (over || usedLetters.has(ch) || hintsLeft <= 0) return;
		usedLetters.add(ch);
		hintsLeft--;

		for (const f of FIELDS) {
			const orig = Array.from(card[f]);
			revealed[f] = revealed[f].map((shown, i) =>
				shown === '_' && orig[i].toLowerCase() === ch ? orig[i] : shown
			);
		}

		if (hintsLeft === 0) openGuess();
	}

	function onLetterInput(e) {
		const ch = (e.data ?? e.currentTarget.value).slice(-1).toLowerCase();
		if (/^[a-z0-9]$/.test(ch)) revealLetter(ch);
	}

	function openGuess() {
		guessText = '';
		result = null;
		guessOpen = true;
	}

	function closeGuess() {
		guessOpen = false;
	}

	function submitGuess() {
		const answer = guessText.trim();
		if (!answer) return;
		const correct = answer.toLowerCase() === card.name.toLowerCase();
		if (!correct) finalizeRunIfNeeded();
		endGame(correct);
		result = correct ? 'correct' : 'wrong';
		setTimeout(closeGuess, 1500);
	}

	async function newCard() {
		if (!won && over && score > 0) finalizeRunIfNeeded();
		if (!won) score = 0;
		won = false;
		try {
			await startCard(await loadRandom());
		} catch (err) {
			console.error(err);
			alert('Could not load a card.');
		}
	}

	async function chooseSeed() {
		const input = prompt(`Enter seed (0 – ${cardCount - 1}):`);
		if (input === null) return;
		const seed = parseInt(input.trim(), 10);
		if (isNaN(seed)) {
			alert('Please enter a valid number.');
			return;
		}
		const c = await loadBySeed(seed);
		if (!c) {
			alert('Invalid seed.');
			return;
		}
		score = 0;
		won = false;
		await startCard(c);
	}

	function showAnswer() {
		if (!over) finalizeRunIfNeeded();
		won = false;
		endGame(false);
	}

	// ── Drawing ───────────────────────────────────────────────────────────────

	function withSpacing(chars) {
		let out = '';
		for (let i = 0; i < chars.length; i++) {
			out += chars[i];
			if (chars[i] === ' ' && i < chars.length - 1) out += '  ';
			else if (chars[i] !== '' && i < chars.length - 1) out += ' ';
		}
		return out;
	}

	function drawWrapped(ctx, text, x, y, maxW, botY, fontSize) {
		const lh = fontSize * 1.35;
		let cy = y + fontSize;
		for (const para of text.split('\n')) {
			if (cy > botY) return;
			let line = '';
			for (const word of para.split(' ')) {
				const test = line ? line + ' ' + word : word;
				if (ctx.measureText(test).width > maxW && line) {
					ctx.fillText(line, x, cy);
					cy += lh;
					if (cy > botY) return;
					line = word;
				} else {
					line = test;
				}
			}
			if (line) {
				ctx.fillText(line, x, cy);
				cy += lh;
			}
		}
	}

	function drawCard() {
		const ctx = canvas.getContext('2d');
		const img = card.isCreature ? frames.creature : frames.nonCreature;
		const pw = canvas.width;
		const ph = canvas.height;
		ctx.clearRect(0, 0, pw, ph);

		const aspect = img.naturalWidth / img.naturalHeight;
		let iw, ih;
		if (pw / ph > aspect) {
			ih = ph;
			iw = ih * aspect;
		} else {
			iw = pw;
			ih = iw / aspect;
		}
		const ix = (pw - iw) / 2;
		const iy = (ph - ih) / 2;
		ctx.drawImage(img, ix, iy, iw, ih);

		const co = card.isCreature ? C.creature : C.nonCreature;
		const tl = ix + iw * C.textLeft;
		const tr = ix + iw * C.textRight;
		const sz = Math.max(10, iw / 30);
		const oraSize = Math.max(8, sz - 4);

		ctx.fillStyle = '#000';

		ctx.font = `bold ${sz}px Arial`;
		ctx.fillText(withSpacing(revealed.name), tl, iy + ih * co.nameY);

		ctx.font = `${Math.max(8, sz - 1)}px Arial`;
		const mana = withSpacing(revealed.manacost);
		ctx.fillText(mana, tr - ctx.measureText(mana).width, iy + ih * co.nameY);
		ctx.fillText(withSpacing(revealed.type), tl, iy + ih * co.typeY);

		ctx.font = `${oraSize}px Arial`;
		drawWrapped(ctx, withSpacing(revealed.ruletext), tl, iy + ih * co.oraY, tr - tl, iy + ih * co.oraBotY, oraSize);

		if (card.isCreature) {
			ctx.font = `bold ${sz}px Arial`;
			const pt = withSpacing(revealed.power) + '/' + withSpacing(revealed.toughness);
			ctx.fillText(pt, ix + iw * C.creature.ptX - ctx.measureText(pt).width / 2, iy + ih * C.creature.ptY);
		}
	}

	// Redraws automatically whenever card or revealed changes
	$effect(() => {
		if (canvas && frames && card) drawCard();
	});

	// ── Start-up (browser only) ───────────────────────────────────────────────

	function loadImage(src) {
		return new Promise((resolve, reject) => {
			const img = new Image();
			img.onload = () => resolve(img);
			img.onerror = () => reject(new Error(`could not load ${src}`));
			img.src = src;
		});
	}

	function focusOnMount(node) {
		node.focus();
	}

	onMount(async () => {
		playerName = nameInput = localStorage.getItem(PLAYER_KEY) ?? '';
		loadLeaderboard();

		try {
			const [creature, nonCreature, first, count] = await Promise.all([
				loadImage(frameSrc),
				loadImage(ncFrameSrc),
				loadRandom(),
				getJson('/cards/count')
			]);
			frames = { creature, nonCreature };
			cardCount = count.count;
			loading = false;
			await startCard(first);
		} catch (err) {
			console.error(err);
			loadError = 'Failed to load card data. ' + err.message;
		}
	});
</script>
```

Points à noter :

- `PUBLIC_API_URL` vaut `/api` (guide précédent) : les appels passent par le proxy Vite, donc ça
  marche depuis `localhost:5173` comme depuis `192.168.1.17:5173`.
- `usedLetters` n'est pas un `$state` parce qu'il n'est jamais affiché : inutile que Svelte le
  surveille.
- `Array.from(texte)` découpe en caractères. C'est plus sûr que `texte[i]` (utilisé dans l'ancien
  code) pour les caractères spéciaux.
- `await tick()` dans `startCard` : on attend que Svelte ait réactivé le champ de saisie avant de
  lui donner le focus (un champ `disabled` ne peut pas recevoir le focus).

### Le HTML

```svelte
<svelte:head>
	<title>MTG Card Guesser</title>
</svelte:head>

<div class="mtg">
	<aside class="leaderboard">
		<h2>Leaderboard</h2>

		<div class="player-row">
			<input
				bind:value={nameInput}
				onkeydown={(e) => e.key === 'Enter' && savePlayerName()}
				maxlength="24"
				placeholder="Enter your name"
				aria-label="Player name"
			/>
			<button class="gold" onclick={savePlayerName}>Save</button>
		</div>
		<p class="player-status">
			{playerName ? `Playing as ${playerName}` : 'Set a name to appear on the leaderboard.'}
		</p>

		<ol>
			{#each leaderboard as row, i (i)}
				<li><span>{i + 1}. {row.username}</span><span>{row.score}</span></li>
			{:else}
				<li class="empty">No scores yet</li>
			{/each}
		</ol>
	</aside>

	<div class="game">
		<h1>MTG Card Guesser</h1>

		<div class="canvas-wrap">
			<canvas bind:this={canvas} width="500" height="620"></canvas>
			{#if loading || loadError}
				<div class="overlay">
					{#if loadError}
						<p>{loadError}</p>
					{:else}
						<div class="spinner"></div>
						<p>Loading card data…</p>
					{/if}
				</div>
			{/if}
		</div>

		<div class="stats">
			<span class="hints">Hints left: {hintsLeft}</span>
			<span class="score">Score: {score}</span>
			<span class="seed">Seed: {card?.id ?? '—'}</span>
		</div>

		<input
			bind:this={letterInput}
			bind:value={letters}
			oninput={onLetterInput}
			disabled={!card || over || hintsLeft === 0}
			class="letter-input"
			autocomplete="off"
			spellcheck="false"
			placeholder="Type a letter to reveal…"
			aria-label="Letter to reveal"
		/>

		<div class="buttons">
			<button class="gold" onclick={() => !over && openGuess()} disabled={!card}>Guess Answer</button>
			<button onclick={newCard} disabled={!card}>New Random Card</button>
			<button onclick={chooseSeed} disabled={!card}>Set Card by Seed</button>
			<button onclick={showAnswer} disabled={!card}>Show Answer</button>
		</div>

		{#if over}
			<a class="scryfall" href={card.url} target="_blank" rel="noopener">See on Scryfall ↗</a>
		{/if}
	</div>
</div>

{#if guessOpen}
	<!-- Clicking the dark background closes the modal (Escape does too) -->
	<!-- svelte-ignore a11y_click_events_have_key_events -->
	<div class="backdrop" role="presentation" onclick={(e) => e.target === e.currentTarget && closeGuess()}>
		<div class="modal" role="dialog" aria-modal="true" aria-label="Guess the card name">
			<p>Enter the card name:</p>
			<input
				use:focusOnMount
				bind:value={guessText}
				onkeydown={(e) => {
					if (e.key === 'Enter') submitGuess();
					if (e.key === 'Escape') closeGuess();
				}}
				autocomplete="off"
				spellcheck="false"
				placeholder="Card name…"
			/>
			<div class="modal-buttons">
				<button class="gold" onclick={submitGuess}>Confirm</button>
				<button onclick={closeGuess}>Cancel</button>
			</div>
			<p class="result {result ?? ''}">
				{result === 'correct' ? '✓ Correct!' : result === 'wrong' ? '✗ Wrong!' : ''}
			</p>
		</div>
	</div>
{/if}
```

Le `<!-- svelte-ignore ... -->` évite un avertissement d'accessibilité sur le fond cliquable de la
modale ; la touche Échap fait la même chose au clavier.

### Le CSS

```svelte
<style>
	/* Styles from the old web/style.css, scoped to this page by Svelte */

	.mtg {
		display: grid;
		grid-template-columns: 280px minmax(0, 560px);
		justify-content: center;
		align-items: start;
		gap: 24px;
		max-width: 900px;
		margin: 32px auto 0;
		padding: 24px;
		background: #1a1a2e;
		color: #e0e0e0;
		border-radius: 12px;
	}

	h1 {
		margin: 0;
		font-size: 2rem;
		letter-spacing: 0.05em;
		color: #c8a951;
	}

	button {
		padding: 9px 14px;
		font-size: 0.95rem;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		background: #0f3460;
		color: #e0e0e0;
		transition: background 0.15s, transform 0.1s;
	}
	button:hover:not(:disabled) { background: #1a5276; }
	button:active:not(:disabled) { transform: scale(0.97); }
	button:disabled { opacity: 0.5; cursor: not-allowed; }

	button.gold { background: #c8a951; color: #1a1a2e; font-weight: bold; }
	button.gold:hover:not(:disabled) { background: #e0bc6a; }

	input {
		box-sizing: border-box;
		border: 2px solid #444;
		border-radius: 6px;
		background: #0f1e3a;
		color: #e0e0e0;
		outline: none;
	}
	input:focus { border-color: #c8a951; }

	/* ── Game ── */

	.game {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 16px;
	}

	.canvas-wrap {
		position: relative;
		width: 100%;
		max-width: 500px;
	}

	canvas {
		display: block;
		width: 100%;
		height: auto;
		border-radius: 8px;
		box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
	}

	.stats {
		box-sizing: border-box;
		display: flex;
		justify-content: space-between;
		width: 100%;
		background: #16213e;
		border-radius: 6px;
		padding: 8px 16px;
	}
	.hints { color: #7ec8e3; }
	.score { color: #c8a951; }
	.seed { color: #aaa; font-size: 0.85rem; }

	.letter-input {
		width: 100%;
		padding: 10px 14px;
		font-size: 1.1rem;
		background: #16213e;
		text-align: center;
	}
	.letter-input:disabled { opacity: 0.4; cursor: not-allowed; }

	.buttons {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
		width: 100%;
	}

	.scryfall { color: #7ec8e3; text-decoration: none; }
	.scryfall:hover { color: #a8dff0; text-decoration: underline; }

	/* ── Loading overlay (covers the card only, not the whole site) ── */

	.overlay {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 20px;
		padding: 16px;
		text-align: center;
		background: #1a1a2e;
		color: #c8a951;
		border-radius: 8px;
	}

	.spinner {
		width: 48px;
		height: 48px;
		border: 5px solid #333;
		border-top-color: #c8a951;
		border-radius: 50%;
		animation: spin 0.8s linear infinite;
	}
	@keyframes spin { to { transform: rotate(360deg); } }

	/* ── Guess modal ── */

	.backdrop {
		position: fixed;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(0, 0, 0, 0.7);
		z-index: 100; /* above the sticky top bar (z-index 50) */
	}

	.modal {
		display: flex;
		flex-direction: column;
		gap: 14px;
		min-width: 300px;
		max-width: 90vw;
		padding: 28px 32px;
		background: #16213e;
		border: 1px solid #444;
		border-radius: 10px;
		box-shadow: 0 8px 40px rgba(0, 0, 0, 0.8);
		color: #ccc;
	}
	.modal p { margin: 0; }
	.modal input { width: 100%; padding: 10px 12px; font-size: 1.05rem; }
	.modal-buttons { display: flex; gap: 10px; }
	.modal-buttons button { flex: 1; }

	.result { font-size: 1.1rem; font-weight: bold; text-align: center; min-height: 1.4em; }
	.result.correct { color: #4caf50; }
	.result.wrong { color: #e53935; }

	/* ── Leaderboard ── */

	.leaderboard {
		background: #16213e;
		border-radius: 10px;
		padding: 16px;
	}
	.leaderboard h2 { margin: 0 0 12px; color: #c8a951; font-size: 1.2rem; }

	.player-row { display: flex; gap: 8px; }
	.player-row input { flex: 1; min-width: 0; padding: 8px 10px; border-width: 1px; }

	.player-status { margin: 8px 0 14px; color: #9cc; font-size: 0.9rem; }

	ol {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	li {
		display: flex;
		justify-content: space-between;
		background: #0f1e3a;
		padding: 8px 10px;
		border-radius: 6px;
	}
	li.empty { justify-content: center; color: #aaa; }

	@media (max-width: 980px) {
		.mtg {
			grid-template-columns: minmax(0, 560px);
			margin: 16px;
			padding: 16px;
		}
		.leaderboard { order: 2; } /* game first on small screens */
	}
</style>
```

---

# Partie D — Lancer et vérifier

## D1. Sur ton PC

```bash
make backend        # terminal 1 (attendre "29743 cards imported" au premier lancement)
make frontend       # terminal 2
```

Ouvre `http://localhost:5173/cardguesser`, puis vérifie :

1. Une carte s'affiche, avec le numéro de seed sous la carte.
2. Taper des lettres dans le champ révèle les caractères. « Hints left » diminue, une lettre déjà
   tapée ne coûte rien.
3. Saisis un nom, puis Save → « Playing as ... ».
4. « Guess Answer » + bon nom → « ✓ Correct! », le score augmente et le lien Scryfall apparaît.
5. « New Random Card » après une victoire garde le score (série).
6. « Show Answer » (ou une mauvaise réponse) → le score de la série apparaît dans le leaderboard.
7. « Set Card by Seed » avec `5` → *Walking Sponge*.
8. Recharge la page : le nom est retenu et le leaderboard est toujours là.

DevTools (F12) → Network : seules de petites requêtes `/api/cards/...` et `/api/scores` doivent
apparaître, plus de `cardlist.json` de 10 Mo ni d'appel à `supabase.co`.

## D2. Sur la Pi

```bash
git add backend/src/main/java/Siteweb/backend/card backend/src/main/java/Siteweb/backend/score \
        backend/src/main/resources/mtg frontend/src/lib/assets/mtg \
        frontend/src/routes/cardguesser/+page.svelte frontend/vite.config.js
git commit -m "Ajout du jeu MTG Card Guesser (H2 à la place de Supabase)"
git push
```

Sur la Pi : `git pull`, puis relancer `make backend` et `make frontend`. Teste ensuite depuis ton PC
sur `http://192.168.1.17:5173/cardguesser`, et vérifie que le blocage des autres `POST` tient
toujours :

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" -d "[]" http://192.168.1.17:5173/api/races       # 404
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" -d '{"username":"x","score":7}' http://192.168.1.17:5173/api/scores   # 400 (refusé par Spring : il est bien passé par le proxy)
```

---

## Dépannage

| Symptôme | Cause probable | Correction |
|---|---|---|
| `package tools.jackson.databind does not exist` | Projet en Jackson 2 | Encadré de l'étape B6 |
| `Value too long for column "ORACLE_TEXT"` | `@Column(length = 1000)` oublié | Étape B1, puis relancer (l'import recommence puisque la table n'est pas complète) |
| `cardlist.json not found` dans le log | Fichier pas dans `backend/src/main/resources/mtg/` | Étape A1 |
| Page : « Failed to load card data. /cards/random: HTTP 503 » | Table vide (import pas fait ou échoué) | Lire le log du backend au démarrage |
| Page : « ... HTTP 404 » sur `/cards/random` | Backend lancé avec l'ancien code | Relancer `make backend` après `git pull` |
| Le jeu marche mais les scores ne s'enregistrent pas, `POST /api/scores` en **404** dans Network | Proxy Vite pas mis à jour | Étape C1, relancer `make frontend` |
| Pas de score enregistré, aucun `POST` dans Network | Pas de nom enregistré (Save), ou partie gagnée et pas encore terminée | Le score n'est envoyé qu'en fin de série (mauvaise réponse ou Show Answer) |
| Erreur d'import `#lib/assets/mtg/frame.png` | Images pas copiées | Étape A2 |

---

## À savoir pour la suite

- **`make clean_database` efface aussi le leaderboard.** Les tables `mtg_cards` et `mtg_scores` sont
  dans le même fichier H2 que les courses (`backend/data/races.mv.db`). Les cartes se réimportent
  toutes seules au démarrage suivant, mais les scores sont perdus. En revanche, le nettoyage
  automatique du changement d'année dans `RaceService` ne touche que les courses, les coureurs et les
  participations.
- **Remettre le leaderboard à zéro** (scores de test) : active temporairement
  `spring.h2.console.enabled=true`, va sur `http://localhost:8080/h2-console` depuis la Pi (URL JDBC
  `jdbc:h2:file:./data/races`, utilisateur `sa`), exécute `DELETE FROM MTG_SCORES;`, puis remets
  `false`.
- **Mettre à jour la liste de cartes** : remplace `backend/src/main/resources/mtg/cardlist.json` et
  redémarre. Si le nombre de lignes change, la table est remplacée automatiquement. S'il est
  identique, l'ancienne liste est gardée : vide la table (`DELETE FROM MTG_CARDS;` dans la console
  H2) pour forcer l'import. Attention : changer le fichier change les seeds.
- **Le nom de la carte est visible dans l'onglet Network** (réponse de `/api/cards/random`). C'était
  déjà le cas avant (le fichier entier était téléchargé). Le cacher demanderait aussi de déplacer la
  partie côté serveur.
- Les anciens scores Supabase ne sont pas migrés. Si tu veux les garder, exporte la table `scores` en
  CSV depuis le tableau de bord Supabase et réinsère-les avec des `POST /api/scores`.