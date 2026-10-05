# Price server on Cloudflare (free)

The app gets most prices straight from free sources (TCGdex, Scryfall, optcgapi, TCGCSV). A few
better sources need an API key, and a key must never be inside the app, because anyone can pull
it out of the APK. So those sources go through a tiny server of our own: a **Cloudflare Worker**.

What the price server does:

- **Keeps the API keys** (JustTCG, PokeTrace, TCG API, Ximilar, PSA, RapidAPI, PokemonPriceTracker).
- **Shares results between everyone.** When one person's app asks for a card, the answer is
  saved for 24 hours (graded prices 72 hours). Everyone else who asks for that card in that time
  gets the saved answer and no provider is called. So what we spend depends on *how many
  different cards* people have, not on how many people use the app.
- **Never goes over a free limit.** It counts every call to every provider per day and per month
  and stops before the limit, moving on to the next provider. When everything is used up, it
  hands out the last known price marked as old (`"stale": true`) instead of an error.

Everything below uses only free plans. It takes about 15 minutes and works from a phone.

---

## Part 1: set it up from your phone

### A. Create a free Cloudflare account

1. Open <https://dash.cloudflare.com/sign-up> and sign up with your email. Confirm the email.
2. When asked what you want to do, you can skip. You do **not** need a domain name.
3. In the left menu (tap ☰ on a phone) open **Compute (Workers) → Workers & Pages**. If it asks
   you to pick a **workers.dev subdomain**, choose any name (for example `tcgcatalogue`) and
   confirm. Your server address will be `https://tcg-price-server.<that-name>.workers.dev`.

### B. Create an API token for GitHub

This token lets the GitHub workflow install the server for you.

1. Open <https://dash.cloudflare.com/profile/api-tokens>.
2. Tap **Create Token**, then next to **Create Custom Token** tap **Get started**.
3. **Token name:** `GitHub price server`.
4. **Permissions** – add exactly these three rows (tap **+ Add more** for each new row):
   | First box | Second box       | Third box |
   |-----------|------------------|-----------|
   | Account   | Workers Scripts  | Edit      |
   | Account   | D1               | Edit      |
   | Account   | Account Settings | Read      |
5. **Account Resources:** `Include` → your account.
6. Leave the rest as it is. Tap **Continue to summary**, then **Create Token**.
7. **Copy the token now** (it is shown only once). Keep it for step D.

### C. Find your Account ID

1. Open <https://dash.cloudflare.com> and tap **Workers & Pages** (or any domain-less page).
2. The **Account ID** is shown on the right side (on a phone: scroll down). It is 32 letters and
   numbers. Tap the copy icon next to it.
   You can also find it in the address bar: `dash.cloudflare.com/<ACCOUNT ID>/...`.

### D. Add the secrets to GitHub

On GitHub open the repository → **Settings** → **Secrets and variables** → **Actions** →
**New repository secret**. Add one secret per row. Only the first three are required; any
provider you leave out is simply not used.

| Secret name | Required? | Where the value comes from |
|-------------|-----------|----------------------------|
| `CLOUDFLARE_API_TOKEN` | yes | The token from step B. |
| `CLOUDFLARE_ACCOUNT_ID` | yes | The Account ID from step C. |
| `APP_KEY` | yes | A long random text you make up, see step E. |
| `JUSTTCG_KEY` | recommended | <https://justtcg.com> → sign in → **Dashboard** → API key (starts with `tcg_`). |
| `TCGAPI_KEY` | optional | <https://tcgapi.dev> → sign up → **Dashboard → API keys** → create key (starts with `tcg_live_`). |
| `POKETRACE_KEY` | optional | <https://poketrace.com/developers> → sign up → dashboard → API key. |
| `XIMILAR_KEY` | optional | <https://app.ximilar.com> → sign up (free plan) → **Settings / API token**. |
| `PSA_TOKEN` | optional | Sign in at <https://www.psacard.com>, then open <https://www.psacard.com/publicapi/documentation> → copy your **access token**. |
| `RAPIDAPI_KEY` | optional | <https://rapidapi.com> → sign up → subscribe (Basic, free) to **TCGPlayer Price Data** by lulzasaur9192 → **Apps → default application → Authorization → X-RapidAPI-Key**. |
| `PPT_KEY` | optional, paid | <https://www.pokemonpricetracker.com> → dashboard → API key. Only add it if you buy a plan; it is used for Pokémon graded (PSA/CGC/BGS) prices. |
| `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET` | free, recommended | <https://developer.ebay.com> → My Account → Application Keys → **Production** keyset: App ID (Client ID) and Cert ID (Client Secret). Graded prices for every game from current eBay listings (asking prices), 5,000 calls/day. |

