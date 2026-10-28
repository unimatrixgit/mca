# Control panel: feature manual and permissions

Published 28 October 2026 at https://cp.dunerealms.com/#staff-manual.
Release directory: /home/dunerealms/apps/dunerealms/releases/20261028-cp-feature-guides.

The staff manual has 13 searchable feature categories, including CoreProtect, WorldEdit, WorldGuard, Chat, Permissions, Moderation, Inventory, Economy, Tickets, Travel, Quests, Politics and Server operations. Guides explicitly distinguish live behaviour from pending integrations. Reading the guide requires cp.menu.staffmanual, checked on the server as well as in the menu.

Account menu sections now open one view below the navigation. Ranks & permissions provides a checkbox matrix, lower-rank creation and protected owner accounts. Both existing accounts unimatrix_admin_44a5a3 and grmacken were assigned Owners without changing passwords. Game permissions await the DuneEss/LuckPerms deployment; this release publishes the website controls and private game snapshot endpoint only.

Validation: 67 website tests passed locally and on the live host before switching the release. Both HTTPS health checks passed; unauthenticated staff manual returned 401. Browser verified Owners session and separate Staff manual view with feature categories. Core and Ess local builds passed, but those game changes were not deployed in this release.

Changed source paths:
- website/control-panel/public/index.html
- website/control-panel/public/pages.mjs
- website/control-panel/public/panel.js
- website/control-panel/public/ranks.css
- website/control-panel/public/ranks.mjs
- website/control-panel/public/staff-manual.mjs
- website/control-panel/server-control.mjs
- website/control-panel/server.mjs
- website/control-panel/staff-manual.mjs
- website/node/account-admin.mjs
- website/node/auth.mjs
- website/node/community-rewards.mjs
- website/node/permissions.mjs
- website/node/rank-admin.mjs
- website/node/schema.sql
- website/node/server.mjs
- website/tests/node-host.test.mjs
- website/tests/rank-permissions.test.mjs
- website/tests/server-control.test.mjs
