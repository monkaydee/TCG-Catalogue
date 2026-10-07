# Collection tools

The Tools tab displays unrealized card PnL, known/unknown row coverage, submissions, pending
photo reviews, private recognition sharing and CSV/PDF insurance exports. A card page provides
per-copy cost editing and splitting: edit a smaller quantity to create a distinct cost lot.

Each lot retains acquisition currency, purchase cost, grading cost, shipping and tax per copy.
Blank is unknown and zero is an explicit zero. Raw acquisitions start with no grading cost;
slabs start with unknown grading cost. Converting a raw card to a slab also requires entering its actual grading fee; the old raw default of zero is not treated as a free grading service. Historic rows are backfilled without inventing those fees.
Market refreshes do not alter ledger costs. Unrealized PnL is current value minus all lot costs;
a row with a missing price or any missing cost is excluded and counted as unknown.
Manual valuations are honored and labeled on the card page. This PnL is distinct from the
existing daily portfolio value chart, which includes collection additions/removals.

Sales allocate the oldest lot first and freeze the total cost basis in sale currency, using the
current FX rate at sale time. Sale fees reduce realized PnL. Partial sales leave remaining costs
unchanged. Purchase, grading, shipping and tax remain original-currency entries while owned.
Known certificates get separate physical slab rows; a duplicate certificate cannot be added twice.
Slabs without a certificate do not silently merge into other slabs.

Submissions track company, reference, preparing/shipped/received/grading/returned/cancelled status
and notes. Grading costs are entered in the linked card's ledger. They do not assume a future grade
or profit. Historical submissions remain available after their card is sold.

CSV exports include one row per lot, certificate, dated quote provenance, image URL and PnL.
PDF exports include card identity, condition, certificate, basis and dated valuation, plus card
thumbnails already cached on the device. Missing cached pictures do not trigger internet requests.
Exports use Android's document picker and run locally. CSV values are quoted and formula-escaped.

Backup format 3 carries lots and submissions; earlier backups still import. Replacement import,
ledger mutations and sales use Room transactions. Backup merges match language and slab identity.
Unfinished review photos remain local across app restarts; addition receipts are stored atomically
with collection additions so a resumed multi-card photo cannot add an already confirmed hit again.
Pre-grading now uses automatic front/back photos with optional centering guides, a swipe-to-reveal
slab presentation, CSV export and private saved reports. Until reliable surface assessment is
available, all games use an explicitly experimental centering-only score. Failed photos or missing
measurements do not produce a score. Saved report photos survive temporary cache cleanup.
