# Support homepage deployment — 2 November 2026

Install `support-index.php` as the native osTicket `index.php`, alongside `dune-support-home.php` and `dune-support-home.css`. Keep native session, header and footer handling. The template resolves only the public topic catalogue; absent topics produce no broken or private-topic links.

The live catalogue now has `GDPR data removal request` (topic 19), public, routed to Legal (department 6). It was cloned from the existing parental-removal topic's routing/SLA configuration, with team assignment cleared and only the standard Ticket Details form (form 2) attached. The parental-only form was not copied. Topic notes require manual ownership/scope review and prohibit passwords/government-ID uploads at intake. No automatic data deletion or new staff permissions were implemented. New topic creation was transactional and name-guarded; existing topics were not edited.

The account integration adds this exact topic name to the existing Minecraft-link exemption for legal/parental/appeal requests. Existing DuneRealms sign-in and staff permission checks remain in place. It does not grant anonymous access to private tickets or bypass staff controls.

Cards are ordered: parental requests, GDPR removal, general support, ban appeal, Alpha signup. The parental card also links to the existing child-data-removal topic. Old welcome content and the sidebar are omitted by the new index; the legacy Alpha invitation injector is avoided by the new root ID, so there is one Alpha card rather than two. Existing database landing-page text is preserved, not deleted.

Verification: PHP routing/template test passed, all changed PHP files linted, live homepage displayed five cards in order, old welcome absent, no duplicate Alpha invitation, desktop and 390px mobile without horizontal overflow. Actual GDPR form opened with its topic selected; no test request or deletion was submitted. Native Legal routing was checked directly. Support desktop menu now matches public-site spacing and hides the hamburger; mobile menu opens as a grid without overflow.

Private rollback: `/root/support-cards-before-20261102` contains previous native index/authentication files and topic/form configuration snapshots. Restore only the changed files/configuration after checking for subsequent edits. Disable the new topic if rolling back; do not overwrite live tickets or restore entire topic tables blindly.

Published guide source: `website/content/support-center-guide.md`; public post ID `80e13e9f-846c-4d79-856d-fe6d6f5b23ee`, source key `manual:support-center-guide-20261102`, category Community. This is a new owner-authorized post; no existing article was overwritten.
