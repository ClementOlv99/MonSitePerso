# Rider search & schedule: frontend guide

**File you'll edit:** `frontend/src/routes/calendrier_cycliste/+page.svelte`. This is the only one.
`+page.server.js` stays as it is. It keeps loading the full calendar into `data.races` when the page opens.

**Goal:**

```
┌──────────────────────────┬──────────────────────────┐
│ Season calendar          │ [ Search a rider…      ] │
│ ┌──────────────────────┐ │  ┌────────────────────┐  │
│ │ full race table      │ │  │ 🇦🇺 Jai HINDLEY     │  │ ← dropdown while typing
│ │ (always data.races)  │ │  │ 🇬🇧 Tom HINDLEY…    │  │
│ │                      │ │  └────────────────────┘  │
│ │                      │ │ 🇦🇺 Jai HINDLEY      [×] │ ← after clicking a rider
│ │                      │ │ ┌──────────────────────┐ │
│ │                      │ │ │ his schedule table   │ │
│ └──────────────────────┘ │ └──────────────────────┘ │
└──────────────────────────┴──────────────────────────┘
```

**Where the data comes from:**

| What | When | Request | Stored in |
|---|---|---|---|
| Full calendar (left) | page load, on the SvelteKit server | `GET /api/races` (already in `+page.server.js`) | `data.races` |
| Dropdown suggestions | while typing, in the browser | `GET /api/rider/search?q=pog` | `suggestions` |
| Rider schedule (right) | when a rider is clicked, in the browser | `GET /api/participation/rider/{id}` | `schedule` |

**JSON you'll receive:**

```js
// GET /api/rider/search?q=hind   → array of riders
[{ id: 12, riderUrl: "rider/jai-hindley", prenom: "Jai", nom: "HINDLEY", country: "au" }]

// GET /api/participation/rider/12 → array of races (same shape as data.races)
[{ id: 1, raceUrl: "race/tour-down-under-2027-gc", name: "Santos Tour Down Under ME",
   startDate: "2027-01-19", endDate: "2027-01-24", category: "UWT", country: "au" }]
```

Rider and race countries are both lowercase 2-letter codes, so your existing `flag()` function
works for both.

The steps below build the page **in small pieces**. Each step ends with a ✅ **Checkpoint**:
check it in the browser before moving on, so that when something breaks you know which step caused it.

---

## Step 0: Before you start

1. Spring Boot is running, and both endpoints answer in a terminal:
   ```
   curl "http://localhost:8080/api/rider/search?q=hind"
   curl "http://localhost:8080/api/participation/rider/<an id from above>"
   ```
2. The frontend dev server is running: `cd frontend && npm run dev`.
3. Open `http://localhost:5173/calendrier_cycliste` and keep **DevTools (F12)** open on the
   **Console** and **Network** tabs while you work.

> The browser address must be `localhost:5173`. Spring only accepts browser calls from that exact
> origin (`@CrossOrigin`). `127.0.0.1:5173` counts as a *different* origin and would be blocked.

✅ **Checkpoint:** the page shows the calendar exactly as before.

---

## Step 1: Two-column layout (no logic yet)

Start with the empty structure. This way, when you add behaviour later, you can already see where it goes.

### 1.1 Wrap the markup

Everything **below** `</script>` goes inside one container with two children:

```svelte
<div class="layout">
	<section class="calendar">
		<!-- your existing <h1> and <table> move here, unchanged -->
	</section>

	<aside class="rider-panel">
		<p>Right side</p>   <!-- temporary, just to see the column -->
	</aside>
</div>
```

- `<section>` and `<aside>` behave like `<div>`, but their names say what they contain.
  `<aside>` means "content next to the main content", which is exactly what this panel is.
- **Don't change anything inside the existing table.** Just cut and paste it into `<section>`.

### 1.2 Add a `<style>` block at the very bottom of the file

Svelte styles are **scoped**: a `table { ... }` rule here only affects tables in this page, not
the rest of the site.

```svelte
<style>
	.layout {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 2rem;
		align-items: start;
	}
</style>
```

- `display: grid` turns the container into a grid.
- `grid-template-columns: 1fr 1fr` creates two columns that share the space equally (`fr` means
  "fraction of the remaining space"). Try `3fr 2fr` if you want the calendar wider.
- `gap` is the space between the columns.
- `align-items: start` matters: without it, the right column would stretch to the full height of
  the (very long) calendar, which would break the "sticky" effect in Step 10.

