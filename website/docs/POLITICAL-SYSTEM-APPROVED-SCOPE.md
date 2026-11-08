# Political system — Owner decisions, 8 November 2026

Implementation distinction: the website currently offers clearly labelled demo flows, not a live political engine. No ballot, office, kit reward, war, transaction, or message is created by these previews. House and office seals are recognition artwork; their presence never grants authority.

## Elections and offices
House term two weeks, Sunday 09:00 GMT. Emperor term four weeks, corresponding Sunday 15:00 GMT. Owner confirmed first election Sunday: 15 November 2026. Candidatures remain open until the exact start of voting. Voting lasts three hours: House 09:00–12:00 GMT, Emperor 15:00–18:00 GMT. At the opening instant applications close; at the closing instant votes stop. Calendar confirmation does not itself activate ballot writes. Own faction members choose their own leader. Anyone may apply for Emperor. House leaders, including the Veiled Concord's Reverent Mother, have five freely distributable Imperial votes. Ordinary faction members have one Imperial vote (Owner confirmed 8 November 2026). Candidates have motivation, acceptance checkbox, paginated cards, expandable statements and ranking by current votes. Owner confirmed that submitted votes are final and cannot be changed or withdrawn; leaders may spend their remaining allowance in separate submissions. A tie for highest votes requires a runoff limited to tied leaders, opening immediately after the prior round and lasting three hours. No arbitrary UUID/name tie-breaking.

The Veiled Concord is the original replacement name for the requested Bene Gesserit-style faction, home Caitain. Its leader is Reverent Mother, with equivalent House leader powers. It counts as one of two Houses supporting a counter rally. In-game faction and join-menu integration explicitly requested later; not delivered by the website preview.

## Formal Council
New tab and page. Bank, rallies, war and shared inbox belong here. Two distinct House supports required for an election rally; two for an Imperial counter rally. When both camps reach threshold, Emperor may confirm Civil War: the supporting camps become enemies. Revolt consequences remain undefined.

War needs Emperor support. Concord cannot declare; it may suggest another House do so. Accepted suggestion entitles that House to a free Sardaukar-themed Legendary+ armour-and-weapons kit, not implemented or distributed. House war declaration: once per three elections (two-round cooldown). Concord proposal: once per ten (nine-round cooldown), faction-wide. Which election type advances the counters is still unresolved.

Emperor-backed war allows free enemy war-teleport, one hour per player cooldown. Civil War rally camp has an additional three-hour blockade. Each House may pay 15% of its current faction bank balance to remove its own blockade; the one-hour cooldown remains. Ordinary transport pricing is unchanged.

Leader appoints Mentat, or Concord Sister for the Concord, for the current House term. Search own-faction members and require confirmation. Appointee controls faction bank: personal deposits, withdrawals, donations to searched own-faction members; transaction history and audit. Kit income goes to the faction bank. Appointee edits the faction kit in the Tailor. Exactly one active faction kit; replacement must have a newly chosen different name. Preserve receipts and past purchases.

## Shared messaging
Same inbox in personal CP, Formal Council and clan context. Prefixes: [User] blue, [House: name] gold, [Clan: name] green, [System] purple. House leaders send internal and inter-House correspondence. Delete with confirmation affects own mailbox only, not another recipient's copy. Permissions and per-mailbox/read-status semantics must be enforced centrally.

System subtags: Elections, Appointments, Rallies, War, Transport, Faction Bank, Faction Kit, Clan, Rewards. Events include applications received/accepted/rejected/withdrawn; vote receipt/update; anonymous candidate notification with current top three; elections opened/reminders/results/term end; appointments/expiry; rally support/threshold/denial; war/civil war; transport block/bribe; transfers/kit revenue; kit replacement; clan invitations/membership/role; reward claim states. Vote identity stays private. Messages do not replace audit records.

## Succession and permanent honours
At official result, revoke only previous office-linked ranks, seals and signatures and assign successor. Advisor appointments expire each term. Personal, faction and earned honours persist. After three consecutive mandates of the same office grant a permanent honour without lasting authority:

