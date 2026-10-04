# Golden set: recognition test on real photos

The golden set is a collection of real card photos with the right answer for each. On every
change to the app, CI runs the app's own photo recognition on it (on an Android emulator) and
writes the score to the run summary (Actions → Recognition test). A drop shows before a release.

## Where the photos live

Not in this repository (it is public). Use a **private** repository, e.g. `monkaydee/tcg-golden`:

```
cases.csv
ogerpon_svp123_de.jpg
evoli_ex_svp174_de.jpg
...
```

`cases.csv`:

```
file,cardId,language,grader,grade
ogerpon_svp123_de.jpg,svp-123,DE,,
flareon_jungle3_psa8.jpg,base2-3,EN,PSA,8
```

- `cardId`: the card's id as the app uses it (TCGdex id for Pokémon, e.g. `svp-123`, `swsh11-TG03`;
  One Piece code e.g. `OP02-017`). The "Wrong card?" report fills this in for you.
- `language`, `grader`, `grade`: optional.

## Connecting it

1. Create the private repository and upload photos + `cases.csv`.
2. GitHub → your profile → Settings → Developer settings → Fine-grained tokens → new token with
   read access ("Contents: Read") to that one repository.
3. In TCG-Catalogue → Settings → Secrets and variables → Actions:
   - secret `GOLDEN_TOKEN` = the token;
   - variable `GOLDEN_REPO` = `monkaydee/tcg-golden`;
   - optional variable `GOLDEN_MIN_EXACT` = e.g. `0.9` to fail when fewer than 90 % are exact.

## Good photos for the set

Original camera files (not WhatsApp copies), mixed on purpose: raw, sleeve, toploader, slab;
all languages you collect; glare, angles, dim light; the cards that failed before.
