# DuneGUI staff renderer — staged release 1.1.0

This extends DuneMenus, keeping its existing public renderer API so current gameplay modules do not need duplicate menu engines. It is not deployed yet.

## Presentation
Java on compatible Paper and client versions: illustrated, two-column native dialog overview, six entries per page, hover descriptions, full review screen, explicit Continue / Back / Close. Existing resource-pack artwork is used through the imperial_menus font. Current older servers retain the existing fallback during staging. Bedrock still uses the native form transport; its new resource-pack skin needs separate client verification before release.

## Staff entry point
`/dunestaff` and `/navigator staff` show only enabled services for which the player currently has permission. Commands run as that player, with a fresh check when selected. No console delegation or new OP bypass.

| Area | Existing service | Workflow |
| --- | --- | --- |
| Moderation | DuneCore + DuneBan | Explicit local/network scope; player; reason; duration; exact confirmation; current-record unban |
| Support | DuneCore + website/osTicket | Own and authorised staff ticket desk; actual department visibility remains enforced by the website; replies remain there |
| Quests | DuneQuests | Current engine definitions and authoritative website settings |
| Treasury | DuneBank | Current balance, deposit/withdraw confirmation and ledger; native permission plus website authority checked by the bank |
| NPCs & missions | DuneNPC | Existing managed NPC and mission tools; broad dune.admin permission required |
| Administration | DuneCore | Existing world/kit/menu/political controls; dune.admin required |

This is a consistent presentation of actual existing functions. It does not pretend a generic offline player editor or in-game osTicket reply implementation exists.

## Artwork inventory
Git origin/main fetched to 6e0f02e without merging or overwriting local work. Tracked game references: docs/artwork/menu-icons-atlas.png, docs/artwork/guild-navigator.png, DuneLobby/previews/guild-lobby-concept.png. New imperial assets already in DuneResourcePack/assets/dunerealms/textures/gui: imperial-menu-atlas-v2.png, imperial-portal.png, five faction heraldic icons. Font: assets/dunerealms/font/imperial_menus.json.

## Release checks
Native dialogs need client protocol771+ (Java1.21.6+) and Paper's dialog API (1.21.7+). Via's unknown protocol fails closed to the existing fallback. Bedrock is checked first and never receives Java dialogs. Do not deploy assuming the live1.21.4 fleet already supports the new screen. Validate the engine/plugin/inventory migration and actual Java+Bedrock rendering before rollout. Existing owner-bound opaque sessions, render revision, expiration, single-use callbacks, and current-authority checks are preserved.
