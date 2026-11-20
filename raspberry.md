# Faire tourner MonSitePerso sur la Raspberry Pi, accessible depuis le réseau local

Objectif : ouvrir `http://192.168.1.17:5173` depuis ton ordinateur et avoir **tout** qui marche,
y compris la recherche de planning de coureur sur `/calendrier_cycliste`.

---

## 0. Pourquoi la recherche de coureur ne marche pas aujourd'hui

Ton site fait des appels à l'API Spring de deux façons différentes :

| Appel | Exécuté par | URL utilisée | Depuis ton PC |
|---|---|---|---|
| Liste des courses (`+page.server.js`) | le **serveur SvelteKit, sur la Pi** | `http://localhost:8080/api/races` | ✅ marche : `localhost` = la Pi |
| Recherche coureur + planning (`+page.svelte`) | ton **navigateur, sur ton PC** | `PUBLIC_API_URL` = `http://localhost:8080/api/...` | ❌ `localhost` = **ton PC**, où aucun Spring ne tourne |

Et même si on remplaçait l'URL par `http://192.168.1.17:8080/api`, ça échouerait encore pour deux
raisons :

1. `application.properties` contient `server.address=127.0.0.1` : Spring n'écoute que les connexions
   venant de la Pi elle-même.
2. `@CrossOrigin(origins = "http://localhost:5173")` : Spring refuse les appels navigateur venant
   d'une page servie depuis `http://192.168.1.17:5173` (erreur CORS).

### La solution retenue : un proxy `/api` dans Vite

Au lieu d'ouvrir Spring au réseau, le navigateur appelle **la même adresse que la page**
(`http://192.168.1.17:5173/api/...`), et le serveur Vite transmet la requête à Spring sur la Pi
(`http://127.0.0.1:8080/api/...`).

```
PC (navigateur) ──► 192.168.1.17:5173  (Vite, sur la Pi)
                         │  /api/... ──► 127.0.0.1:8080 (Spring, sur la Pi)
                         └  le reste  ──► pages SvelteKit
```

Avantages :
- **Aucun changement côté Java** : Spring reste fermé au réseau (`127.0.0.1`), c'est ce que tu veux
  pour la mise en public plus tard.
- **Plus de CORS** : page et API ont la même origine, le navigateur ne bloque rien.
- Ça marche quelle que soit l'adresse utilisée (`localhost:5173` sur la Pi, `192.168.1.17:5173`
  depuis le PC, un nom de domaine plus tard).

Au total : **2 fichiers à modifier**, tous les deux dans `frontend/`.

---

## Étape 1 — Faire écouter Vite sur le réseau et ajouter le proxy

Fichier : `frontend/vite.config.js`

Ajoute un bloc `server` après `plugins`. Le fichier complet devient :

```js
import adapter from '@sveltejs/adapter-auto';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},

			// adapter-auto only supports some environments, see https://svelte.dev/docs/kit/adapter-auto for a list.
			// If your environment is not supported, or you settled on a specific environment, switch out the adapter.
			// See https://svelte.dev/docs/kit/adapters for more information about adapters.
			adapter: adapter()
		})
	],

	server: {
		host: true, // écoute sur toutes les interfaces (0.0.0.0) : accessible via 192.168.1.17
		port: 5173,
		strictPort: true, // échoue au lieu de passer silencieusement sur 5174
		proxy: {
			// Le navigateur appelle /api/... sur le port 5173, Vite transmet à Spring sur la Pi
			'/api': {
				target: 'http://127.0.0.1:8080',
				// Seules les lectures passent : les POST du scraper restent internes à la Pi
				bypass: (req) => (req.method === 'GET' ? undefined : false)
			}
		}
	}
});
```

Explications ligne par ligne :

