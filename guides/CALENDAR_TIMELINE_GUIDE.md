# Timeline calendar (Gantt style): frontend + small backend guide

**Files you'll edit:**

| File | What changes |
|---|---|
| `frontend/src/routes/calendrier_cycliste/+page.svelte` | Almost everything: the table becomes a timeline, the rider tables become timeline rows |
| `frontend/src/routes/calendrier_cycliste/+page.server.js` | Step 8 only: loads the preselected riders |
| `backend/.../rider/RiderController.java` + `RiderService.java` | Step 8 only: a new endpoint to find riders by URL |

**Goal (based on `calendar.jpg`):**

```
               12/02      19/02      26/02      05/03      12/03
             ┌────────────────────────────────────────────────────┐
 Calendrier (‹)│🇫🇷 ▇▇▇  🇪🇸 Andalu ▇▇▇▇     🇫🇷 🇧🇪   🇫🇷 Paris - Nice ▇▇ │(›)  ← 4 lanes,
             │ 🇦🇪 UAE Tour ▇▇▇▇▇▇           🇮🇹 Tirreno ▇▇▇▇▇▇▇▇ │      always the
             │            🇫🇷                                    │      whole season
             │                                                  │
             └────────────────────────────────────────────────────┘
 [Search…   ]
 × 🇦🇺 Jai HINDLEY │        🇪🇸 Andalu ▇▇▇▇        🇫🇷 Paris - Nice ▇▇ │  ← one row per rider,
 × 🇳🇿 L. PITHIE   │ 🇦🇪 UAE Tour ▇▇▇                🇫🇷 Paris - Nice ▇ │    same columns as the
                  └────────────────────────────────────────────────────┘    top calendar, no gap
                                                                            between rows
```

- The top calendar shows **35 days** (5 weeks, roughly a month) and has **4 lanes**, so up to 4
  races running at the same time are all visible.
- `‹` and `›` move the window **one day** back or forward. Every timeline on the page moves together.
- The search bar sits in the left column. Each rider you pick adds a row **below the previous ones**,
  with a × to remove it.
- A list of riders written in the code (`PRESET_RIDERS`) is loaded automatically when the page opens.

**What stays the same:** the search logic (`onSearchInput`, debounce, dropdown) and the two
endpoints `GET /api/rider/search` and `GET /api/participation/rider/{id}`. You only change how the
results are **displayed**.

**What goes away:** the season `<table>`, the per-rider `<table>`s, `isOngoing`, `isThisWeek`, the
`.layout` two-column grid and the mobile "Ouvrir calendrier" toggle. Delete them as you reach the
steps that replace them (Step 4 and Step 7), not before, so the page keeps working while you build.

The steps below build the page **in small pieces**. Each step ends with a ✅ **Checkpoint**:
check it in the browser before moving on.

---

## Step 0: Before you start

1. Spring Boot is running and `curl http://localhost:8080/api/races` returns the season.
2. The frontend dev server is running: `cd frontend && npm run dev`.
3. Open `http://localhost:5173/calendrier_cycliste` with **DevTools (F12)** open.
4. Commit your current state (`git commit -am "before timeline"`). This is a big rewrite of the
   page, so it's nice to be able to compare or go back.

✅ **Checkpoint:** the page works exactly as before.

---

## Step 1: Dates as day numbers

A timeline is all about "how many days between X and Y". JavaScript `Date` objects make that
painful (time zones, summer time, months of different lengths). The trick: turn every date into a
**plain integer**, the number of days since 1 January 1970. Then:

- the number of days between two dates is a subtraction,
- "the day after" is `+ 1`,
- comparing dates is `<` / `>`.

### 1.1 Helpers

In `<script>`, next to `formatDate` and `flag`:

```js
const DAY_MS = 24 * 60 * 60 * 1000;   // milliseconds in one day

// "2027-02-15" -> 20864 (days since 1970-01-01)
function toDay(iso) {
	const [y, m, d] = iso.split('-').map(Number);
	return Date.UTC(y, m - 1, d) / DAY_MS;
}

// today's date as a day number
function todayDay() {
	const t = new Date();
	return Date.UTC(t.getFullYear(), t.getMonth(), t.getDate()) / DAY_MS;
}

// 20864 -> "15/02"
function dayLabel(day) {
	const d = new Date(day * DAY_MS);
	const dd = String(d.getUTCDate()).padStart(2, '0');
	const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
	return `${dd}/${mm}`;
}

function isMonday(day) {
	return new Date(day * DAY_MS).getUTCDay() === 1;   // 0 = Sunday, 1 = Monday…
}
```

- `.map(Number)` turns `["2027","02","15"]` into `[2027, 2, 15]`.
- `Date.UTC(...)` returns milliseconds since 1970 **in UTC**. Using UTC everywhere is what removes
  the time-zone and summer-time problems: in UTC, every day is exactly `DAY_MS` long, so the
  division always gives a whole number.
- `m - 1` because JavaScript months start at 0 (January = 0). Yes, really.
- That's also why `dayLabel` and `isMonday` use the `getUTC…` methods and not `getDate()` /
  `getDay()`: the local versions could shift the result by one day depending on your time zone.

### 1.2 Try them

Temporarily, at the top of the markup:

```svelte
<p>Debug: {toDay('2027-02-15')} → {dayLabel(toDay('2027-02-15'))}, monday: {isMonday(toDay('2027-02-15'))}</p>
```