✅ **Checkpoint:** the calendar is on the left half and "Right side" is on the right half.

---

## Step 2: The search input + state

### 2.1 The API base URL

In `<script>`, just below `let { data } = $props();`:

```js
const API = 'http://localhost:8080/api';
```

**Why not reuse `API_URL` from `+page.server.js`?** That file imports from `$env/dynamic/private`.
*Private* variables only exist on the server, and SvelteKit refuses to send them to the browser.
The code you're writing now runs in the browser. If you want an env variable later, put
`PUBLIC_API_URL=http://localhost:8080/api` in `frontend/.env` and use
`import { PUBLIC_API_URL } from '$env/static/public';`. For now, a constant is fine.

### 2.2 Declare the state

Still in `<script>`:

```js
let query = $state('');
let suggestions = $state([]);
let selectedRider = $state(null);
let schedule = $state([]);
```

- `$state(...)` is a Svelte 5 **rune**. It makes a variable *reactive*: whenever you assign a new
  value (`query = 'abc'`), every part of the markup that uses it re-renders automatically.
- A normal `let x = ...` would **not** update the page when it changes.

What each variable is for:

| Variable | Starts as | Holds |
|---|---|---|
| `query` | `''` | the text currently in the input |
| `suggestions` | `[]` | riders returned by the search (the dropdown) |
| `selectedRider` | `null` | the rider object you clicked (`null` = nobody selected) |
| `schedule` | `[]` | that rider's races |

### 2.3 Put the input in the right column

Replace `<p>Right side</p>` with:

```svelte
<div class="search">
	<input bind:value={query} placeholder="Search a rider…" />
</div>

<p>Debug: {query}</p>   <!-- temporary -->
```

- `bind:value={query}` is a **two-way binding**. Typing updates `query`, and assigning `query`
  in JavaScript updates what's shown in the input. You'll use the second direction in Step 6, to
  put the rider's full name in the box.

✅ **Checkpoint:** typing in the input shows the same text live in "Debug: …".

---

## Step 3: Fetch suggestions while typing

### 3.1 Write `onSearchInput` (first version, without debounce)

In `<script>`:

```js
async function onSearchInput() {
	const q = query.trim();
	if (q.length < 2) {
		suggestions = [];
		return;
	}
	const res = await fetch(`${API}/rider/search?q=${encodeURIComponent(q)}`);
	if (res.ok) {
		suggestions = await res.json();
	}
}
```

Line by line:
- `query.trim()` removes the spaces at both ends, so `"  hind "` becomes `"hind"`.
- `q.length < 2` → empty the dropdown and stop. This matches the backend, which returns
  `[]` for fewer than 2 characters anyway, so calling it would waste a request.
- `` `...${...}...` `` is a **template string**: `${}` inserts a JavaScript value into the text.
- `encodeURIComponent(q)` makes the text safe to put in a URL. A space becomes `%20`, `é`
  becomes `%C3%A9`, and an `&` can't break the query string.
- `fetch(...)` sends the request. `await` waits for the answer without freezing the page.
  `await` only works inside an `async` function, which is why the function is marked `async`.
- `res.ok` is `true` for status 200–299. On a 404 or 500 you just keep the old suggestions.
- `await res.json()` reads the body and turns the JSON text into a JavaScript array.

### 3.2 Call it on every keystroke

Add `oninput` to the input:

```svelte
<input bind:value={query} oninput={onSearchInput} placeholder="Search a rider…" />
```

- Write `oninput={onSearchInput}` **without parentheses**. That gives Svelte the function, and
  Svelte calls it on each input event. `oninput={onSearchInput()}` would call it *once* while the
  page renders and pass the result (a Promise) as the handler, which is wrong.
- In Svelte 5, events are plain attributes: `oninput`, `onclick`, and so on. The Svelte 4 syntax
  `on:input` is the old one.

### 3.3 Show the raw result, temporarily

Replace the debug line with:

```svelte
<p>Debug: {suggestions.length} riders found</p>
```

✅ **Checkpoint:**
- Type `hi`: the count changes, and the **Network** tab shows a `search?q=hi` request with
  status 200. Click it → **Response** tab: you should see the JSON array.
- Type `h` alone: the count goes back to 0 and no request is sent.
- A red **CORS** error in the Console means the browser blocked the call: check that you're on
  `localhost:5173` and that Spring was restarted after `@CrossOrigin` was added.

---

