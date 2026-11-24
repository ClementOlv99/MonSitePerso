# GitHub projects page: frontend guide

**Files you'll create** (the folder doesn't exist yet):
- `frontend/src/routes/projets/+page.server.js` fetches your repos from GitHub
- `frontend/src/routes/projets/+page.svelte` displays them as cards

The home page already links to `/projets` (`+page.svelte`, "Mes Projets Persos"), so that link
works as soon as the route exists.

**Goal:**

```
┌──────────────────────────────────────────────────────────────────┐
│ Accueil          Site de Louis-Clément Olivier           Contact │ ← existing top bar
├──────────────────────────────────────────────────────────────────┤
│ Mes Projets Persos                                               │
│                                                                  │
│ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐   │
│ │ MonSitePasVibe…  │ │ autre-projet     │ │ encore-un        │   │
│ │ Mon site perso   │ │ Description …    │ │ Pas de descrip…  │   │
│ │ [svelte] [java]  │ │                  │ │                  │   │ ← topics (optional)
│ │ ● Svelte  ★ 2    │ │ ● Python  ★ 0    │ │ ● Java  ★ 1      │   │
│ │ il y a 2 jours   │ │ il y a 3 sem.    │ │ il y a 5 mois    │   │
│ └──────────────────┘ └──────────────────┘ └──────────────────┘   │
│ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐   │
│ │ …                │ │ …                │ │ …                │   │
└──────────────────────────────────────────────────────────────────┘
          each card is a link to the repo on github.com
```

**Where the data comes from:**

| What | When | Request | Stored in |
|---|---|---|---|
| Your public repos, most recently pushed first | page load, on the SvelteKit server | `GET https://api.github.com/users/ClementOlv99/repos?sort=pushed&per_page=30` | `data.repos` |

There's only one request, and it runs **on the SvelteKit server**, not in the browser. It's the
same pattern as `calendrier_cycliste/+page.server.js`, except the API is GitHub instead of Spring Boot.

**JSON you'll receive** (an array of repos, trimmed to the fields you'll use; GitHub sends about 80 per repo):

```js
// GET https://api.github.com/users/ClementOlv99/repos?sort=pushed&per_page=30
[{
  id: 123456789,
  name: "MonSitePasVibecode",
  description: "Mon site perso",          // can be null!
  html_url: "https://github.com/ClementOlv99/MonSitePasVibecode",
  language: "Svelte",                     // can be null (empty repo, only markdown…)
  stargazers_count: 2,
  topics: ["svelte", "spring-boot"],      // can be []
  fork: false,
  archived: false,
  pushed_at: "2026-11-17T14:03:12Z"
}]
```

> **Rate limit:** without a token, GitHub allows **60 requests per hour per IP address**. Every
> page load (and every dev-server hot reload) uses one request. Step 8 adds a cache so that
> becomes one request every 10 minutes at most, and Step 9 adds an optional token (5,000/hour).

The steps below build the page **in small pieces**. Each step ends with a ✅ **Checkpoint**:
check it in the browser before moving on, so that when something breaks you know which step caused it.

---

## Step 0: Before you start

1. The GitHub API answers in a terminal:
   ```
   curl "https://api.github.com/users/ClementOlv99/repos?sort=pushed&per_page=3"
   ```
   You should see a JSON array with your 3 most recently pushed repos.
2. Check how many requests you have left:
   ```
   curl -s -I "https://api.github.com/users/ClementOlv99/repos" | grep -i x-ratelimit
   ```
   `x-ratelimit-remaining` is what's left for this hour, and `x-ratelimit-reset` is when the
   counter resets (a Unix timestamp; `date -d @<number>` converts it).
3. The frontend dev server is running: `cd frontend && npm run dev`.
4. Keep **DevTools (F12)** open, but note: this time the GitHub request happens on the server, so
   it **won't show up in the Network tab**. Its errors and `console.log`s appear in the
   **terminal running `npm run dev`**.