- `host: true` : par défaut Vite n'écoute que sur `localhost`, donc la Pi est injoignable depuis le
  PC. Avec `true`, il écoute sur toutes les interfaces. (Si tu lançais déjà `npm run dev -- --host`,
  c'est la même chose, mais écrit une fois pour toutes.)
- `strictPort: true` : si le port 5173 est déjà pris (un ancien `npm run dev` oublié), Vite s'arrête
  avec une erreur au lieu de démarrer sur 5174 sans que tu le voies.
- `target: 'http://127.0.0.1:8080'` : on écrit `127.0.0.1` et **pas** `localhost`. Sur la Pi,
  `localhost` peut se résoudre en IPv6 (`::1`) alors que Spring n'écoute qu'en IPv4 sur
  `127.0.0.1` → erreur `ECONNREFUSED` dans le proxy.
- `bypass` : tes endpoints `POST /api/races` et `POST /api/participation` (utilisés par le scraper)
  n'ont aucune authentification. Sans cette ligne, n'importe qui sur le réseau pourrait envoyer des
  données dans ta base via le port 5173. Avec, toute requête autre que `GET` reçoit un 404. Le
  scraper n'est pas concerné : il tourne sur la Pi et parle directement à `localhost:8080`.

---

## Étape 2 — Faire pointer le navigateur vers `/api`

Fichier : `frontend/src/env.js`

Change la valeur par défaut de `PUBLIC_API_URL` :

```diff
 	PUBLIC_API_URL: {
 		public: true,
 		description: 'Spring Boot API base URL, used by browser-side fetches',
-		schema: (value) => value ?? 'http://localhost:8080/api'
+		schema: (value) => value ?? '/api'
 	}
```

Avec une URL relative, le navigateur appelle `/api/rider/search?q=...` sur **l'adresse de la page**,
donc `http://192.168.1.17:5173/api/...`, que Vite transmet à Spring.

Ne touche **pas** à `API_URL` (juste au-dessus) : il est utilisé côté serveur, sur la Pi, et
`http://localhost:8080/api` y est correct.

> **Pourquoi modifier le défaut plutôt qu'un fichier `.env` ?** `.env` est dans ton `.gitignore`,
> donc il ne serait pas sur la Pi après un `git pull`. Changer la valeur par défaut dans le code
> marche partout (PC de dev et Pi). Si un jour tu as besoin d'une autre valeur, un
> `PUBLIC_API_URL=...` dans `frontend/.env` l'emporte toujours sur le défaut.

> ⚠️ Vérifie qu'il n'existe pas déjà un `frontend/.env` sur la Pi ou sur ton PC contenant
> `PUBLIC_API_URL=http://localhost:8080/api` : il écraserait la nouvelle valeur par défaut.
> ```bash
> cat frontend/.env 2>/dev/null
> ```
> Si la ligne existe, supprime-la ou remplace-la par `PUBLIC_API_URL=/api`.

---

## Étape 3 — Ce qu'il ne faut PAS modifier

| Élément | Laisser tel quel | Pourquoi |
|---|---|---|
| `server.address=127.0.0.1` dans `application.properties` | ✅ | Spring n'a pas besoin d'être joignable depuis le réseau, seul Vite lui parle |
| `@CrossOrigin(...)` dans les contrôleurs Java | ✅ | Devenu inutile (même origine), mais inoffensif. Tu peux le supprimer plus tard si tu veux faire le ménage |
| `API_BASE = "http://localhost:8080/api"` dans `Scraper_Manager.py` | ✅ | Le scraper tourne sur la Pi |
| `HEALTH_URL` dans le `Makefile` | ✅ | Idem |
| `+page.server.js` du calendrier | ✅ | S'exécute sur la Pi |

---

## Étape 4 — Déployer sur la Pi

Sur ton PC, commite et pousse :

```bash
git add frontend/vite.config.js frontend/src/env.js
git commit -m "Proxy /api via Vite pour accès depuis le réseau local"
git push
```

Sur la Pi :

```bash
cd ~/MonSitePerso          # adapte le chemin
git pull
cd frontend && npm install && cd ..
```

Puis, dans **deux terminaux** (ou deux sessions `tmux`/`screen`) :

```bash
# Terminal 1 : backend (+ scraper toutes les 72 h)
make backend
```

```bash
# Terminal 2 : frontend
make frontend
```

Au démarrage, Vite doit afficher une ligne `Network` :

```
  ➜  Local:   http://localhost:5173/
  ➜  Network: http://192.168.1.17:5173/
```

S'il n'y a que la ligne `Local`, le `host: true` n'a pas été pris en compte (fichier pas à jour,
`git pull` oublié).