## Step 4: Debounce (wait until the user stops typing)

Right now, typing `hindley` sends 6 requests (`hi`, `hin`, `hind`…). A **debounce** waits for a
short pause in typing and only sends the last one.

### 4.1 Add a timer variable

At the top of `<script>`, next to the state:

```js
let timer;
```

This is a plain `let`, **not** `$state`: it's never displayed, so it doesn't need to be reactive.

### 4.2 Rewrite `onSearchInput`

```js
function onSearchInput() {
	clearTimeout(timer);

	const q = query.trim();
	if (q.length < 2) {
		suggestions = [];
		return;
	}

	timer = setTimeout(async () => {
		const res = await fetch(`${API}/rider/search?q=${encodeURIComponent(q)}`);
		if (res.ok) {
			suggestions = await res.json();
		}
	}, 250);
}
```

How it works:
- `setTimeout(fn, 250)` means "run `fn` in 250 ms". It returns an id, which you store in `timer`.
- `clearTimeout(timer)` cancels the *previous* scheduled search. Each keystroke cancels the one
  before it, so only the last keystroke's search actually runs, 250 ms after you stop typing.
- `clearTimeout` runs **before** the length check. That way, if you type `hi` and quickly delete
  back to `h`, the pending `hi` search is cancelled too.
- The outer function no longer needs `async`. The `await` is now inside the arrow function,
  which is the one marked `async`.

✅ **Checkpoint:** type `hindley` quickly. The Network tab shows **one** request
(`search?q=hindley`) instead of six.

---

## Step 5: Render the dropdown

### 5.1 Markup

Replace the debug line. The list goes **inside** `<div class="search">`, right after the input:

```svelte
<div class="search">
	<input bind:value={query} oninput={onSearchInput} placeholder="Search a rider…" />

	{#if suggestions.length > 0}
		<ul>
			{#each suggestions as rider (rider.id)}
				<li>
					<button type="button">
						{flag(rider.country)} {rider.prenom} {rider.nom}
					</button>
				</li>
			{/each}
		</ul>
	{/if}
</div>
```

- `{#if ...}` only renders the `<ul>` when there's something to show. An empty bordered box would
  look broken.
- `{#each suggestions as rider (rider.id)}` loops over the array. The `(rider.id)` part is a
  **key**: it tells Svelte which item is which when the list changes, so it updates the right
  rows instead of rebuilding them all. You already do this with `(race.id)` in the table.
- **Why a `<button>` and not a clickable `<li>`?** A button can be focused with Tab and activated
  with Enter or Space, and screen readers announce it as clickable. A clickable `<li>` gets none
  of that, and Svelte would warn you about accessibility.
- `type="button"` prevents the button from ever acting as a form "submit" button.

### 5.2 Styles (inside `<style>`)

```css
.search {
	position: relative;
}

.search input {
	width: 100%;
	padding: 6px 10px;
	box-sizing: border-box;
}

.search ul {
	position: absolute;
	top: 100%;
	left: 0;
	right: 0;
	z-index: 10;
	margin: 0;
	padding: 0;
	list-style: none;
	background: white;
	border: 1px solid #ccc;
	max-height: 300px;
	overflow-y: auto;
}

.search button {
	width: 100%;
	text-align: left;
	padding: 6px 10px;
	border: none;
	background: none;
	cursor: pointer;
}

.search button:hover {
	background: #eee;
}
```

Why each one matters:
- `.search { position: relative }` makes `.search` the **reference box** for the dropdown.
  `position: absolute` elements are placed relative to the nearest `relative` parent.