You don't need Spring Boot for this page.

✅ **Checkpoint:** the `curl` returns your repos, and `x-ratelimit-remaining` is above 0.

---

## Step 1: Create the route (no data yet)

### 1.1 The folder and the page

Create `frontend/src/routes/projets/+page.svelte`:

```svelte
<main class="projets">
	<h1>Mes Projets Persos</h1>
	<p>Bientôt des cartes ici</p>   <!-- temporary -->
</main>
```

- In SvelteKit, **the folder name is the URL**: `routes/projets/+page.svelte` is served at `/projets`.
- The top bar comes from `+layout.svelte` automatically. Every page is rendered inside its
  `{@render children()}`, so you don't repeat the header here.
- The dark background and white text also come from the layout (`:global(body)`).

### 1.2 A bit of spacing

At the bottom of the file:

```svelte
<style>
	.projets {
		max-width: 1100px;
		margin: 0 auto;
		padding: 2rem;
	}
</style>
```

- `max-width` + `margin: 0 auto` stops the content from stretching across a very wide screen
  and centers it. `auto` left/right margins share the leftover space equally.
- `padding: 2rem` matches the top bar's horizontal padding, so the title lines up with "Accueil".

✅ **Checkpoint:** on the home page, click "Mes Projets Persos". You land on `/projets` with the
top bar, the title and the temporary text, not on the 404 page (`+error.svelte`).

---

## Step 2: Fetch the repos on the server

### 2.1 Create `+page.server.js`

Create `frontend/src/routes/projets/+page.server.js`:

```js
const GITHUB_USER = 'ClementOlv99';
const URL = `https://api.github.com/users/${GITHUB_USER}/repos?sort=pushed&per_page=30`;

export async function load({ fetch }) {
	const response = await fetch(URL, {
		headers: { Accept: 'application/vnd.github+json' }
	});
	const repos = await response.json();
	return { repos };
}
```

Line by line:
- A file called `+page.server.js` next to a `+page.svelte` runs **only on the server**, before
  the page renders. Whatever `load` returns becomes `data` in the page.
- `sort=pushed` sorts by the last `git push`. This is what "recently modified" means here. The
  other option, `sort=updated`, also changes when someone stars the repo or you edit its
  description, so it's less accurate.
- `per_page=30` asks for 30 repos. You'll keep only some of them in Step 4, after removing forks
  and archived repos, so ask for more than you'll show.
- `Accept: 'application/vnd.github+json'` is the header GitHub recommends. It pins the response
  format so it doesn't change under you.
- Use the `fetch` that `load` gives you (`{ fetch }`), not the global one. SvelteKit's version
  works the same way, but it also handles cookies and relative URLs. It's a good habit even
  though you don't need those features here.

**Why on the server and not in the browser?** In the browser, every visitor would call GitHub
from their own IP. That works, but you couldn't add a cache (Step 8) or hide a token (Step 9).
On the server, you control both.

### 2.2 Look at what you got

In `+page.svelte`, add a `<script>` at the top and replace the temporary text:

```svelte
<script>
	let { data } = $props();
</script>

<main class="projets">
	<h1>Mes Projets Persos</h1>
	<p>Debug: {data.repos.length} repos</p>   <!-- temporary -->
