# Builder recruitment and imperial recognition

Ticket508368 (native391). Builder membership and earned recognition are independent.

## Membership, manual awards and forum milestones

The `game_builder` rank shows the Builder Team seal. Its initial game permissions match the ordinary member baseline; accepting an application does not automatically grant this rank or editing tools. Assign through the existing account rank controls after staff review.

Authorized staff with account-assignment permission can grant or revoke **Builder Award** from a user's More menu. The API enforces Origin, active targets, rank hierarchy, an explicit registered award and boolean decision, with audit/outbox synchronization. Rank membership does not award it. Existing department awards still require25 verified closures **within that department**, including protected staff.

Forum awards are cumulative at1,10,50,100,1000 approved posts. The additional **Forum Legend** starts at1001 (more than1000), alongside the1000-post award. Current MyBB `postnum` is authoritative, including existing members and count reductions. No fake posts or stored counter migrations are needed. Post author panels show the highest ribbon and expandable earlier awards; native and central profiles show earned awards.

`website/node/recognition-catalog.json` is shared with PHP. New manual/forum entries automatically join the DuneRealms example account; role entries join its displayed role seals. Showcase display is not a permission, purchase, earned achievement or post-count grant. Keep Crysknife excluded and never display another user's named personal seal. To extend the catalogue, provide safe unique keys, labels, integer thresholds where applicable, and public image derivatives; deploy the matching catalogue to the native integrations directory too.

## Applications and publication

The CLI-only `configure-builder-signup.php` creates the separate Builder Application topic and eight structured fields, idempotently. It retains Alpha signup and the native private ticket workflow. Back up the form, field, help-topic and topic-form tables before running it. The short route is `https://support.dunerealms.com/builder-signup.php`.

Publish `website/content/builders-wanted.md` through the normal news editor after the application and video are available. Existing forum/Discord publication workers handle delivery; the forum announcement author is DuneRealms. X remains manual.

The20-second720pMP4 is an animation of original community artwork, with original epic instrumental music, not recorded gameplay. Music provenance and generated-image provenance are beside their source assets. The video has H264/yuv420p picture and stereoAAC audio. No third-party music is included.

## Validation and rollout

39 focused local Node/SQLite checks pass, including permission failures, grant/revoke isolation, membership, profile grouping and all milestone boundaries. Two pre-existing legacy node-catalogue assertions are excluded from this focused run; do not report a full-suite pass. PHP lint plus mocked Alpha/Builder intake, navigation, workspace, support-home and forum rendering regressions are checked separately. Never run the Node fixture tests on the production integration host: the native adapters there perform real synchronization.

Deploy reviewed exact files with backups. Install native catalogue/helper and expanded synchronization allowlists before enabling the new Node projections. Native role and badge refresh must use `syncNativeSeals(db,{sealsOnly:true})` to preserve native forum groups. Restart only the website and account-panel Node services. Preserve deployed shared header, sign-in, age gates and user-generated profile fields.

Rollback restores the backed-up runtime files and restarts those two services. Keep application records; disable the Builder topic rather than deleting submitted applications. Native metadata additions are separate from application data. Old `team-*` achievement image aliases remain compatible, now showing AWARD. Real rank/team membership artwork retains TEAM.