Paste each value exactly, without spaces before or after.

### E. Choose the APP_KEY

The app sends this value in a header called `X-App-Key`. Requests without it are refused.

- Make it long and random, for example 40 letters and numbers. A password manager's
  "generate password" button works well (letters and digits only).
- Save it somewhere: you will type the same value into the app's settings later.
- Honest note: this is **not real security**. The key ends up inside the app, so a determined
  person could find it. It only stops casual abuse (bots and people poking at the address).
  The real protection is that the server can never spend more than the free limits, and that a
  single phone (IP address) can make at most 500 requests a day.

### F. Run the "Deploy price server" workflow

1. On GitHub open the repository → **Actions**.
2. Tap **Deploy price server** in the list on the left (on a phone: tap **All workflows** first).
3. Tap **Run workflow** → choose the branch → **Run workflow**.
4. Wait about 2 minutes until the run has a green tick. It:
   - runs the tests,
   - creates the database (only the first time),
   - installs the server,
   - uploads the keys you added,
   - and checks that the server answers.

Run the workflow again whenever you add or change a key. It is safe to run as often as you like.

### G. Copy the server address

Open the finished run. At the top of the summary (and in the **Smoke test** step) you see:

```
Price server URL: https://tcg-price-server.<your-subdomain>.workers.dev
```

Copy that address and add it as one more repository secret (as in step D):

| Secret name | Value |
|-------------|-------|
| `PRICE_SERVER_URL` | The address above, e.g. `https://tcg-price-server.tcgcatalogue.workers.dev` |

From then on, every APK that GitHub builds has the server address and the `APP_KEY` built in, so
nobody has to type anything. (In the app, **Settings → Price server** shows it, can test the
connection, and can point an APK to another server.)

### H. Check that it works

The **Smoke test** step already called `/v1/status` and printed the answer. In it, every provider
you gave a key to shows `"configured": true`, with how many calls it has left today and this month.

To check again later from a computer:

```
curl -H "X-App-Key: YOUR_APP_KEY" https://tcg-price-server.<your-subdomain>.workers.dev/v1/status
```

(A browser cannot add the header, so opening the address in a browser shows `unauthorized`.
That is correct.)

### If something goes wrong

| What you see | What to do |
|--------------|-----------|
| "These repository secrets are missing" | Add the named secret in step D and run again. |
| `Authentication error` / code 10000 | The token is wrong or misses a permission. Redo step B. |
| "No workers.dev URL in the output" | Do step A.3 (choose a workers.dev subdomain), then run again. |
| A provider shows `"configured": false` | Its secret is missing or empty. Add it and run again. |
| A provider shows `"blockedToday": true` | The provider itself said its quota is used up. It is tried again tomorrow (UTC). |

To **remove** a key later: delete the GitHub secret **and** delete it in Cloudflare
(**Workers & Pages → tcg-price-server → Settings → Variables and Secrets**). The workflow only
adds and updates keys, it never deletes them.

---

## Part 2: alternative, from a computer

You need Node.js 20 or newer.