✅ **Checkpoint:** you see `20864 → 15/02, monday: true` (15 February 2027 is a Monday). Remove
the debug line.

---

## Step 2: The visible window + the date header

### 2.1 State

In `<script>`:

```js
const DAYS = 35;          // width of the window, in days
const MAIN_LANES = 4;     // number of lines in the top calendar

// day number of the 1st of the month that contains `day`
function firstOfMonth(day) {
	const d = new Date(day * DAY_MS);
	return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1) / DAY_MS;
}

const today = todayDay();
const initialStart = firstOfMonth(today);

let start = $state(initialStart);
let end = $derived(start + DAYS - 1);
let days = $derived(Array.from({ length: DAYS }, (_, i) => start + i));
```

- `start` is the **first day shown** (a day number). It's the only thing the arrows will change.
- `firstOfMonth` takes the year and month of a day and rebuilds the date with day `1`. So when you
  open the page on 24 November, the calendar starts on 1 November.
- 35 days from the 1st always covers the whole month (the longest month has 31 days) plus the
  first few days of the next one.
- `initialStart` is kept in its own constant because the "Aujourd'hui" button (Step 9.4) uses it
  to come back to this view.
- `end` and `days` are `$derived`: they're recomputed automatically each time `start` changes.
- `Array.from({ length: DAYS }, (_, i) => start + i)` builds `[start, start+1, …, start+34]`.
  The `_` is the (unused) first argument; only the index `i` matters.

> **Before the season starts**, the current month has no races (in November 2026, the season starts
> in January 2027), so the page opens on 5 empty weeks and you have to click `›` many times. If you
> want to avoid that, start on the 1st of the season's first month when the season hasn't started yet:
> ```js
> const seasonStart = data.races.length > 0 ? toDay(data.races[0].startDate) : today;
> const initialStart = firstOfMonth(Math.max(today, seasonStart));
> ```
> `Math.max` picks the later of the two days: today during the season, the first race before it.
> Svelte may then warn *"This reference only captures the initial value of `data`"*. That's what
> you want here (computed once), so you can ignore it.

### 2.2 The row structure

Every horizontal band on the page (date header, top calendar, search bar, each rider) will be a
**row** with the same 4 columns, so the timelines line up perfectly:

```
┌──────────────┬────┬──────────────────────────────┬────┐
│ 1: label     │ 2: │ 3: timeline (35 days)        │ 4: │
│ (220px)      │ ‹  │                              │ ›  │
└──────────────┴────┴──────────────────────────────┴────┘
```

Under the existing markup (keep the old table for now), add:

```svelte
<div class="planning">
	<div class="row">
		<div class="dates" style="--days: {DAYS}">
			{#each days as day, i (day)}
				{#if isMonday(day)}
					<span style="grid-column: {i + 1}">{dayLabel(day)}</span>
				{/if}
			{/each}
		</div>
	</div>
</div>
```

- `style="--days: {DAYS}"` creates a **CSS variable** on that element. The CSS below reads it
  with `var(--days)`, so the number 35 is written only once, in JavaScript.
- `grid-column: {i + 1}` places each label in the column of its day. CSS grid columns start at 1,
  while `i` starts at 0, hence the `+ 1`.
- Only Mondays get a label, like `12/02`, `19/02`… in the image.

### 2.3 Styles

```css
.planning {
	padding-inline: 2rem;
}

.row {
	display: grid;
	grid-template-columns: 220px 2rem 1fr 2rem;
	column-gap: 0.5rem;
	align-items: stretch;
}

.dates {
	grid-column: 3;
	display: grid;
	grid-template-columns: repeat(var(--days), minmax(0, 1fr));
	font-weight: bold;
	padding-bottom: 4px;
}

.dates span {
	white-space: nowrap;
}
```

- `.row` is the 4-column grid from the drawing. `1fr` gives the timeline all the remaining width.
- `.dates { grid-column: 3 }` puts the header in the timeline column. It is **itself** a grid too,
  with 35 equal columns, one per day. An element can be a grid item (of `.row`) and a grid
  container (for its own children) at the same time.
- `repeat(var(--days), minmax(0, 1fr))` means "35 columns of equal width". **Use `minmax(0, 1fr)`,
  not just `1fr`**: a plain `1fr` column refuses to get narrower than its content, so a long race
  name would make its columns wider than the others and break the alignment between rows.
- `white-space: nowrap` lets `12/02` overflow its (narrow) day column onto the next ones instead
  of wrapping onto two lines.

✅ **Checkpoint:** under the old table, a line of dates appears, one label per Monday, 5 labels in
total, starting about 220px from the left.

---

## Step 3: Spread races over lanes

When two races overlap in time, they can't be on the same line. You need to give each race a
**lane** (a line number): 0, 1, 2 or 3.