- `.search ul { position: absolute; top: 100% }` places the list **right under the input**
  (100% of the parent's height), and *takes it out of the normal flow*. It floats over the content
  below instead of pushing it down.
- `left: 0; right: 0` makes the list exactly as wide as the input.
- `z-index: 10` keeps it **on top** of the rider header and table that come later.
- `background: white` is needed because a floating element is transparent by default, so the
  content below would show through.
- `max-height` + `overflow-y: auto` → if there are many results, the list scrolls instead of
  covering the whole screen.
- `margin: 0; padding: 0; list-style: none` removes the browser's default bullets and indentation.
- `input { box-sizing: border-box }` keeps `width: 100%` from overflowing because of the padding.

✅ **Checkpoint:** type `hi`. A list of riders with flags appears under the input, floating
over the page, and rows turn grey on hover. Clicking does nothing yet.

---

## Step 6: Select a rider and load their schedule

### 6.1 Write `selectRider`

In `<script>`:

```js
async function selectRider(rider) {
	selectedRider = rider;
	query = `${rider.prenom} ${rider.nom}`;
	suggestions = [];

	const res = await fetch(`${API}/participation/rider/${rider.id}`);
	if (res.ok) {
		schedule = await res.json();
	}
}
```

- `selectedRider = rider` stores the **whole rider object**. You need `prenom`, `nom` and
  `country` for the header (Step 7) and `id` for the request.
- `query = ...` puts the full name in the input, thanks to `bind:value`.
- `suggestions = []` closes the dropdown (the `{#if}` becomes false).
- The fetch uses `rider.id`, the number returned by the search endpoint.

### 6.2 Connect it to the buttons

```svelte
<button type="button" onclick={() => selectRider(rider)}>
```

- This time you **need** an arrow function, because you want to pass an argument.
  `() => selectRider(rider)` means "when clicked, call `selectRider` with this rider".
- `onclick={selectRider(rider)}` would call `selectRider` for **every** rider immediately while
  the list renders. That's a common mistake.

### 6.3 Temporary debug

Below `<div class="search">`:

```svelte
<p>Debug: {selectedRider?.nom} has {schedule.length} races</p>
```

- `?.` is **optional chaining**: if `selectedRider` is `null`, it gives `undefined` instead of
  crashing.

✅ **Checkpoint:** click a rider. The input shows their full name, the dropdown closes, the
debug line shows their race count, and the Network tab shows `participation/rider/<id>` → 200.

---

## Step 7: Rider header with flag

Replace the debug line with:

```svelte
{#if selectedRider}
	<h2>
		{flag(selectedRider.country)} {selectedRider.prenom} {selectedRider.nom}
	</h2>
{/if}
```

- `flag()` is the function you already have. It turns `"au"` into 🇦🇺.
- Everything that depends on a rider being selected goes **inside this `{#if}`**. If you used
  `selectedRider.nom` outside it while `selectedRider` is `null`, the page would crash.

Add to `<style>`:

```css
.rider-panel h2 {
	display: flex;
	align-items: center;
	gap: 0.5rem;
}
```

- `display: flex` puts the children in a row, `align-items: center` aligns them vertically, and
  `gap` sets the spacing. This matters once you add the × button in Step 9.

> 💡 Flag emojis don't render on Windows (Chrome/Edge show two letters like "AU" instead). That's
> a Windows font limitation, not a bug in your code. They work on macOS, Linux, Android and iOS.

✅ **Checkpoint:** after clicking a rider, their flag and name appear in big text under the search bar.

---

## Step 8: The schedule table

### 8.1 Option A: copy the row markup (simplest)

Inside the `{#if selectedRider}`, **after** the `<h2>`:

```svelte
<table>
	<thead>
		<tr><th>Dates</th><th>Race</th><th>Category</th></tr>
	</thead>
	<tbody>
		{#each schedule as race (race.id)}
			<tr>
				<td>{formatDates(race)}</td>
				<td>{flag(race.country)} {race.name}</td>
				<td>{race.category}</td>
			</tr>
		{:else}
			<tr><td colspan="3">No races found for this rider.</td></tr>
		{/each}
	</tbody>
</table>
```

- The `<td>`s are identical to the left table, because the endpoint returns the same race objects.
- `{:else}` inside `{#each}` renders when the array is **empty**, for example a rider whose
  startlists haven't been scraped yet.
- `colspan="3"` makes that single cell span all three columns.

### 8.2 Option B: write the row once with a snippet (recommended)

With Option A, the `<tr>` is written twice. If you add a column later, you'd have to change it in
both places. Svelte 5 **snippets** are reusable pieces of markup.

1. Define the snippet **once**, anywhere in the markup (for example just after `</script>`):
   ```svelte
   {#snippet raceRow(race)}
   	<tr>
   		<td>{formatDates(race)}</td>
   		<td>{flag(race.country)} {race.name}</td>
   		<td>{race.category}</td>
   	</tr>
   {/snippet}
   ```
2. In **both** tables, replace the `<tr>…</tr>` inside `{#each}` with:
   ```svelte
   {@render raceRow(race)}
   ```

A snippet works like a function that returns markup: `race` is its parameter, and `{@render ...}` calls it.

### 8.3 Table styles

```css
table {
	width: 100%;
	border-collapse: collapse;
}
```

- `width: 100%` makes each table fill its column instead of shrinking to its content.
- `border-collapse: collapse` merges adjacent cell borders, if you add borders later.

✅ **Checkpoint:** click a rider. Under their name, a table lists their races in date order,
with the same formatting as the left calendar. The left calendar hasn't changed.

---

## Step 9: Close button + empty-state hint

### 9.1 `clearRider`

In `<script>`:

```js
function clearRider() {
	selectedRider = null;
	schedule = [];
	query = '';
}
```

Because all the right-side content is inside `{#if selectedRider}`, setting it to `null` makes
the header and the table disappear automatically.

### 9.2 The × button

Inside the `<h2>`, after the name:

```svelte
<button type="button" class="close" onclick={clearRider} aria-label="Close">×</button>
```

- `onclick={clearRider}` has **no** arrow function and **no** parentheses. There's no argument
  to pass, so you hand the function over directly.
- `aria-label="Close"`: screen readers would otherwise read "times" or "multiplication sign".

```css
.close {
	margin-left: auto;
	border: none;
	background: none;
	font-size: 1.5rem;
	cursor: pointer;
}
```

- `margin-left: auto` inside a flex row pushes the button to the **far right**.

### 9.3 Hint when nobody is selected

Add an `{:else}` to the `{#if selectedRider}`:

```svelte
{#if selectedRider}
	<!-- h2 + table -->
{:else}
	<p class="hint">Search a rider to see their schedule.</p>
{/if}
```

```css
.hint {
	color: #888;
}
```

✅ **Checkpoint:** on page load, the right side shows the search bar and the grey hint. Select a
rider → header + table. Click × → back to the hint, and the input is empty.

---

## Step 10: Keep the right panel visible + small screens

### 10.1 Sticky panel

The calendar is long. When you scroll down, the right panel would disappear off the top.

```css
.rider-panel {
	position: sticky;
	top: 1rem;
	max-height: calc(100vh - 2rem);
	overflow-y: auto;
}
```

- `position: sticky; top: 1rem` keeps the panel in place while you scroll, sticking 1rem from
  the top of the window.
- This only works because of `align-items: start` on `.layout` (Step 1). Otherwise the panel is
  as tall as the calendar and has no room to "stick".
- `max-height: calc(100vh - 2rem)` + `overflow-y: auto` let a long schedule scroll **inside**
  the panel instead of overflowing past the bottom of the window. `100vh` is the window height.

### 10.2 Phones and narrow windows

Two columns are too cramped on a phone, so stack them:

```css
@media (max-width: 800px) {
	.layout {
		grid-template-columns: 1fr;
	}
	.rider-panel {
		position: static;
		max-height: none;
		order: -1;
	}
}
```

- `@media (max-width: 800px)` means these rules only apply when the window is 800px wide or less.
- `grid-template-columns: 1fr` gives a single column, so the sections stack.
- `position: static` turns sticky off. A sticky panel on a phone would cover half the screen.
- `order: -1` moves the search panel **above** the calendar. Without it, you'd have to scroll
  past the whole season to find the search bar.

✅ **Checkpoint:** scroll down the calendar and check that the right panel stays visible. Then
narrow the browser window under 800px and check that the search moves to the top and everything
is one column.

---

## Step 11 (optional): Highlight the rider's races in the left calendar

This links the two sides visually: the rider's races are highlighted in the full calendar.

1. In `<script>`:
   ```js
   let scheduleIds = $derived(new Set(schedule.map((r) => r.id)));
   ```
   - `schedule.map((r) => r.id)` turns the schedule into a list of ids, for example `[1, 5, 9]`.
   - `new Set(...)` makes the `.has(id)` lookups fast.
   - `$derived(...)` is the rune for **computed values**: Svelte recomputes it every time
     `schedule` changes. Don't assign it yourself.
2. On the **left** table's `<tr>`:
   ```svelte
   <tr class:highlight={scheduleIds.has(race.id)}>
   ```
   - `class:highlight={condition}` adds the CSS class `highlight` only when the condition is true.
3. CSS:
   ```css
   .highlight {
   	background: #fff3c4;
   }
   ```

If you used the snippet (Option B), add a second parameter so only the left table gets highlighted:
- `{#snippet raceRow(race, highlight)}` and `<tr class:highlight>`. When the variable has the same
  name as the class, `class:highlight` is shorthand for `class:highlight={highlight}`.
- Left table: `{@render raceRow(race, scheduleIds.has(race.id))}`
- Right table: `{@render raceRow(race, false)}`

✅ **Checkpoint:** select a rider. Their races turn yellow in the left calendar, and clicking ×
removes the highlight.

---

## Step 12 (optional): Polish

These aren't required, but each one makes the page more pleasant to use.

**12.1 "Loading…" while the schedule is fetched**
- Add `let loading = $state(false);`
- In `selectRider`: `loading = true;` before the fetch and `loading = false;` after it.
- In the markup, inside `{#if selectedRider}`: `{#if loading} <p>Loading…</p> {:else} <table>…</table> {/if}`

**12.2 Error message when the backend is down**
- Add `let errorMsg = $state('');`
- Wrap the fetches in `try { ... } catch { errorMsg = 'Backend unreachable'; }`. `fetch`
  *throws* when the server can't be reached at all, which is different from `res.ok` being false.
- Show it with `{#if errorMsg}<p class="error">{errorMsg}</p>{/if}`.

**12.3 Close the dropdown with Escape**
- On the input: `onkeydown={(e) => { if (e.key === 'Escape') suggestions = []; }}`

**12.4 Stale responses**
If a slow response for `hin` arrives *after* the one for `hindley`, it overwrites the right
results with older ones. The debounce makes this rare. To fully prevent it, inside the
`setTimeout` callback, before assigning `suggestions`, check that the text hasn't changed in
the meantime: `if (q === query.trim()) suggestions = ...`.

---

## Final file structure (for orientation)

```
<script>
	let { data } = $props();
	const API = ...;

	// state:        query, suggestions, selectedRider, schedule  ($state)
	// plain:        timer
	// derived:      scheduleIds                                   (optional, step 11)

	// existing:     formatDate, formatDates, flag
	// new:          onSearchInput, selectRider, clearRider
</script>

{#snippet raceRow(race)} ... {/snippet}          ← optional, step 8.2

<div class="layout">
	<section class="calendar">
		<h1>Season calendar</h1>
		<table> ... {#each data.races ...} ... </table>
	</section>

	<aside class="rider-panel">
		<div class="search">
			<input ... />
			{#if suggestions.length > 0} <ul> ... </ul> {/if}
		</div>

		{#if selectedRider}
			<h2> flag + name + × </h2>
			<table> ... {#each schedule ...} ... </table>
		{:else}
			<p class="hint"> ... </p>
		{/if}
	</aside>
</div>

<style>
	.layout, .calendar, .rider-panel, .search ..., table, h2, .close, .hint,
	.highlight, @media (max-width: 800px) { ... }
</style>
```

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Red **CORS** error in Console | You're not on `localhost:5173` exactly, or Spring wasn't restarted after `@CrossOrigin` was added |
| `search` request → **404** | Spring Boot wasn't restarted, or there's a typo in the URL (`/api/rider/search`, singular `rider`) |
| Request → **500** | Look at the Spring Boot logs; the SQL is printed because of `show-sql=true` |
| `Failed to fetch` / `ERR_CONNECTION_REFUSED` | Spring Boot isn't running |
| No request at all when typing | `oninput` missing, written as `oninput={onSearchInput()}`, or fewer than 2 characters typed |
| Every rider's schedule loads at once when the list appears | `onclick={selectRider(rider)}` → use `onclick={() => selectRider(rider)}` |
| Dropdown pushes the content down | `position: absolute` missing on `.search ul` |
| Dropdown appears in a weird place | `position: relative` missing on `.search` |
| Dropdown hidden behind the table / see-through | `z-index` or `background: white` missing on `.search ul` |
| Page crashes with `Cannot read properties of null (reading 'nom')` | `selectedRider.something` used outside `{#if selectedRider}` |
| Input doesn't show the rider's name after clicking | `bind:value={query}` missing (you used `value={query}`, which is one-way) |
| Right panel doesn't stay visible when scrolling | `align-items: start` missing on `.layout` |
| Flags show as "AU", "FR"… | Windows doesn't ship flag emojis; this is normal on that OS |

## Pitfall: don't close the dropdown on `onblur`

It's tempting to add `onblur={() => suggestions = []}` on the input so the dropdown closes when you
click elsewhere. But **blur fires before click**: when you press the mouse on a suggestion, the
input loses focus first, the dropdown disappears, and the click lands on nothing. If you add this
later, change the suggestion buttons from `onclick` to `onmousedown`, which fires *before* the blur.