</main>
```

- `let { data } = $props();` is the same line as in `calendrier_cycliste/+page.svelte`. It
  receives what `load` returned.

✅ **Checkpoint:** the page shows "Debug: N repos" with your real number of public repos (up to 30).
If the page crashes instead, look at the `npm run dev` terminal and go to Step 3.

---

## Step 3: Handle failures

The GitHub API can fail. Your network might be down, or you might hit the rate limit (status
403 or 429). Right now, `data.repos` would then be an error object instead of an array, and
`.length` would be wrong or crash.

### 3.1 Choose: error page or empty list?

`calendrier_cycliste` calls `error(503, …)`, which shows `+error.svelte`. That makes sense there,
because the page is useless without the backend. Here, the projects page is part of your
portfolio, so a broken GitHub request shouldn't replace the whole page with an error. It's
better to show the page with a short message.

### 3.2 Rewrite `load`

```js
export async function load({ fetch }) {
	let response;

	try {
		response = await fetch(URL, {
			headers: { Accept: 'application/vnd.github+json' }
		});
	} catch (err) {
		// Network failure: no internet, DNS…
		console.error('GitHub unreachable:', err);
		return { repos: [], failed: true };
	}

	if (!response.ok) {
		// 403/429 = rate limit, 404 = wrong username
		console.error('GitHub error', response.status, await response.text());
		return { repos: [], failed: true };
	}

	const repos = await response.json();
	return { repos, failed: false };
}
```

- `fetch` **throws** only when the server can't be reached at all. A 404 or 403 does *not*
  throw; it gives a normal response with `response.ok === false`. That's why there are two
  separate checks, like in `calendrier_cycliste`.
- `console.error` prints to the **`npm run dev` terminal**, because this code runs on the server.
  `await response.text()` includes GitHub's error message, which usually says exactly what's wrong
  (for example `"API rate limit exceeded for 1.2.3.4"`).
- Returning `failed` lets the page tell "GitHub is down" apart from "you have no repos".

### 3.3 Show the message

In `+page.svelte`, below the `<h1>`:

```svelte
{#if data.failed}
	<p class="hint">Impossible de charger les projets depuis GitHub pour le moment.</p>
{/if}
```

```css
.hint {
	color: #aaa;
}
```

✅ **Checkpoint:** temporarily change `GITHUB_USER` to a name that can't exist (for example
`'zzz-nobody-zzz-12345'`). The page still shows the top bar and title with the grey message, and
the terminal prints `GitHub error 404 …`. **Change it back.**

---

## Step 4: Keep only what you need

GitHub sends about 80 fields per repo. You'll display 7. Everything `load` returns is **serialized
into the HTML** sent to the browser, so trimming it keeps the page light.

Replace `const repos = await response.json();` with:

```js
const all = await response.json();

const repos = all
	.filter((repo) => !repo.fork && !repo.archived)
	.slice(0, 9)
	.map((repo) => ({
		id: repo.id,
		name: repo.name,
		description: repo.description,
		url: repo.html_url,
		language: repo.language,
		stars: repo.stargazers_count,
		topics: repo.topics ?? [],
		pushedAt: repo.pushed_at
	}));
```

- `.filter(...)` keeps only the repos for which the function returns `true`:
  - `!repo.fork` drops repos you forked from someone else, which aren't really your projects.
  - `!repo.archived` drops repos you marked as finished or abandoned on GitHub.
  - Remove either condition if you *do* want those repos to appear.
- `.slice(0, 9)` keeps the first 9. They're already sorted by GitHub (`sort=pushed`), so these are
  the 9 most recent. 9 fills a 3-column grid evenly; pick any number.
- `.map(...)` builds a **new, smaller object** for each repo.
  - `(repo) => ({ ... })`: the extra parentheses around `{}` are required. Without them,
    JavaScript reads `{` as the start of a function body instead of an object.
  - `html_url` becomes `url` and `pushed_at` becomes `pushedAt`, so the names are nicer in Svelte.
  - `repo.topics ?? []`: `??` means "if the left side is `null` or `undefined`, use the right
    side". You'll always get an array, so `{#each}` can't crash.

✅ **Checkpoint:** the debug count is now 9 or fewer, and there are no forks among them.
To see the data, temporarily add `<pre>{JSON.stringify(data.repos, null, 2)}</pre>` to the page.
`JSON.stringify(x, null, 2)` prints the object with 2-space indentation.

---

## Step 5: Render the cards

### 5.1 Markup

Replace the debug line in `+page.svelte` with:

```svelte
<div class="grid">
	{#each data.repos as repo (repo.id)}
		<a class="card" href={repo.url} target="_blank" rel="noopener noreferrer">
			<h2>{repo.name}</h2>
			<p class="description">{repo.description ?? 'Pas de description'}</p>
		</a>
	{:else}
		{#if !data.failed}
			<p class="hint">Aucun projet public pour l'instant.</p>
		{/if}
	{/each}
</div>
```

- `{#each data.repos as repo (repo.id)}` loops over the array. `(repo.id)` is the **key**, like
  `(race.id)` in the calendar.
- `{:else}` renders when the array is empty. The `{#if !data.failed}` inside avoids showing
  "Aucun projet" **and** the error message at the same time.
- **The whole card is an `<a>`**, so you can click anywhere on it. Putting block elements
  (`<h2>`, `<p>`) inside an `<a>` is valid HTML5.
- `target="_blank"` opens GitHub in a new tab, so the visitor doesn't leave your site.
- `rel="noopener noreferrer"`: without `noopener`, the opened page can control your tab through
  `window.opener`. Modern browsers add it automatically for `_blank`, but writing it is the safe habit.
- `repo.description ?? 'Pas de description'`: the description is `null` when you never wrote
  one on GitHub, and `{null}` would display nothing at all.

### 5.2 Grid layout (inside `<style>`)

```css
.grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
	gap: 1rem;
}
```

This one line handles every screen width, so you won't need a `@media` query:
- `minmax(260px, 1fr)` means each column is **at least 260px** wide and otherwise takes an equal
  share of the space.
- `repeat(auto-fill, ...)` means "fit as many of those columns as possible". On a wide screen
  that's 3 or 4 columns, on a tablet 2, and on a phone 1.

✅ **Checkpoint:** the repos appear as a grid of (still ugly, blue-underlined) blocks. Clicking
one opens the repo on github.com in a new tab. Make the window narrow and check that the number
of columns goes down.

---

## Step 6: Card styling

The text is blue because `+layout.svelte` sets `:global(a) { color: #8ab4f8 }`, and your cards
are `<a>`. Add to `<style>`:

```css
.card {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	padding: 1rem 1.25rem;
	border: 1px solid #444;
	border-radius: 8px;
	background: #323232;
	color: inherit;
	text-decoration: none;
	transition: border-color 0.15s, transform 0.15s;
}

.card:hover {
	border-color: #4b3b61;
	transform: translateY(-2px);
}

.card h2 {
	margin: 0;
	font-size: 1.1rem;
	color: #8ab4f8;
	overflow-wrap: anywhere;
}

.description {
	margin: 0;
	color: #ccc;
	flex-grow: 1;
}
```

Why each one matters:
- `display: flex; flex-direction: column` stacks the card's content vertically. Together with
  `flex-grow: 1` on the description, it pushes everything after the description (Step 7) to the
  **bottom of the card**, so the footers line up even when the descriptions have different lengths.
  Grid rows stretch all cards in a row to the same height, so the alignment shows.
- `background: #323232` is slightly lighter than the page (`#282828`), so the cards stand out.
- `color: inherit` cancels the global link blue for the card body (white, from `body`), and
  `text-decoration: none` removes the underline.
- The hover uses `#4b3b61`, the purple of the top bar, so the page matches the rest of the site.
- `transform: translateY(-2px)` lifts the card by 2px, and `transition` animates the change
  instead of jumping. It's only animated for the properties listed.
- `overflow-wrap: anywhere` lets a long name like `MonSitePasVibecode` break instead of
  overflowing the card on a phone.
- `margin: 0` removes the browser's default space above and below `<h2>` and `<p>`; `gap`
  handles the spacing now.

✅ **Checkpoint:** cards look like cards: dark boxes with rounded corners, a blue title, grey
text, and a purple border plus a small lift on hover.

---

## Step 7: Footer: language, stars, last update

### 7.1 Relative date ("il y a 3 jours")

In `<script>`:

```js
const rtf = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' });

function timeAgo(iso) {
	const days = Math.round((new Date(iso) - Date.now()) / 86_400_000);
	if (days > -30) return rtf.format(days, 'day');
	if (days > -365) return rtf.format(Math.round(days / 30), 'month');
	return rtf.format(Math.round(days / 365), 'year');
}
```

- `Intl.RelativeTimeFormat` is built into JavaScript (no library). It turns `(-3, 'day')` into
  `"il y a 3 jours"`, in French because of `'fr'`.
- `numeric: 'auto'` gives `"aujourd’hui"` and `"hier"` instead of `"dans 0 jour"` / `"il y a 1 jour"`.
- `new Date(iso) - Date.now()` is the difference in **milliseconds**. It's negative because the
  date is in the past, which is what `rtf.format` expects for "il y a…".
- `86_400_000` is the number of ms in a day (24 × 60 × 60 × 1000). The `_` characters are only
  there for readability; JavaScript ignores them.
- The 30 and 365 thresholds go from days to months to years, so you get "il y a 2 mois" instead
  of "il y a 61 jours".

### 7.2 Markup

Inside the `<a class="card">`, after the description:

```svelte
<div class="meta">
	{#if repo.language}
		<span>{repo.language}</span>
	{/if}
	<span>★ {repo.stars}</span>
	<span class="date">{timeAgo(repo.pushedAt)}</span>
</div>
```

- `{#if repo.language}`: the language is `null` for repos without code (only a README, for
  example). Without the check, you'd get an empty `<span>` and a strange gap.

```css
.meta {
	display: flex;
	flex-wrap: wrap;
	gap: 0.25rem 1rem;
	font-size: 0.85rem;
	color: #aaa;
}

.date {
	margin-left: auto;
}
```

- `flex-wrap: wrap` lets the items go on a second line if the card is too narrow.
- `gap: 0.25rem 1rem` sets the vertical gap first, then the horizontal one.
- `margin-left: auto` on the date pushes it to the far right, the same trick as the × button in
  the rider guide.

✅ **Checkpoint:** each card shows its language, star count and "il y a …" at the bottom, and
the footers line up across a row even when descriptions differ in length. The repo you pushed
most recently (probably this site) should say "aujourd’hui" or "hier".

---

## Step 8: Cache the result (protect the rate limit)

Right now, **every** page load calls GitHub. While you develop, each save triggers a reload, so
you can burn through the 60/hour quickly. When that happens the page shows the Step 3 message
until the hour resets.

### 8.1 A module-level cache

In `+page.server.js`, **above** `load`:

```js
const CACHE_MS = 10 * 60 * 1000; // 10 minutes
let cache = null;                // { repos, time }
```

At the very **start** of `load`:

```js
if (cache && Date.now() - cache.time < CACHE_MS) {
	return { repos: cache.repos, failed: false };
}
```

And just before the final `return { repos, failed: false };`:

```js
cache = { repos, time: Date.now() };
```

How it works:
- Variables declared **outside** `load` live as long as the server process runs. They're shared
  by every visitor and every request, unlike variables inside `load`, which start fresh each time.
- The first visitor triggers a real request and fills `cache`. Everyone else in the next
  10 minutes gets the stored copy instantly, without calling GitHub.
- **Only successful results are cached.** The two `return { repos: [], failed: true }` lines
  don't touch `cache`, so a temporary failure is retried on the next visit instead of being
  stuck for 10 minutes.
- Saving `+page.server.js` reloads the module and empties the cache. That's normal during
  development; saving `+page.svelte` keeps it.

> On a classic Node server (`adapter-node`) the cache lasts until restart. On serverless hosts
> (Vercel, Netlify), each instance can start fresh, so the cache helps less, but it never breaks
> anything.

✅ **Checkpoint:** add `console.log('calling GitHub')` just before the `fetch`. Reload `/projets`
several times: the terminal prints it **once**, not on every reload. Remove the log afterwards.

---

## Step 9 (optional): Use a token

A token raises the limit to 5,000 requests per hour. You only need one if you still hit the
limit, for example because several sites share the server's IP.

1. On GitHub: **Settings → Developer settings → Personal access tokens → Fine-grained tokens →
   Generate new token**. For **Repository access**, choose "Public repositories (read-only)".
   It needs **no permissions**. Copy the token; GitHub shows it only once.
2. In `frontend/.env` (create it if it doesn't exist):
   ```
   GITHUB_TOKEN=github_pat_xxxxxxxx
   ```
   Check that `.env` is in `.gitignore` (`git check-ignore -v frontend/.env` should print a
   line). **Never commit a token.**
3. In `+page.server.js`:
   ```js
   import { env } from '$env/dynamic/private';
   ```
   and build the headers like this:
   ```js
   const headers = { Accept: 'application/vnd.github+json' };
   if (env.GITHUB_TOKEN) {
   	headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
   }
   // ...
   response = await fetch(URL, { headers });
   ```
   - It's the same `$env/dynamic/private` as `API_URL` in the calendar. **Private** means
     SvelteKit refuses to send it to the browser, which is exactly what you want for a secret.
   - The `if` keeps the page working without a token (on another machine, for example).
4. Restart `npm run dev`. `.env` is only read at startup.

✅ **Checkpoint:** run `curl -s -H "Authorization: Bearer <your token>" -I https://api.github.com/users/ClementOlv99/repos | grep -i x-ratelimit-limit`.
It should print `5000`. The page works the same as before.

---

## Step 10 (optional): Polish

These aren't required, but each one makes the page more pleasant.

**10.1 Topics as small tags**

GitHub repos can have topics (repo page → ⚙️ next to "About"). You already return them in Step 4.
Between the description and `.meta`:

```svelte
{#if repo.topics.length > 0}
	<ul class="topics">
		{#each repo.topics as topic (topic)}
			<li>{topic}</li>
		{/each}
	</ul>
{/if}
```

```css
.topics {
	display: flex;
	flex-wrap: wrap;
	gap: 0.35rem;
	margin: 0;
	padding: 0;
	list-style: none;
}

.topics li {
	padding: 0.1rem 0.6rem;
	border-radius: 999px;
	background: #4b3b61;
	font-size: 0.75rem;
}
```

- `(topic)` as the key works because topic names are unique within a repo.
- `border-radius: 999px` is the classic trick for "pill" shapes. Any value larger than half the
  height gives fully rounded ends.

**10.2 A colored dot for the language, like on GitHub**

In `<script>`:

```js
const LANG_COLORS = {
	JavaScript: '#f1e05a', TypeScript: '#3178c6', Svelte: '#ff3e00', Python: '#3572a5',
	Java: '#b07219', HTML: '#e34c26', CSS: '#663399', C: '#555555', 'C++': '#f34b7d', Shell: '#89e051'
};
```

Replace the language `<span>` with:

```svelte
<span>
	<span class="dot" style:background={LANG_COLORS[repo.language] ?? '#888'}></span>
	{repo.language}
</span>
```

```css
.dot {
	display: inline-block;
	width: 0.7rem;
	height: 0.7rem;
	border-radius: 50%;
	vertical-align: middle;
}
```

- `style:background={...}` is the Svelte directive for setting **one** CSS property from a variable.
- `?? '#888'` gives grey to a language that isn't in the map.
- `'C++'` needs quotes because `+` isn't allowed in a bare object key.

**10.3 Show a few repos you choose, always first**

If you'd rather pin some projects, keep a list of names in `+page.server.js` and sort them to
the front before the `.slice`:

```js
const PINNED = ['MonSitePasVibecode'];
// in the chain, before .slice(0, 9):
.sort((a, b) => PINNED.includes(b.name) - PINNED.includes(a.name))
```

`true - false` is `1` in JavaScript, so pinned repos sort before the others. `Array.prototype.sort`
is stable, so the rest keep GitHub's order (most recent first).

---

## Final file structure (for orientation)

```
src/routes/projets/+page.server.js
	import { env } from '$env/dynamic/private';     ← optional, step 9
	const GITHUB_USER, URL, CACHE_MS
	let cache                                        ← step 8

	export async function load({ fetch }) {
		if (cache is fresh) return it
		try { fetch(URL, { headers }) } catch → { repos: [], failed: true }
		if (!response.ok)                     → { repos: [], failed: true }
		filter (no forks / archived) → slice(0, 9) → map (7 fields)
		cache = …
		return { repos, failed: false }
	}

src/routes/projets/+page.svelte
	<script>
		let { data } = $props();
		rtf, timeAgo()
		LANG_COLORS                                  ← optional, step 10.2
	</script>

	<main class="projets">
		<h1>Mes Projets Persos</h1>
		{#if data.failed} <p class="hint"> … </p> {/if}
		<div class="grid">
			{#each data.repos as repo (repo.id)}
				<a class="card" href=… target="_blank">
					<h2>name</h2>
					<p class="description"> … </p>
					<ul class="topics"> … </ul>         ← optional, step 10.1
					<div class="meta"> language · ★ · date </div>
				</a>
			{:else} … {/each}
		</div>
	</main>

	<style>
		.projets, .hint, .grid, .card, .card:hover, .card h2, .description,
		.meta, .date, .topics, .dot
	</style>
```

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `/projets` shows the 404 page | Folder misspelled (`projet` vs `projets`) or file not named exactly `+page.svelte` |
| Grey "Impossible de charger…" message | Read the `npm run dev` terminal: it prints GitHub's status and message (Step 3) |
| Terminal: `GitHub error 403` / `429` + "rate limit exceeded" | 60 requests/hour used up. Wait for the reset (Step 0.2), check that the cache works (Step 8), or add a token (Step 9) |
| Terminal: `GitHub error 404` | Typo in `GITHUB_USER` |
| Terminal: `GitHub error 401` "Bad credentials" | The token in `.env` is wrong or expired: generate a new one, or remove the line to go back to unauthenticated |
| Token change has no effect | `npm run dev` wasn't restarted; `.env` is only read at startup |
| `data.repos.length` crashes / says `undefined` | You returned an error object: check the `!response.ok` branch is there (Step 3) |
| Nothing in the Network tab | Normal: the request runs on the server, not in the browser |
| A repo you just created is missing | It's a fork, archived, private, or not in the 9 most recent. Or the cache is still serving the old list (wait 10 min or save `+page.server.js`) |
| Cards are blue and underlined | `color: inherit` / `text-decoration: none` missing on `.card` (Step 6) |
| Card footers don't line up | `flex-direction: column` on `.card` or `flex-grow: 1` on `.description` missing |
| Description shows nothing | It's `null` on GitHub and the `?? 'Pas de description'` fallback is missing |
| A long repo name overflows the card | `overflow-wrap: anywhere` missing on `.card h2` |

## Pitfall: `timeAgo` runs twice

SvelteKit renders the page **on the server first**, then the browser runs the same code again
("hydration"). `timeAgo` uses `Date.now()`, so the two runs can give different text: for example if
the page is loaded just around midnight, or if the server and browser are in different timezones.
Svelte then shows a hydration warning in the Console and replaces the text. This is harmless and
rare. If it bothers you, compute the string in `load` instead (add `ago: timeAgo(repo.pushed_at)`
in the `.map` and move `rtf` / `timeAgo` into `+page.server.js`). The server then decides
once and the browser just displays it. The trade-off: with the Step 8 cache, the text can be up
to 10 minutes out of date.
