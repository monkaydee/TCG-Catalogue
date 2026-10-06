Restore raw quotes from condition data and clarify slab-price availability.

- Recover saved raw valuations from TCGplayer condition sales when a catalogue has a product ID but no aggregate market price. Missing intermediate condition buckets no longer hide an available quote for the requested condition.
- Refresh missing raw valuations when their card page opens, and update the saved valuation when “All prices” is refreshed. Queue an upgrade refresh while retaining previous quotes, manual values and purchase costs.
- Show an automatically loaded, clearly labelled ungraded market reference on slab pages. It uses the saved language and printing and is kept separate from graded valuations, binder values and portfolio totals.
- Say when this specific grade has no quote. Distinguish an upstream graded-price provider outage or request limit from an unreachable server.
- Remove misleading “previous quote retained” notices when no previous quote exists.
- Add regression checks for condition-only prices, Zekrom raw-price recovery, GSG 8.5 reference separation, language isolation and provider-status handling.

CardNavo name, signing and the 0.1.<build-number> release convention are preserved. An ungraded reference is not a slab valuation. Exact grader/grade/language quotes may remain unavailable without sufficient matching market evidence.
