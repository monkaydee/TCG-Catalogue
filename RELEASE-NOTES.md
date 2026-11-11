Pre-grading: findings report with markers on the card, and a way to record the grade a card received.

- New "Findings" section on the pre-grade result: every corner, edge and the surface is listed as confirmed (you recorded damage), possible (an automatic check flagged it) or not assessable (the photos cannot show it), with a marker on the card photo. "Nothing flagged" is never worded as "flawless".
- Export the full report as JSON (centering ratios, each finding with its location and measured evidence, what extra photos are needed, limits of the estimate).
- New "Grade received" on saved pre-grades: after a card comes back from grading, enter grader, grade and certificate number. "Export training sample" saves your photos with that verified grade, the data a real grade model needs.
- Pre-grading is now offered for Pokémon and One Piece only.
- The score itself is unchanged: centering only, experimental. A grade model was trained and tested on 6,388 PSA-graded cards and is deliberately not used: it did barely better than knowing a card's language and print year (docs/PREGRADING-SYSTEM.md).
- Settings no longer shows the price server address and key in builds that ship with the server.
- CardNavo Pro (paid tier for the Play Store build) from the previous release is unchanged; this GitHub build keeps every tool open.

Keep CardNavo, the existing 0.1.<build-number> convention, package and signing key.