```bash
cd worker
npm install
npx wrangler login                              # opens the browser once
npx wrangler d1 create tcg-price-cache          # prints a database_id
#   → paste that id into wrangler.toml (database_id = "…")
npx wrangler d1 execute tcg-price-cache --remote --file schema.sql
npx wrangler deploy                             # prints the server URL

# Keys: one at a time (it asks for the value) …
npx wrangler secret put APP_KEY
npx wrangler secret put JUSTTCG_KEY
# … or all at once from a JSON file (do not commit it; it is in .gitignore):
#   {"APP_KEY":"…","JUSTTCG_KEY":"…","TCGAPI_KEY":"…"}
npx wrangler secret bulk secrets.json

curl -H "X-App-Key: YOUR_APP_KEY" https://tcg-price-server.<your-subdomain>.workers.dev/v1/status
```

Run the tests with `npx vitest run` and the type check with `npx tsc --noEmit`.
To try it locally: put `APP_KEY=test` in `worker/.dev.vars`, run
`npx wrangler d1 execute tcg-price-cache --local --file schema.sql` and then `npx wrangler dev`.

Settings such as cache times and budgets are in the `[vars]` section of `worker/wrangler.toml`.
Change them there and deploy again.

---

## How the limits work with 100 users

### The key idea: calls depend on cards, not on users

Every answer is saved in the server's database and shared. If 60 people own the same Charizard,
the server asks a provider about it **once per day**, not 60 times. So the question is never
"how many users?" but "how many *different* cards were looked at in the last day?".

And only cards someone actually opens or refreshes are looked up. A card nobody looks at costs
nothing.

### What each free plan gives (with our 10% safety margin)

| Provider | Free limit | Our budget | Cards per call | Card refreshes |
|----------|-----------|------------|----------------|----------------|
| JustTCG | 1,000 calls/month, 100/day | 900/month, max 90/day | up to **20** | 900 × 20 = **18,000 a month** (≈ 580 a day). The full free 1,000 calls would be 20,000. |
| TCG API | 100 calls/day | 90/day | 1 | ≈ 90 a day, ≈ 2,700 a month (one blended market price, no conditions on the free plan) |
| PokeTrace | 250 calls/day, 1 per 2 s | 225/day | up to 20 | up to 4,500 Pokémon cards a day |
| RapidAPI (TCGplayer + PSA pop) | 50 calls/month in total | 45/month | 1 | last resort only |
| PSA (cert lookup) | ~100 calls/day | 90/day | 1 | 90 certs a day, each kept 30 days |
| Ximilar (identify photo) | 1,000 credits/month, 10 per photo | 90 photos/month, max 10/day | 1 | ≈ 3 photos a day for everyone together |

**Spreading the month.** For monthly limits the server allows on any day at most
*what is left this month ÷ days left in the month*. On 1 October that is 900 ÷ 31 = 29 JustTCG
calls. If a day uses fewer, the rest is added to later days automatically. So the budget can
never run out on day 10 and leave the rest of the month empty.

**Batching.** JustTCG and PokeTrace accept 20 cards in one call. The server collects all cards
that need a new price in one app request and sends them together. That is why 1,000 JustTCG calls
can refresh up to 20,000 cards.

### Example: 100 people for a month

- 100 users × about 30 owned or graded cards each = 3,000 cards, but people own many of the same
  popular cards. Realistically **1,500–3,000 different cards**.
- If every one of them were opened every day, that would be 1,500–3,000 refreshes a day.
- Capacity per day: JustTCG ≈ 580 cards + TCG API ≈ 90 cards, plus PokeTrace up to 4,500 for
  Pokémon cards.
- So Pokémon cards are refreshed daily, and the other games every 1–3 days in the worst case.
  In reality nobody opens all their cards every day, so most cards stay within a day.
- Graded prices (JustTCG beta) cost one JustTCG call per card instead of one per 20. They are
  kept for 72 hours to make up for that, and only cards marked as graded are asked for.