---

## Étape 5 — Vérifier, du plus bas au plus haut

### 5.1 Sur la Pi : Spring répond

```bash
curl "http://127.0.0.1:8080/api/rider/search?q=pog"
```

Tu dois obtenir un JSON (éventuellement `[]` si la base est vide : lance le scraper d'abord).

### 5.2 Sur la Pi : le proxy Vite fonctionne

```bash
curl "http://127.0.0.1:5173/api/rider/search?q=pog"
```

Même résultat qu'en 5.1 → le proxy transmet bien.

### 5.3 Depuis ton PC : la Pi est joignable

```bash
curl "http://192.168.1.17:5173/api/rider/search?q=pog"
```

(Sous Windows PowerShell : `curl.exe "http://192.168.1.17:5173/api/rider/search?q=pog"`)

### 5.4 Depuis ton PC : les POST sont bien bloqués

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" -d "[]" http://192.168.1.17:5173/api/races
```

Doit afficher `404`.

### 5.5 Dans le navigateur

1. Ouvre `http://192.168.1.17:5173/calendrier_cycliste`.
2. Ouvre les DevTools (F12) → onglet **Network**.
3. Tape au moins 2 lettres dans la recherche.
4. Tu dois voir une requête `search?q=...` vers **`192.168.1.17:5173/api/rider/search`**
   avec un statut **200**, puis, en cliquant un coureur, `192.168.1.17:5173/api/participation/rider/<id>`.

Si la requête part encore vers `localhost:8080`, le navigateur utilise un ancien bundle :
recharge avec **Ctrl+Shift+R**, et vérifie l'étape 2 (et l'absence de `.env` qui l'écrase).

---

## Dépannage

| Symptôme | Cause probable | Correction |
|---|---|---|
| La page ne charge pas du tout depuis le PC | Vite n'écoute que sur `localhost` | Vérifie `host: true` et la ligne `Network` au démarrage |
| La page ne charge pas, la ligne `Network` est bien affichée | Pare-feu sur la Pi | `sudo ufw status` ; si actif : `sudo ufw allow from 192.168.1.0/24 to any port 5173 proto tcp` |
| `Port 5173 is already in use` | Un ancien `npm run dev` tourne encore | `fuser -k 5173/tcp` puis relance |
| Requêtes vers `localhost:8080` dans l'onglet Network | `PUBLIC_API_URL` pas à jour | Étape 2, vérifier `frontend/.env`, Ctrl+Shift+R |
| `/api/...` renvoie **502** ou **500**, terminal Vite affiche `http proxy error ... ECONNREFUSED` | Spring pas démarré, ou `target` en `localhost` résolu en IPv6 | `curl http://127.0.0.1:8080/api/races` sur la Pi ; `target` doit être `127.0.0.1` |
| Recherche OK mais toujours `[]` | Base vide | Laisser `make backend` lancer le scraper, ou `rm .last_scrape` pour forcer un nouveau scrape |
| Erreur `500` sur `/calendrier_cycliste` (page entière) | Le `load` serveur n'atteint pas Spring | Même vérif `curl http://127.0.0.1:8080/api/races` sur la Pi |

---

## Pour plus tard : la mise en ligne publique

Ce que tu as maintenant est fait pour **tester sur le réseau local**. Deux points à prévoir avant
d'ouvrir le site sur Internet :

1. **Ne pas exposer `vite dev`.** C'est un serveur de développement (rechargement à chaud, pas de
   cache, sources accessibles). En production : passer de `adapter-auto` à `@sveltejs/adapter-node`,
   faire `npm run build`, et lancer le serveur Node généré (avec systemd pour qu'il redémarre seul).
2. **Le proxy `/api` de Vite disparaît en production.** Il faudra le recréer dans un reverse proxy
   devant le site (Caddy ou nginx, qui gèrent aussi le HTTPS) :
   - `/api/*` en `GET` uniquement → `127.0.0.1:8080`
   - tout le reste → le serveur Node SvelteKit

   Comme le navigateur appelle déjà une URL relative `/api`, **aucun changement dans le code Svelte**
   ne sera nécessaire à ce moment-là.