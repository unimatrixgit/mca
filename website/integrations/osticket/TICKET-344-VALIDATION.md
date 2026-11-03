# Dev workflow validation — ticket 344

## Result

Completed 4 November 2026. The confirmed Unicode defect was delivered under approved Bug354 through merged PR7 (`0dee7041d4a0157c93c5754915671f4b881fc0f6`) and verified live. This follow-up completes the form audit with executable route acceptance fixtures and live browser checks. It introduces no additional production permission or ticket-state changes beyond the documented completion of this task.

## Per-action coverage

| Form/action | Checks and result |
|---|---|
| New request | Required/blank/whitespace, string versus array, every text-field Unicode boundary, malformed UTF8 and enums: 104 policy assertions pass. Real authenticated HTTP accepts160 accented title characters and rejects161; deliberately omitted privacy acknowledgement prevents creation of a production test task. Native browser blank submission focuses title and reports7 invalid controls. |
| Valid submission | Actual PHP handler runs against random-namespaced MySQL fixture tables. VIP request produces one native fixture ticket and one pending registry row; requestedHigh becomesMedium. Verified Owner fixture autoapproves and retainsHigh. |
| Duplicate/concurrent submission | Same original request creates no duplicate; rotated submission token rejects browser replay. Two parallel PHP processes, using real namespaced MySQL GET_LOCK/RELEASE_LOCK, produce one fixture ticket and registry row. |
| Interrupted response | Fixture interruption after native ticket creation leaves one native row. Retry locates that ticket by submitter/department/subject and adds only the missing registry row. Native creation failure produces no registry row. |
| Execution brief | Dev can update an approved fixture. VIP cannot.30,000 accented characters accepted;30,001 rejected. Invalid/nonpositive IDs rejected on actual served handler. Original request is preserved. |
| Priority | Dev updates approved fixture; VIP denied. Array and unknown enum inputs rejected. |
| Approve/deny | Only verified reviewer identity allowed. Dev approval rejected both in fixture and live HTTP. Approval persists, repeated decision rejects; denial closes the native fixture and persistsDenied. Denied task cannot be reapproved. |
| Complete | Pending completion rejects. Native closure failure leaves registryApproved/nativeOpen. Successful completion closes native fixture and marksCompleted. Replay rejects without another event. |
| Privacy and role boundaries | Ordinary-member fixture403; VIP cannot read another submitter’s task404. No submitted identity or rank is trusted. Existing policy tests include revoked reviewer rank and reviewer-name spoofing. |
| CSRF/origin | Real authenticated HTTP rejects badCSRF, explicit foreign/null origins, invalid IDs and expired submission token.11 live HTTP assertions pass. |
| Browser/accessibility | Actual signed-in Dev form verified at1280px and390px: no horizontal overflow; mobile fields fit317px inside viewport. Blank submit focusesTitle; Tab moves toRequest type with solid visible outline. Existing private navigation, English labels and declared field limits verified. Screenshots saved outside Git. |

## Executable evidence

- 104 PHP policy assertions pass against the actual served policy.
- 30 isolated PHP-route assertions pass with real MySQL tables/advisory locks and explicitly mocked native ticket/email boundaries.
- 11 authenticated live HTTP checks pass without creating or deciding production test tasks.
- 3 focused Node Dev-role tests pass.
- Browser screenshots: local `output/ticket344-20261104/desktop-form.png` and `mobile-form.png`.

Run the route harness with PHP CLI (PDO MySQL + mbstring) and Python3:

```sh
python3 website/integrations/osticket/tests/dev-route-test.py /path/to/private-db-config.json
```

The private configuration provides host, optional port, database, username and password. It is read only at runtime and never printed or copied into the test workspace. Every SQL table and advisory lock is confined to a random validated fixture prefix. Only those six fixture tables are dropped in cleanup; temporary scripts and request files are removed. No native production ticket, email, payment, approval or deletion service is called by the harness.

## Practical limits

Native osTicket ticket creation and closure are represented by explicit test doubles in the route harness; it proves this handler’s input, orchestration, replay and locking behaviour, not every osTicket internal failure mode. Existing real Dev task creation/completion and the repaired Alpha submission were separately verified in their original delivery records. This Dev intake has no upload field: attachments belong to the resulting native private ticket and are outside this ticket’s input scope. The page uses server-rendered HTML, not JSON parsing. Interrupted-response recovery is tested; no arbitrary production connection failure was induced.

The source changes for this follow-up are tests and this coverage report. The live fix and rollback remain `/root/ticket354-before-20261103`. Completion and native closure must be recorded only after the acceptance branch is merged.

