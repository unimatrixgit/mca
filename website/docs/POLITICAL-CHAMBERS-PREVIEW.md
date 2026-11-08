# Political chambers — website design preview
Owner scope: under User Panel / Politics & Gameplay, Landsraad contains House Elections and Imperial Elections. Imperial Command is the Emperor's water/spice rights workspace, with separate Water and Spice pages. All use shared Dev design and existing Minecraft-themed artwork.

This iteration is read-only website scaffolding. No votes, nominations, office changes, rights grants or game writes. All action controls remain disabled and clearly marked as a design preview. No synthetic election totals or recipients.

House context is resolved from the authenticated account's verified Minecraft UUID, through the existing private gateway's read-only faction endpoint. Only the verified own faction appears. Bank fields are discarded and never returned. Missing/unavailable faction produces a closed chamber, never an all-Houses fallback. The existing read-only office projection identifies the Emperor. Website ranks do not confer that political office.

Planned flow: nomination, ballot, result; imperial rights: recipient, scope, term, decree, confirmation, audited register. These are design placeholders, not approved rules. Before activation define candidature/voter eligibility, election schedules, ties, faction changes, term transitions, and rights/revocation semantics. Revalidate faction/office server-side for every future write.

Acceptance: six navigable pages with shared artwork/tabs; own-House-only projection; no identity overrides or write methods; unavailable and unlink states; responsive desktop/mobile layout; no game changes. Rollout requires reviewed fork merge, backups and live page checks.

Owner addition: The Landsraad overview includes a Force elections card, divided into Start a rally (reason and action) and House agreement. It is not displayed on House Elections. Two distinct Houses must agree before a new election is held; repeated endorsements from one House do not increase the count. This remains a disabled design preview, with no invented rally or support totals. Before activation, establish who can represent a House, whether the initiating House counts toward the two, and the target/expiry rules for a rally.