| Office | Permanent title |
|---|---|
| House Leader | High Lord of the Three Banners |
| Reverent Mother | Grand Matriarch of the Veil |
| Emperor | Emperor Emeritus of the Three Crowns |
| Spice | Lord High Warden of Spice |
| Water | Lord High Keeper of the Wells |
| Mentat | Grand Chancellor of the House |
| Concord Sister | High Preceptor of the Veiled Court |

For appointed posts count consecutive appointments/terms, not votes. Rank/seal automation, delivery, permanent honour art and backend implementation remain work until separately verified.

## Election office ledger implementation
Final results are recorded once per kind, faction and scheduled cycle, with immutable identity, dates and events. House expiry is the next two-week calendar boundary; Emperor expiry the next four-week boundary. A delayed worker or runoff does not extend a mandate. No-vote results record a vacant cycle. Three consecutive same-player victories in the same office and faction record a permanent recognition; a gap, vacancy or different winner breaks the streak. Recognition remains recorded after later defeats.
This is an election-fact ledger, not an activated authority source. Existing game offices and account ranks remain authoritative until the separate succession adapter is tested and deployed. Seal/signature projection and political notifications are pending; no rank grants are claimed by this change. Seven rule tests and five isolated MariaDB concurrency/rollback scenarios verified.

## 8 November — application result register execution brief
Owner-authorised continuation (separate from design-only ticket 371). Objective: replace application register vote/result placeholders with read-only recorded election facts. Scope: own applications only, per-round aggregate counts, final result, recorded term and permanent honour metadata. No test election, ballot writes or rank grants. Steps: implement projection, cover runoff/elimination/delayed finalisation, verify query against existing records, merge via Dev fork and deploy only two changed runtime files with backup. Acceptance: unfinished runoff stays open; votes never summed across rounds; winner without reconciled term is not described as rank delivery; private voter identities never returned. Validation: six focused projection cases pass; live read-only MariaDB query returned two existing applications for one account, both pending, internal IDs excluded. Deployment evidence follows separately.

## Electoral Roll — Owner-authorised execution brief, 8 November
Add a Dev-style page in the Realm & Politics left menu. Public table exposes only username, election (including round), and number of votes, with sorting and 50-row pagination. Candidate choices and player/account IDs remain private. Derive temporary I Voted recognition from actual ballots and a still-open latest round; preserve it through a runoff and remove it when the election closes. Display via existing profile honours renderer without creating permission ranks or overwriting earned honours. Tests: strict public projection and invalid sort/page rejection; isolated database cases for non-voter, active voter, expiry, other account, runoff carry and terminal-result removal. No rehearsal or production vote is created. Rollout requires scoped backup, Dev-fork review and live empty-state/menu verification.

## 8 November follow-up decisions
War and Concord cooldowns count completed House-election cycles (not Imperial elections or runoff rounds). Water and Spice Rights recipients are CLANS, not factions or people. Proposed term remains the current Imperial term. Revolt is a separate request, never an automatic war declaration.

## Proclamation archive execution brief
Replace fictional MOTDs with durable news-style entries (title, body, author, publication time, version and immutable request ID), one per archive page. Server derives House scope and validates current leader/Emperor authority; Guild publication is restricted to guild_envoy. Readers see their own House plus Imperial and Guild archives. Editors remain in councils above banking; existing messages appear only on Proclamations. Retry-safe publication, input limits and explicit confirmation; no external news syndication or invented posts. Five focused authorization/input/session tests pass. Preserve all concurrent mail assets during scoped rollout.

## Imperial clan charters execution brief
Water and Spice each have one current clan recipient, selected from active website clans, with a decree and explicit confirmation. Current Emperor authority comes from the existing verified game/website transition; no administrator bypass. New grants replace active grants in a transaction; revocation preserves history; optimistic versions prevent concurrent stale edits. Expiry is bounded by current Imperial term, legacy grants by first election close. Audit insert and grant mutation are atomic. Archived clans cannot receive new grants. Three focused charter tests and an isolated real MariaDB eight-way race/replacement/revocation/audit-failure rollback pass. Website charter records do not themselves credit game resources or mutate player inventories.
