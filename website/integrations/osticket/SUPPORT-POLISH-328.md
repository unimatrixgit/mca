# Support intake and native presentation (328)

This package implements consolidated sources 119, 133 and 134. It changes presentation and adds a customer-facing Sales intake; it does not change account checks, ticket access, Legal routing, department email addresses or award eligibility.

## Request catalogue

The homepage resolves an explicit list through `Topic::getPublicHelpTopics()`. It presents parental requests (with the separate child-removal link), GDPR removal, general help, Sales, forum help, technical problems, access issues, ban appeals and Alpha signup. Missing/non-public topics produce no card. Existing priority relationships and specialised appeal/Alpha handlers remain intact. Cards use two equal desktop columns and one mobile column; the final card has the same treatment as every other card.

`configure-public-sales.php` defaults to a read-only plan. Run it with `--apply` only for an authorised rollout. It creates **Customer sales enquiries**, routed through the existing Sales department/team, using only standard Ticket Details form 2. The existing private **Sales enquiries** email/internal topic remains private and unchanged. The installer requires transactional topic tables, serialises creation with a named lock, validates repeat runs and refuses routing/form drift. It does not create a ticket or process a payment/refund.

## Shared actions

Keep the existing `/dune-actions.css` and `/dune-actions.js` design system. Remove conflicting card/submit overrides rather than adding another button theme. The workspace shares typography and control sizing while retaining the shared neutral, approval and destructive variants, keyboard focus and disabled styles. Busy controls have a progress cursor.

The small Support-only `dune-support-actions.js` supplement gives native ticket-action links explicit semantics. A reused native `#confirm-form` derives its colour from the current hidden `do` action, so an “OK” button for deletion stays visibly destructive and a later owner-change confirmation loses the destructive treatment. It responds to PJAX/popup insertion and modal visibility changes. It only changes presentation classes/data attributes; it never invokes an action, changes a form field, submits a request or grants permission.

## Managed signatures and mobile forum

Preserve managed identity, original conversation bodies and email signature markup. The existing author checks, duplicate-signature protection and **25 verified closures in the same department** eligibility policy remain unchanged. Mobile CSS wraps presentation-table seal groups inside their container on Support and MyBB. MyBB's legacy 990/970px container minima are removed only below the mobile breakpoint; post text, images and native controls fit the available width.

Focused acceptance includes public-catalogue routing/private-topic omission, 86 native presentation checks preserving forms/CSRF/uploads/PJAX/JSON, seven ticket-author/signature checks, 16 departmental-award checks and three native-action tests under Node 22. PHP/JS syntax checks also pass. Source/avatar/receipt/payment/progression tests are outside this presentation change.

Browser verification uses an isolated Support fixture for desktop/mobile and confirmation-state changes, and a temporary candidate CSS overlay on the real public forum thread for mobile layout. The overlay is removed by reloading; it is not a publication. In that mobile check the 13 managed seal images loaded, the signature fit its container, and page width fell from 990px to within a 390px viewport. Final production form links/layout and installer idempotency must be verified during rollout. No real ticket, reply, refund, deletion or ban is submitted for QA.

## Rollback

Restore only the native PHP/CSS/JS files from the recorded per-rollout backup, preserving later unrelated edits. No static website Worker rebuild is required. If rolling back the new Sales intake, hide/disable only the newly created topic after checking whether it has received real tickets; retain those tickets and their history. Never restore entire native topic/ticket tables over subsequent customer activity. Existing private topics, mail settings, ranks, signatures and award records require no database rollback.