The classic method (it's the same problem as booking meeting rooms):
1. Go through the races **sorted by start date**.
2. For each race, put it in the **first lane that is free**, meaning the last race in that lane
   ended before this one starts.
3. If no lane is free, open a new one. If you've reached the maximum, the race is hidden.

This method is proven to use the smallest possible number of lanes. With the current data, at most
3 races overlap on the same day, so the 4 lanes of the top calendar are always enough.

### 3.1 `assignLanes`

```js
function assignLanes(races, maxLanes = Infinity) {
	const sorted = [...races].sort((a, b) => a.startDate.localeCompare(b.startDate));
	const laneEnds = [];   // laneEnds[k] = last day used in lane k
	const placed = [];
	let hidden = 0;

	for (const race of sorted) {
		const s = toDay(race.startDate);
		const e = toDay(race.endDate);

		let lane = laneEnds.findIndex((lastDay) => lastDay < s);
		if (lane === -1) {
			lane = laneEnds.length;
			laneEnds.push(e);
		} else {
			laneEnds[lane] = e;
		}
		placed.push({ race, lane, s, e });
	}

	return { placed, laneCount: Math.max(1, laneEnds.length), hidden };
}
```

- `[...races]` makes a **copy** before sorting. `.sort()` modifies the array it's called on, and
  you don't want to reorder `data.races` itself.
- `localeCompare` compares two strings. ISO dates (`"2027-02-15"`) sort correctly as plain text,
  that's one of the reasons that format exists.
- `findIndex(...)` returns the index of the first lane whose last day is **strictly before** the
  start of this race, or `-1` if there isn't one. Strictly (`<`) because a race ending on the 15th
  and one starting on the 15th do overlap on the 15th.
- Each placed race keeps its start/end day numbers (`s`, `e`) so you don't recompute them while rendering.
- `laneCount` is at least 1, so a rider with no races still gets a (empty) row.
- `maxLanes = Infinity` is a **default parameter**: riders get as many lanes as they need (almost
  always 1); the top calendar passes `MAIN_LANES`.

### 3.2 Compute it once for the season

```js
let mainLayout = $derived(assignLanes(data.races, MAIN_LANES));
```

**Lanes are computed on the whole season, not just on the visible window.** If you only laid out
the visible races, a race would jump from one line to another while you click the arrows, as
other races enter and leave the window. Computing it once on everything keeps every race on the
same line all the time.

### 3.3 Try it

Temporarily: `<p>Debug: {mainLayout.placed.length} placed, {mainLayout.laneCount} lanes, {mainLayout.hidden} hidden</p>`

✅ **Checkpoint:** all races are placed, `3 lanes`, `0 hidden` (with the current `Races.json`: 81
races). Remove the debug line.

---

## Step 4: The timeline snippet + the top calendar

The same timeline is drawn in the top calendar and in every rider row, so write it **once** as a
snippet (like `raceRow` in the rider search guide).

### 4.1 Position helpers

In `<script>`:

```js
// grid column (1…DAYS) of a day; days outside the window are clamped to the edges
function col(day) {
	return Math.min(Math.max(day - start, 0), DAYS - 1) + 1;
}

function isVisible(p) {
	return p.e >= start && p.s <= end;
}
```

- `day - start` is "how many days after the first visible day", so `0` for the first column.
- `Math.max(…, 0)` and `Math.min(…, DAYS - 1)` **clamp** the value: a race that started before
  the window is drawn from the first column, a race that ends after it is drawn up to the last
  column. That's how Paris-Nice gets cut at the right edge in the image.
- `isVisible`: a race is (at least partly) visible when it ends after the window starts **and**
  starts before the window ends. Drawing the others would create grid items in the wrong place.

### 4.2 The snippet

Just after `</script>`:

```svelte
{#snippet timeline(layout, rows)}
	<div class="timeline" style="--days: {DAYS}; --rows: {rows}">
		{#each days as day, i (day)}
			<div
				class="day"
				class:monday={isMonday(day)}
				class:today={day === today}
				style="grid-column: {i + 1}"
			></div>
		{/each}

		{#each layout.placed as p (p.race.id)}
			{#if isVisible(p)}
				<div
					class="race"
					style="grid-column: {col(p.s)} / {col(p.e) + 1}; grid-row: {p.lane + 1}"
				>
					{flag(p.race.country)} {p.race.name}
				</div>
			{/if}
		{/each}
	</div>
{/snippet}
```

- The snippet takes the result of `assignLanes` and the number of rows to draw.
- The first loop draws one **background cell per day**. These cells draw the vertical lines and
  a faint "today" highlight (made much more visible in Step 5.5). They span every row (see the CSS).
- The second loop draws the races on top. `grid-column: 3 / 8` means "from the start of column 3
  to the start of column 8", so columns 3 to 7. That's why the end is `col(p.e) + 1`.
- `grid-row: {p.lane + 1}`: lane 0 is grid row 1, and so on.
- Day cells and races share the same grid cells. That's allowed: unlike tables, grid items can
  overlap.

### 4.3 The top calendar row

Inside `.planning`, after the dates row:

```svelte
<div class="row">
	<div class="label">Calendrier</div>
	{@render timeline(mainLayout, MAIN_LANES)}
</div>
```

`MAIN_LANES` (4) is passed as the row count, not `mainLayout.laneCount` (3): you asked for 4 lines
in the top calendar, even when only 3 are used.

### 4.4 Styles

```css
.label {
	grid-column: 1;
	display: flex;
	align-items: center;
	gap: 0.4rem;
	font-weight: bold;
}

.timeline {
	grid-column: 3;
	display: grid;
	grid-template-columns: repeat(var(--days), minmax(0, 1fr));
	grid-template-rows: repeat(var(--rows), 28px);
	background: #262b31;
	border: 1px solid #555;
}

.day {
	grid-row: 1 / -1;
	border-left: 1px solid #ffffff12;
}

.day.monday {
	border-left-color: #ffffff40;
}

.day.today {
	background: #ffffff18;
}

.race {
	z-index: 1;
	margin: 2px 0;
	padding: 0 4px;
	background: #3d5a8c;
	font-size: 0.8rem;
	line-height: 24px;
	white-space: nowrap;
	overflow: hidden;
}
```

- `.timeline` has the same `grid-column: 3` and the same column template as `.dates`, which is
  what makes the date labels sit exactly above their days.
- `grid-template-rows: repeat(var(--rows), 28px)` creates **exactly** `--rows` rows. This matters
  for the next rule:
- `.day { grid-row: 1 / -1 }` means "from the first row line to the **last** one", so the cell is
  as tall as the whole timeline. `-1` only refers to rows declared in `grid-template-rows`, which
  is why the rows must be declared explicitly.
- `#ffffff12` is white with low opacity (the last two hex digits are the alpha), which gives the
  faint grid lines of the image on any dark background. Monday lines are stronger to mark the weeks.
- `.race { z-index: 1 }` keeps races above the day cells. On grid items, `z-index` works without
  `position: relative`.
- `overflow: hidden` + `white-space: nowrap`: a name that doesn't fit is cut instead of wrapping
  and making the row taller.

### 4.5 Remove the old season table

Now that the top calendar shows the same data, delete:
- the `<section class="calendar">` with the season `<table>`, and the `calendarOpen` state + toggle button,
- `isOngoing` and `isThisWeek` (the "today" highlight of Step 5.5 replaces them),
- the CSS for `tr.ongoing`, `tr.isthisweek`, `.toggle`, `.calendar.collapsed`.

Keep the `<aside class="rider-panel">` for now: it still works and you'll move its content in
Step 7. Also keep `.layout` for the moment, but take `.planning` **out** of it (put
`<div class="planning">` above `<div class="layout">`), so the timeline gets the full width.

✅ **Checkpoint:** the top calendar shows 5 weeks of races as blue bars on 4 lines (the 4th one
empty or nearly), with week lines aligned under the Monday dates. Compare a few bars with the dates
in `Races.json`: the UAE Tour (15/02 → 21/02) should start exactly on the `15/02` line.

---

## Step 5: Make the races look like the image

### 5.1 Colour per category

The data has these categories: `UWT`, `Pro`, `Monument`, `TDF`, `GIRO`, `VUELTA`. In `<script>`:

```js
function categoryClass(category) {
	if (['TDF', 'GIRO', 'VUELTA'].includes(category)) return 'gt';
	if (category === 'Monument') return 'monument';
	if (category === 'UWT') return 'uwt';
	return 'pro';
}
```

On the race `<div>`:

```svelte
<div class="race {categoryClass(p.race.category)}" ...>
```

- `class="race {…}"` mixes a fixed class and a computed one: the element gets `race uwt`, `race pro`…
- Grand Tours are grouped into one class because they should look the same.

```css
.race.uwt      { background: #3d5a8c; }
.race.pro      { background: #5b7a2b; }
.race.monument { background: #7a3d8c; }
.race.gt       { background: #a0522d; }
```

Choose any colours you like; these are close to the blue / green of the image.

### 5.2 The gold end marker

In the image, every race ends with a thin orange line. Add to `.race`:

```css
border-right: 3px solid #e0a526;
```

That also separates two races that follow each other in the same lane.

> A race cut by the right edge of the window still shows the marker, even though it doesn't really
> end there. To hide it, add `class:cut-end={p.e > end}` on the race and
> `.race.cut-end { border-right: none; }`.

### 5.3 Short races: flag only

A one-day race is one column wide, about 25px: its name can't fit. Like in the image, show only
the flag when the visible part is shorter than 3 days.

In `<script>`:

```js
function visibleDays(p) {
	return Math.min(p.e, end) - Math.max(p.s, start) + 1;
}
```

In the snippet, replace `{flag(p.race.country)} {p.race.name}` with:

```svelte
<span class="flag">{flag(p.race.country)}</span>
{#if visibleDays(p) >= 3}
	<span class="name">{p.race.name}</span>
{/if}
```

- It counts **visible** days, not the full length, so a stage race that is almost out of the
  window also falls back to the flag only.

```css
.race {
	display: flex;
	align-items: center;
	gap: 4px;
}

.race .name {
	overflow: hidden;
	text-overflow: ellipsis;
}
```

- `text-overflow: ellipsis` adds `…` when the name is cut. It only works on the element that has
  `overflow: hidden`, hence the rule on `.name`.

### 5.4 Tooltip with the full info

Since short races only show a flag, you need a way to know which race it is. Add a `title` on the
race `<div>`:

```svelte
title="{p.race.name} · {formatDates(p.race)} · {p.race.category}"
```

The browser shows `title` as a tooltip on hover. `formatDates` is your existing function.

### 5.5 Make today stand out

The `.day.today` background from Step 4.4 is faint, and it's **under** the races (`z-index: 1`),
so on a day with races you can't see it at all. Two additions fix that: a coloured band drawn
**over** everything in every timeline, and a marker in the date header.

**1. The band, in the snippet.** After the `{#each layout.placed …}` loop, still inside
`<div class="timeline">`:

```svelte
{#if today >= start && today <= end}
	<div class="today-line" style="grid-column: {today - start + 1}"></div>
{/if}
```

- `{#if …}`: when today is outside the window (after a few clicks on the arrows), the band isn't
  drawn. Without it, `grid-column` would be 0 or negative and the band would land in the wrong place.
- `today - start + 1` is today's column, the same formula as in `col()`, but without clamping.
- It's in the snippet, so it appears in the top calendar **and** in every rider row: you see at
  a glance which race each rider is doing today.

```css
.today-line {
	grid-row: 1 / -1;
	z-index: 2;
	pointer-events: none;
	background: #ff5c5c30;
	border-left: 2px solid #ff5c5c;
	border-right: 2px solid #ff5c5c;
}
```

- `z-index: 2` puts the band **above** the races (`z-index: 1`). It's translucent
  (`30` alpha at the end of the colour), so the race under it stays readable, just tinted red.
- `pointer-events: none` makes the band "transparent to the mouse": hovering it shows the
  tooltip of the race underneath instead of nothing.
- Red is a colour nothing else on the page uses (races are blue/green/purple/brown, the end
  markers and arrows are gold), so it can't be confused with anything. Change `#ff5c5c` in the 3
  places if you prefer another colour.

You can now delete `class:today={day === today}` on the day cells and the `.day.today` CSS rule:
the band replaces them.

**2. The marker in the header.** Give `.dates` a second row and put a small `▼` above today's
column. In the dates loop (Step 2.2):

```svelte
{#each days as day, i (day)}
	{#if isMonday(day)}
		<span style="grid-column: {i + 1}; grid-row: 1">{dayLabel(day)}</span>
	{/if}
	{#if day === today}
		<span class="today-mark" style="grid-column: {i + 1}; grid-row: 2">▼</span>
	{/if}
{/each}
```

```css
.dates .today-mark {
	color: #ff5c5c;
	text-align: center;
	font-size: 0.7rem;
	line-height: 1;
}
```

- The labels go on row 1 and the marker on row 2, so even when today is a Monday, the marker
  doesn't overlap the `dd/mm` label. The grid creates the second row by itself as soon as an item
  asks for `grid-row: 2`.
- `text-align: center` centres the `▼` in its day column, so it points exactly at the band.

✅ **Checkpoint:** races have colours per category, a gold line at the end, one-day races are just
a flag, and hovering any race shows its name, dates and category. Today's column has a red band
across the top calendar and every rider row, with a red `▼` above it, and hovering a race under
the band still shows its tooltip. Click `›` until today leaves the window: the band and the `▼`
disappear.

> To check this while the season hasn't started, temporarily replace `const today = todayDay();`
> with `const today = toDay('2027-02-17');` (the middle of the UAE Tour). Put it back afterwards.

---

## Step 6: The arrows

### 6.1 Markup

In the top calendar row, around the timeline:

```svelte
<div class="row">
	<div class="label">Calendrier</div>
	<button type="button" class="arrow" onclick={() => (start -= 1)} aria-label="Jour précédent">‹</button>
	{@render timeline(mainLayout, MAIN_LANES)}
	<button type="button" class="arrow" onclick={() => (start += 1)} aria-label="Jour suivant">›</button>
</div>
```

- `start -= 1` is all it takes. `end`, `days`, `col()`, `isVisible()` and `visibleDays()` all
  depend on `start`, so every timeline on the page re-renders one day earlier.
- The parentheses in `() => (start -= 1)` aren't required, but they make it clear you're running
  an assignment, not returning a value.
- In this row the items are placed **in order** in columns 1, 2, 3, 4: label, `‹`, timeline, `›`.
  `.timeline` has `grid-column: 3` anyway, so it can't land in the wrong column.

### 6.2 Styles

```css
.arrow {
	align-self: center;
	width: 2rem;
	height: 2rem;
	border-radius: 50%;
	border: 1px solid #e0a526;
	background: none;
	color: #e0a526;
	font-size: 1.2rem;
	line-height: 1;
	cursor: pointer;
}

.arrow:hover {
	background: #e0a52622;
}
```

- `align-self: center` centres the button vertically in the row. Without it, the button would be
  stretched to the full height of the 4 lanes (because of `align-items: stretch` on `.row`).
- `border-radius: 50%` on a square makes a circle, like the arrows in the image.

### 6.3 (Optional) Shift-click jumps a week

```svelte
onclick={(e) => (start -= e.shiftKey ? 7 : 1)}
```

`e` is the click event; `e.shiftKey` is `true` when Shift is held. `cond ? a : b` means "a if
cond, else b". Same thing with `+=` on the other arrow.

✅ **Checkpoint:** clicking `›` slides every bar and every date label one column to the left.
Clicking `‹` 7 times moves back exactly one week (the Monday labels are back on the same lines).
No race ever changes line while you click.

---

## Step 7: Rider rows under the calendar

### 7.1 Store riders as objects

Your current code stores each rider as an array: `entry[0]` is the rider, `entry[1]` is the
races. That works, but `entry.rider` and `entry.races` are much easier to read. Change
`selectRider` and `removeRider`:

```js
async function selectRider(rider) {
	suggestions = [];
	query = '';
	if (selectedRiders.some((entry) => entry.rider.id === rider.id)) return;

	const res = await fetch(`${PUBLIC_API_URL}/participation/rider/${rider.id}`);
	if (res.ok) {
		selectedRiders.push({ rider, races: await res.json() });
	}
}

function removeRider(id) {
	selectedRiders = selectedRiders.filter((entry) => entry.rider.id !== id);
}
```

- `{ rider, races: … }` is shorthand for `{ rider: rider, races: … }`.
- `selectedRiders.push(...)` updates the page because `$state` arrays are reactive in Svelte 5,
  including `push`.
- New riders are pushed at the **end**, so each one appears below the previous ones.
- `removeRider` now takes the id directly: there's nothing else it needs.

### 7.2 The search row

Inside `.planning`, after the top calendar row, **move** your existing `<div class="search">`
(input + dropdown, unchanged) into a new row:

```svelte
<div class="row search-row">
	<div class="search">
		<!-- your existing <input …> and {#if suggestions.length > 0} <ul> … {/if} -->
	</div>
</div>
```

and update the CSS of the search:

```css
.search-row {
	margin-top: 1.5rem;
	margin-bottom: 0.5rem;
}

.search {
	grid-column: 1;
	position: relative;
}

.search input {
	width: 100%;
	padding: 6px 10px;
	box-sizing: border-box;
}

.search ul {
	min-width: 100%;
	width: max-content;
	/* keep the rest of your existing rules: position absolute, top 100%, z-index… */
}
```

- `grid-column: 1` puts the search in the label column, above the rider names.
- The input is now `100%` of that 220px column, instead of `50%` of a half-page.
- `width: max-content` lets the dropdown be wider than the input when a name is long, with
  `min-width: 100%` so it's never narrower. Remove `right: 0` from `.search ul` if you have it,
  otherwise it forces the width back to the input's.

### 7.3 One row per rider

Right after the search row:

```svelte
{#each selectedRiders as entry (entry.rider.id)}
	{@const layout = assignLanes(entry.races)}
	<div class="row rider-row">
		<div class="label">
			<button
				type="button"
				class="close"
				aria-label="Retirer {entry.rider.prenom} {entry.rider.nom}"
				onclick={() => removeRider(entry.rider.id)}
			>
				×
			</button>
			<span class="rider-name">{flag(entry.rider.country)} {entry.rider.prenom} {entry.rider.nom}</span>
		</div>
		{@render timeline(layout, layout.laneCount)}
	</div>
{:else}
	<div class="row">
		<p class="label hint">Cherche un coureur pour afficher son programme.</p>
	</div>
{/each}
```

- The × comes **before** the name, so it sits at the far left of the label column, where the ⓘ
  circle is in the image. `.label` is a flex row (Step 4.4), so the children simply line up from
  left to right in the order you write them.
- `{@const …}` declares a local constant inside a block. Here it's the lane layout of **this**
  rider's races, recomputed if the list changes.
- `assignLanes(entry.races)` without a max: a rider who is on two overlapping startlists gets a
  2-line row instead of losing a race.
- The row has no arrows: columns 2 and 4 stay empty, but because the grid is identical, the
  timeline is exactly under the top calendar. The arrows at the top move every row.
- `{:else}` shows the hint when nobody is selected.

### 7.4 Styles

```css
.rider-row {
	margin: 0;
}

.rider-row + .rider-row .timeline {
	border-top: none;
}

.rider-row .label {
	font-weight: normal;
	border-bottom: 1px solid #ffffff12;
}

.rider-name {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.close {
	flex-shrink: 0;
	width: 1.4rem;
	padding: 0;
	border: none;
	background: none;
	color: #e0a526;
	font-size: 1.3rem;
	line-height: 1;
	cursor: pointer;
}

.close:hover {
	color: #ff6b6b;
}

.hint {
	color: #888;
	font-weight: normal;
}
```

**No gap between riders:**
- Rows touch each other because nothing adds space between them: `.row` only has a `column-gap`
  (between columns), no `row-gap` and no margin. `margin: 0` on `.rider-row` makes that explicit,
  so a margin added later to `.row` doesn't separate the riders.
- Each timeline has a 1px border all around, so two stacked timelines would show a **double**
  line between them (1px bottom of the first + 1px top of the second). `.rider-row + .rider-row`
  means "a rider row that comes right after another rider row": for those, the top border is
  removed and the line between two riders is 1px, like in the image. The first rider keeps its
  top border, since it follows the search row, not a rider row.
- The faint `border-bottom` on the label continues each separator line into the name column, as
  in the image.

**The × on the left:**
- `flex-shrink: 0` and a fixed `width`: the × never gets squeezed by a long name, and every name
  starts at the same position, whatever the rider.
- `padding: 0` removes the browser's default button padding, which would push the name to the right.
- Gold like the arrows, red on hover to make it clear that it removes something.

### 7.5 Remove the old rider panel

Delete the now empty `<aside class="rider-panel">`, the `<div class="layout">` around it, and the
CSS for `.layout`, `.rider-panel h2`, `th`, `td`, `table` and the old `@media` block. You'll write
a new `@media` rule in Step 9.

✅ **Checkpoint:** search a rider and click them: a row appears under the search bar with their
× on the left, then flag and name, and their races, aligned with the top calendar. Add a second rider: they appear
below the first one. Click `›`: every row moves together. Click × on the first rider: only that
row disappears.

---

## Step 8: Preselected riders

You want some riders loaded automatically, with the list written in the code.

**Which identifier to use?** Not the `id`: `RaceService.importRaces` empties the `riders` table
at the start of every new season, so the ids change every year. The `riderUrl`
(`"rider/jai-hindley"`) comes from ProCyclingStats and never changes. You'll find it in
`scraper/Startlist.json`, or in the search response (Network tab → Response).

The backend can't find a rider by URL yet, so this step has a small backend part.

### 8.1 Backend: `GET /api/rider/by-urls`

`RiderRepository` already has `findAllByRiderUrlIn` (used by the startlist import). Add to
`RiderService`:

```java
public List<RiderDTO> findByUrls(List<String> urls) {
    return riderRepository.findAllByRiderUrlIn(urls).stream()
            .map(r -> new RiderDTO(r.getId(), r.getRiderUrl(), r.getPrenom(), r.getNom(), r.getCountry()))
            .toList();
}
```

And to `RiderController`:

```java
// Called by +page.server.js: GET /api/rider/by-urls?urls=rider/jai-hindley&urls=rider/laurence-pithie
@GetMapping("/by-urls")
public List<RiderDTO> findByUrls(@RequestParam("urls") List<String> urls) {
    return riderService.findByUrls(urls);
}
```

- `@RequestParam List<String>` collects **every** `urls=` parameter of the query string into a
  list. Spring also accepts a comma-separated list (`?urls=a,b`), but repeating the parameter is
  safer if a URL ever contains a comma.
- It returns `RiderDTO`s, the same shape as the search endpoint, so the frontend treats preset
  riders and searched riders the same way.

Restart Spring Boot, then:

```
curl "http://localhost:8080/api/rider/by-urls?urls=rider/jai-hindley&urls=rider/laurence-pithie"
```

You should get two riders. URLs that don't exist are simply ignored.

### 8.2 Frontend: load them on the server

In `+page.server.js`, at the top (after `API_URL`):

```js
// Riders loaded automatically on the calendar page. Edit this list freely.
// Use the riderUrl (see scraper/Startlist.json), not the id: ids change every season.
const PRESET_RIDERS = [
	'rider/jai-hindley',
	'rider/laurence-pithie',
];

async function loadPresets(fetch) {
	if (PRESET_RIDERS.length === 0) return [];

	try {
		const params = new URLSearchParams();
		for (const url of PRESET_RIDERS) params.append('urls', url);

		const res = await fetch(`${API_URL}/rider/by-urls?${params}`);
		if (!res.ok) return [];
		const riders = await res.json();

		// keep the order of PRESET_RIDERS (the database returns them in any order)
		riders.sort((a, b) => PRESET_RIDERS.indexOf(a.riderUrl) - PRESET_RIDERS.indexOf(b.riderUrl));

		return await Promise.all(
			riders.map(async (rider) => {
				const r = await fetch(`${API_URL}/participation/rider/${rider.id}`);
				return { rider, races: r.ok ? await r.json() : [] };
			})
		);
	} catch {
		return [];
	}
}
```

And change the end of `load`:

```js
const races = await response.json();
const presets = await loadPresets(fetch);
return { races, presets };
```

Why it's written like this:
- **Why on the server?** The riders are already there when the page arrives in the browser: no
  flash of an empty list, and no CORS question, since it's server-to-server like the races.
- `URLSearchParams` builds the query string and encodes each value. `${params}` gives
  `urls=rider%2Fjai-hindley&urls=rider%2Flaurence-pithie` (`/` is encoded as `%2F`, Spring decodes it).
- `Promise.all(...)` sends the schedule requests for all riders **at the same time** and waits
  for all of them. With a `for` loop and `await` inside, they would be sent one after the other.
- Each item has the same `{ rider, races }` shape as in Step 7.1, so the page can't tell a preset
  rider from a searched one.
- The `try/catch` and the `return []`s: if anything goes wrong with the presets, the page still
  loads, just without them. Unlike the races, the presets aren't worth an error page.

### 8.3 Use them as the starting list

In `+page.svelte`, change the declaration of `selectedRiders`:

```js
let selectedRiders = $state(data.presets);
```

Move this line **below** `let { data } = $props();`, since it now uses `data`. As in Step 2, Svelte
may warn that it only captures the initial value: that's intended, the list then lives its own life
(search adds, × removes).

Preset riders can be closed with × like any other; they come back on the next page load.

✅ **Checkpoint:** reload the page. Jai Hindley and Laurence Pithie are already listed, in that
order, under the search bar. Edit `PRESET_RIDERS` (add `'rider/…'` from `Startlist.json`), save,
reload: the new rider is there. Searching a preset rider doesn't add them twice.

---

## Step 9: Polish

### 9.1 Phones and narrow windows

35 columns don't fit on a phone. Instead of squeezing them, make the whole planning scroll
horizontally:

```css
@media (max-width: 800px) {
	.planning {
		padding-inline: 1rem;
		overflow-x: auto;
	}

	.row {
		min-width: 800px;
		grid-template-columns: 150px 2rem 1fr 2rem;
	}
}
```

- Every row is at least 800px wide, so on a smaller screen `.planning` overflows, and
  `overflow-x: auto` turns that overflow into a horizontal scrollbar.
- All rows are inside the same `.planning`, so they scroll **together** and stay aligned.

### 9.2 Keep the top calendar visible when many riders are listed

Wrap the dates row and the top calendar row in `<div class="top">`, then:

```css
.top {
	position: sticky;
	top: 0;
	z-index: 5;
	background: #3b3b3b;   /* the page background, so rider rows don't show through */
}
```

> This doesn't work together with 9.1 on phones: `overflow-x: auto` turns `.planning` into its own
> scroll box, and sticky elements stick inside their scroll box, not the window. On desktop (no
> overflow rule), it works.

### 9.3 Keyboard arrows

Anywhere in the markup:

```svelte
<svelte:window
	onkeydown={(e) => {
		if (e.target instanceof HTMLInputElement) return;
		if (e.key === 'ArrowLeft') start -= 1;
		if (e.key === 'ArrowRight') start += 1;
	}}
/>
```

- `<svelte:window>` attaches the listener to the whole window, and Svelte removes it when you
  leave the page.
- The first line ignores the keys while you type in the search bar, otherwise moving the cursor in
  the input would also move the calendar.

### 9.4 "Aujourd'hui" button

In the label of the top calendar:

```svelte
<div class="label">
	Calendrier
	<button type="button" class="today-btn" onclick={() => (start = initialStart)}>Aujourd'hui</button>
</div>
```

It brings the window back to the 1st of the current month, the same view as when the page opens.

### 9.5 Warning when races are hidden

With 4 lanes and today's data, nothing is ever hidden. If a future season has 5 races at the same
time, `mainLayout.hidden` will be above 0. In the top calendar label:

```svelte
{#if mainLayout.hidden > 0}
	<small class="hint">+{mainLayout.hidden} masquées</small>
{/if}
```

and raise `MAIN_LANES` if it happens.

### 9.6 Empty rider

A rider with no scraped startlist gets an empty row, which could look like a bug. In the rider
label, after the name:

```svelte
{#if entry.races.length === 0}<small class="hint">aucune course</small>{/if}
```

---

## Final file structure (for orientation)

```
+page.server.js
	API_URL, PRESET_RIDERS
	loadPresets(fetch)            → [{ rider, races }]
	load()                        → { races, presets }

+page.svelte
<script>
	let { data } = $props();
	// constants:    DAY_MS, DAYS, MAIN_LANES, today, initialStart
	// state:        query, suggestions, selectedRiders (= data.presets), start
	// derived:      end, days, mainLayout
	// plain:        timer

	// dates:        toDay, todayDay, firstOfMonth, dayLabel, isMonday, formatDate, formatDates, flag
	// layout:       assignLanes, col, isVisible, visibleDays, categoryClass
	// search:       onSearchInput, selectRider, removeRider
</script>

{#snippet timeline(layout, rows)}
	day cells (lines) + race bars (flag, name, tooltip) + today band
{/snippet}

<div class="planning">
	<div class="top">                                  ← optional sticky (9.2)
		<div class="row"> .dates (Monday labels) </div>
		<div class="row"> label · ‹ · {@render timeline(mainLayout, 4)} · › </div>
	</div>
	<div class="row search-row"> .search (input + dropdown) </div>
	{#each selectedRiders}
		<div class="row rider-row"> label (×, flag, name) · {@render timeline(…)} </div>
	{:else}
		hint
	{/each}
</div>

<style>
	.planning, .row, .dates, .timeline, .day, .race (+ categories), .arrow,
	.label, .search …, .rider-row, .close, .hint, @media (max-width: 800px)
</style>
```

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Rows aren't aligned with each other / some days are wider | `1fr` instead of `minmax(0, 1fr)` in `grid-template-columns` |
| Date labels don't match the week lines | `.dates` and `.timeline` don't have the same `grid-template-columns` or the same `grid-column: 3` |
| Day lines only cover the first lane | `grid-template-rows` missing on `.timeline` (`1 / -1` needs explicit rows) |
| Races are one day off | A local-time method (`getDate`, `getDay`, `new Date("2027-02-15")` + `getDate`) instead of `Date.UTC` / `getUTC…` |
| Races appear stacked after the timeline, at the bottom | A race with no `isVisible` check, or `grid-row` > number of rows: check `--rows` |
| Races are hidden behind the grid lines | `z-index: 1` missing on `.race` |
| Races change line when clicking the arrows | `assignLanes` called on the visible races only; call it on all races (Step 3.2) |
| The arrows are as tall as the calendar | `align-self: center` missing on `.arrow` |
| Rider timeline sits under the `‹` column | `grid-column: 3` missing on `.timeline` |
| Space or a thick double line between two riders | A margin/`row-gap` on `.row` or `.rider-row`, or the `.rider-row + .rider-row .timeline { border-top: none }` rule is missing |
| The × is on the right of the name | The `<button class="close">` is written after the `<span class="rider-name">`, or an old `margin-left: auto` is still on `.close` |
| `Cannot read properties of undefined (reading 'id')` in the rider loop | Old array format still somewhere: `entry[0]` instead of `entry.rider` |
| Preset riders don't appear | `curl` the `by-urls` endpoint: 404 → Spring not restarted; `[]` → wrong `riderUrl` (check `Startlist.json`, no leading `/`) |
| Preset riders appear in a random order | The `riders.sort(...)` line is missing in `loadPresets` |
| Page opens on empty weeks | Normal before the season starts: the current month has no races. See the note in Step 2.1 to open on the season's first month instead |
| Calendar doesn't start on the 1st | `start` initialised with `today` instead of `firstOfMonth(today)`, or a local-time method in `firstOfMonth` (use `getUTCFullYear` / `getUTCMonth`) |
| Today's band is hidden under races | `z-index: 2` missing on `.today-line` (races have `z-index: 1`) |
| No tooltip when hovering a race on today's column | `pointer-events: none` missing on `.today-line` |
| Today's band appears in the first column after moving away | The `{#if today >= start && today <= end}` around `.today-line` is missing |
