# Native rank and seal synchronization

Rank assignment, permission-matrix changes and manual seal edits now save a native-sync event in the same database transaction as the central change. Dedicated Tester/VIP follow-up awards also enqueue within their own transaction. The event stores no credentials or profile payload.

The request attempts immediate delivery. An adapter failure leaves a durable event and returns `nativeSyncPending: true`; it does not undo a successfully committed central rank change. Delivery regenerates native memberships and signatures from current authoritative state. A newer change committed during delivery remains queued. A database lease prevents overlapping normal deliveries across the main and CP processes; expired leases allow recovery after a crash. Backoff starts at five seconds and caps at five minutes.

The retry worker reads only the outbox. When no change event exists it never loads accounts, ranks, native signatures or adapter scripts. This is retrying failed events, not periodically checking users' ranks.

## Parent integration required before deployment

1. Deploy the new module, the schema table, and updated rank-admin, badge-admin and community-badges modules together. `openDatabase` applies `node/schema.sql`; alternatively run `initializeNativeSyncOutbox(db)` before enabling the changed routes. The table must exist before any rank or badge writes.
2. In the **main Node server only**, import `startNativeSyncRetryWorker` from `./native-sync-outbox.mjs`, call it after opening/initializing the database, and call its returned stop function during shutdown before closing the database. CP requests can attempt immediate delivery; do not start a second retry timer there.
3. Optionally surface `nativeSyncPending` in the rank/seal UI as “Saved; native team/signature update queued for retry.” Do not claim the native update succeeded when this flag is true.
4. An initial deploy creates no event and performs no broad synchronization. Explicitly enqueue only if a reviewed outstanding change must be replayed.

No live deployment, account/rank edits or broad sync was performed by this source task. The native adapters still generate a full membership snapshot when a change event is delivered. The existing PHP timeouts bound the normal adapter calls; the outbox lease is 120 seconds.

## Separate security work

Durable retry does not by itself invalidate a native PHP session while its adapter is offline. Existing central assignment session invalidation is preserved. Native request-time checks/version guards must independently reject stale native privileges; do not describe the outbox as that guard. Direct SQL edits, separate administrative scripts, registrations and Git-owned badge writes are not newly instrumented by this patch.
