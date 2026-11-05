# Ticket 348: private Support notification email

Account centre → Your Account Portal → Security now includes the contact email
form beneath the password form. Website password confirmation is required to
request or remove an address. The emailed 144-bit code expires after 15 minutes,
is stored only as SHA-256, and can be used once by the signed-in requesting
account. Five failed code attempts invalidate it. Resends are limited by a
persistent cooldown/hourly budget; password and sync attempts are also bounded.
Changing an address preserves the verified former contact until confirmation.

`/api/account/email` is authenticated, same-origin for mutations and no-store.
It never appears in public profiles, public SSO profile data or account rank
information. Native Support uses its configured outgoing email transport.
Verification messages contain only the code, expiry and account centre link.
Internal `.invalid` identities and email addresses belonging to a different
native user are rejected rather than merging users. Legitimate operator mailboxes
may also be registered as Support system mailboxes; that does not prevent proving
ownership. Verification messages are marked automatically generated.

The Node table is initialized at CP startup. Install
`integrations/osticket/contact-email-schema.sql` in the integrations database
before enabling the helper. Deploy `integrations/contact-email.php` as
`/home/dunerealms/apps/integrations/contact-email.php` and its policy file in
the existing native Support directory. Communication is local stdin/stdout,
bounded by size and timeout; no password or verification code in command-line
arguments or logs. Native `authenticate.php` remains UUID-alias based: compare
the resolved user ID, not the mutable notification address.

Projection updates are transactional and revision ordered. The immutable
`UUID@accounts.dunerealms.invalid` alias stays attached to the same native UID.
Only the managed contact email becomes that user's notification default. A
previous managed email is detached on change/removal; unrelated addresses,
ticket ownership, threads, attachments, receipts and staff access are untouched.
Native failure leaves an explicit pending status and Retry Support sync button;
it is not reported as successful notification activation. Removal restores the
technical alias, so users must read tickets online until adding another contact.

## Verification and rollout, 5 November 2026

- 14 Node tests passed: confirmation lifecycle, password/origin/authentication,
  resend limits across handlers, attempts, expiry/replay/supersession, concurrent
  confirmation, mail failure, native retry, removal and public profile regression.
- 20 SQLite and 20 temporary InnoDB checks passed against the actual projection
  policy; MySQL tables shadow live names only on the test connection. Includes
  collision rollback, stale/replayed revisions, removal and identity isolation.
- Actual native TicketOwner/EndUser/User objects in memory select the changed
  default email while the saved UUID alias stays unchanged. No live user changed.
- Native sender has configured active SMTP. This is configuration evidence,
  not proof of inbox delivery; no real user's email was set or mailed in testing.
- 8 live HTTP checks passed: private route authentication, initial private state,
  origin/password/format/invalid-code gates, unchanged contact and native Dev SSO.
  SSO needed its normal warmup route before opening a deep ticket link.
- Live browser: form beside password controls, semantic labels, browser email
  validation, visible incorrect-password feedback and no horizontal overflow.
- Backups: `/root/ticket348-before-20261105`. Only CP restarted. Additive empty
  tables plus six scoped runtime files installed; no game, payment or rank change.

Rollback the six files from the preserved baseline and restart only the CP.
If users have subsequently verified addresses, restore their native defaults to
the retained UUID aliases through the current revisioned sync before removing
the feature. Retain contact/projection tables privately for reconciliation;
do not drop them or delete user/ticket/history records as a rollback shortcut.

## Owner-reported correction

The initial result went to a global notice above the viewport, and an overly
broad system-mailbox guard rejected the Owner's operator mailbox. The correction
shows sending progress, success, failure and sync warnings beside the form with
live announcement/focus; an accepted result survives a failed refresh. Four
behavioral UI regressions cover those states. Existing backend and ownership gates
remain intact; personal email addresses are not silently trusted.

The Owner explicitly requested immediate activation of their own notification
address. Its exact match in the existing active verified staff-mail identity was
independently checked, reused and audited, with private before-state backups.
Central contact and native default recipient were updated; no new verification
email was sent or inbox delivery claimed. This is a scoped operator action based
on existing ownership proof, not an ordinary-user verification bypass.