### What happens when a limit is reached

Nothing breaks. In this order:

1. The next provider in the list is tried (JustTCG → TCG API → PokeTrace → RapidAPI for normal
   prices; JustTCG graded → PokemonPriceTracker for graded prices).
2. If all are used up, the server sends the last known price with `"stale": true` and the date
   it was fetched. The app can show it with a small "old price" note.
3. If there never was a price, the answer is `null` with a reason (`"unavailable"`), and the app
   keeps using its free sources. Next day the budgets are back.

### Cloudflare's own free limits are far away

| Cloudflare free limit | Our use with 100 users |
|-----------------------|------------------------|
| 100,000 Worker requests a day | 100 users × ~20 app requests ≈ 2,000 a day (2%) |
| D1: 5 million rows read a day | a few rows per request ≈ 20,000 a day (under 1%) |
| D1: 100,000 rows written a day | ~3–6 rows per request + new prices ≈ 15,000 a day (15%) |
| D1: 5 GB storage | ~1 KB per card ≈ 5 MB for 5,000 cards |
| 50 outgoing calls per request | capped at 20 provider calls per request |

We use D1 (Cloudflare's database) rather than KV on purpose: free KV allows only 1,000 writes a
day, which 100 users would use up quickly.

---

## Privacy

- **Photos (Identify online):** a photo is sent only when the user taps **Identify online**. The
  server passes it straight to Ximilar for recognition and returns the matches. The photo is not
  saved, cached or logged by the server. Ximilar's own privacy policy applies to their side.
- **IP addresses:** for the 500-requests-a-day fairness limit the server stores only a scrambled
  (salted SHA-256) value of the IP that changes every day, and deletes it after two days.
- **Cards:** the server stores prices per card (shared by everyone), not who owns which card.

---

## API reference (for the app)

Base URL: the Worker address, e.g. `https://tcg-price-server.<subdomain>.workers.dev`.
Every request needs the header **`X-App-Key: <APP_KEY>`**; without it the answer is `401`.
All answers are JSON. Over the per-IP limit the answer is `429 {"error":"daily_limit_reached"}`.

### `POST /v1/prices`

Request (max 50 cards):

```json
{ "cards": [
  { "game": "POKEMON", "id": "base1-4", "name": "Charizard", "set": "Base", "number": "4/102",
    "tcgplayerId": "42382", "printing": "Holofoil", "graded": true }
] }
```

- `game`: `POKEMON`, `ONE_PIECE`, `MAGIC`, `DRAGON_BALL_FW`, `DRAGON_BALL_SUPER`, `UNION_ARENA`,
  `WEISS_SCHWARZ`, `NARUTO` (any case).
- `id` or `tcgplayerId` is required. `tcgplayerId` (TCGplayer product id, digits) is what most
  providers need, so send it whenever known. For Magic, `id` may be the Scryfall id.
- `printing` is optional (e.g. `Normal`, `Holofoil`, `Reverse Holofoil`, `1st Edition`, `Foil`).
- `graded: true` also asks for graded prices.

Response (same order as the request):

```json
{ "results": [
  { "key": "pokemon:tcg42382:holofoil",
    "conditions": { "NM": 420.5, "LP": 310, "MP": null, "HP": null, "DMG": 95.25 },
    "market": null,
    "graded": [ { "grader": "PSA", "grade": "10", "price": 12650.55, "currency": "USD",
                  "source": "pokemonpricetracker", "date": "2026-10-01", "sales": 12 } ],
    "source": "justtcg",
    "fetchedAt": "2026-10-03T12:00:00.000Z",
    "stale": false,
    "reason": null } ],
  "invalid": [2] }
```

- `conditions`: USD per condition, each may be `null`. `null` as a whole when there is no price.
- `market`: a single TCGplayer market price (USD) from sources that do not give a price per
  condition (TCG API free plan, RapidAPI). It is **not** a Near Mint price.
- `graded`: only real graded prices; never a raw price. Empty when none are known.
- `stale: true`: an older saved price, because all budgets are used up right now.
- `reason` (only when there is no price): `not_found` (no provider knows the card),
  `unsupported` (no provider can look it up, e.g. no TCGplayer id), `unavailable` (budgets used
  up or providers failing; try again later).
- `invalid`: positions of cards in the request that were ignored (unknown game, no id).

### `GET /v1/cert/{certNumber}`

```json
{ "cert": { "certNumber": "48658983", "description": "2000 POKEMON ROCKET DARK GYARADOS-HOLO #8",
            "grade": "MINT 9", "gradeDescription": "MINT", "year": "2000", "set": "POKEMON ROCKET",
            "cardNumber": "8", "subject": "DARK GYARADOS-HOLO", "variety": null, "specId": "1234567",
            "population": { "total": 1520, "higher": 210 }, "source": "psa" },
  "fetchedAt": "…", "stale": false }
```

`404 {"error":"not_found"}` for an unknown cert, `503 {"error":"unavailable"}` when the PSA budget
is used up and nothing is saved. Saved for 30 days.

### `GET /v1/pop?cert={certNumber}` or `GET /v1/pop?specId={specId}`

```json
{ "population": { "total": 4100, "higher": 210, "byGrade": { "10": 210, "9": 1520, "9 (Q)": 12 },
                  "description": "…", "specId": "1234567", "source": "psa" },
  "fetchedAt": "…", "stale": false }
```

Saved for 7 days. Uses the RapidAPI population API when `RAPIDAPI_POP_HOST` is set in
`wrangler.toml`, otherwise the PSA API.

### `POST /v1/identify`

Body: the JPEG bytes (max 1.5 MB), `Content-Type: image/jpeg`. Optional header `X-Game: POKEMON`
(puts matches of that game first).

```json
{ "matches": [ { "name": "Dark Gyarados", "fullName": "Dark Gyarados Team Rocket (RO) #8",
                 "set": "Team Rocket", "setCode": "RO", "number": "8", "outOf": "82", "year": 2000,
                 "rarity": "Rare Holo", "game": "pokemon", "tcgplayerId": "84606", "confidence": 0.69 } ] }
```

`confidence` is a rough 0–1 score for sorting (Ximilar returns a visual distance, not a
probability). Errors: `413` too large, `415` not a JPEG, `503` budget used up or not configured.

### `GET /v1/status`

Calls used and left per provider today and this month, whether each is configured, and the
number of saved results.

---

## Things to know about the providers

- **JustTCG graded prices** come from their v2 *beta*. It is not documented whether the free plan
  includes graded data. If it does not, graded prices for Pokémon come from PokemonPriceTracker
  (only with `PPT_KEY`), and other games show no graded price.
- **JustTCG's month** restarts on the day of the month your account was created, not on the 1st.
  The 10% margin and the even spreading cover the difference.
- **PSA** has been reported to lower free API tokens in 2026. If PSA answers "too many requests",
  the server stops asking until the next day and serves saved certs.
- **RapidAPI PSA population:** the article does not say this API's RapidAPI address. If you
  subscribe to it, copy its host (looks like `something.p.rapidapi.com`) from its RapidAPI page into
  `RAPIDAPI_POP_HOST` in `worker/wrangler.toml` and deploy again. Without it, `/v1/pop` uses PSA.
- **TCG API** gives per-condition prices only on its paid Pro plan; on the free plan we get one
  market price per printing, returned as `market`.

## Recognition learning

The release also enables private opt-in reports. See [SHARED-LEARNING.md](SHARED-LEARNING.md) for the private R2 bucket, separate moderation key, retention/deletion and operator review commands. `/v1/status` now reports whether private photos and moderation are configured, without returning secret values.
