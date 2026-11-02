# Ticket 350: verified closure counts and personal department seals

Owner clarification: **25 total distinct closed tickets in a department, not 25 percent**. All accounts, including Owner and Main Network Administrator, follow the same threshold. One seal represents each currently assigned department: general below 25, personalised at 25 or above. Separate rank, elected-office and recognition seals remain independent. Redundant department/team image pairs are removed from managed forum, ticket and email signatures.

## Attribution

`staff-closure-counts.sql` is shared by the Node roster and PHP signature adapter. It reads the native closing event’s `uid`/`uid_type`, not its assignee snapshot `staff_id`. The native actor must map through the verified DuneRealms staff backend to a central account. The event’s department is the department where the ticket was closed.

Only currently closed tickets with a latest non-annulled closure count. Reopens and repeated closes cannot accumulate duplicate credit. System-only events are not attributed from display names. Consolidated/superseded Dev tickets and other administrative batch closures do not earn progress. A valid custom Dev completion/denial can use its authenticated audit actor when a native event has no actual staff actor; native events can have default type `S` with a null UID, which must not prevent this fallback. A real positive native staff UID takes precedence. A ticket is never credited twice.

Public roster cards expose safe total/per-department aggregates, not ticket IDs, account UUIDs, evidence or request content. Unavailable statistics are reported as unavailable; they never unlock a personal seal.

## Rendering and freshness

The original imperial department artwork is composed with a deterministic gold/sapphire name ribbon and “25 RESOLVED” label using the installed GD renderer. This is a code-rendered UI badge, not a manually granted achievement. Asset names contain an opaque digest rather than a central account UUID. The PNG is generated only for an eligible currently assigned department, atomically, in the Support image directory. Artwork falls back to the general seal if generation is unavailable.

Signature rendering reads authoritative aggregates. Native close/reopen events schedule one stored-signature refresh at request shutdown, after the native closure and any Dev audit metadata have been committed. This updates the cached email/forum records without a periodic polling job. Historic ticket message bodies are not rewritten.

## Checks and rollout

- 23 focused Node tests cover roster/access regressions, actual SQL attribution, assignee decoys, system/administrative exclusions, custom Dev fallback, latest close, per-department totals and private-field exclusion.
- 16 PHP assertions cover 24/25/26, per-department isolation, Owner/Main Network Administrator non-exemption, duplicate seal removal, safe badge names and unavailable-data fallback.
- The personal artwork render test uses a private staging fixture, never a live award or fabricated closure count.
- Back up source plus stored signatures, preferences and pre-existing message hashes before rollout; compare existing message hashes, preferences and rank rows afterwards. Native signature source and the public roster are the only deployment targets. No game, economy or payment changes.

Private recovery/evidence locations: `/root/ticket350-before-20261102`, `/home/dunerealms/.config/ticket350`, and the local `output/ticket-350` directory. Do not commit those private snapshots or restore them over newer user activity without review.
