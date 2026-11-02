# Public moderation display — 2 November 2026

Owner explicitly requested immediate execution as an exception to the scheduled backlog.

- Homepage: Latest bans contains 12 real public active records, excluding demo and unresolved legacy identities before the limit. Latest appeal decisions sits immediately below, six per page, approved/denied only, with original public ban fields and CSS imperial sand/gold decision seals.
- Public ban list: 50/page, includes inactive records with appeals, separate actual Active/Unbanned state and latest appeal decision. Pending derives from an open native ticket; closing an undecided ticket does not fabricate approval, and a fresh appeal supersedes the previous public decision. Explicit approved/denied decisions survive native ticket closure.
- Privacy: no requester IDs, evidence, private decision reasons, ticket contents or Minecraft UUID field added. Existing unresolved legacy/demo exclusions remain. Decision-card endpoint returned 38 publishable records over seven pages at verification; excluded records are not made public merely because a system appeal exists.
- Shared navigation: Home and Support center UPDATE badges; native Support link spacing matches public header. A misplaced badge entry briefly broke navigation tuple rendering during implementation; corrected, then added a regression test. Actual fresh browser verified all 11 navigation images loaded and both badges rendered.

Checks: four focused Node tests passed, including real SQLite query execution with pending/denied/approved, closed ticket, reopened appeal, pagination boundaries, private fields and demo/legacy filtering. Live HTTP verified 12 latest bans, six first-page decisions/two on last page and 50 public-list rows. Browser clicked Next and confirmed page 2 with six different cards. Desktop six columns/no horizontal overflow. Support desktop nav width matched public nav at 1029.6px in the same 1280px viewport; mobile 390px menu opened correctly, no overflow.

Deployment: backed up native web entry and modules at `/root/public-appeals-before-20261102`, surgically changed only the relevant embedded assets and routes/modules, restarted only `dunerealms-web`. No world, inventory engine, payment or game ban mutation in this display change. Rollback uses the backed-up entry plus both modules together. Existing Alpha release tag is unchanged.
