# Account head rendering —368

The account list now projects Minecraft face and hat layers onto a40px canvas rather than positioning a complete skin sheet with CSS. Uses the existing member avatar endpoint and server-derived avatar mode, respecting custom avatars. Archived rows retain the existing roster fallback. Ordinary image avatars are drawn whole; fallback faces remain visible. No account, UUID, permission or skin changes.

10 focused tests passed:5head-coordinate/fallback checks plus5member-profile privacy/avatar tests. Real public Owner skin endpoint exercised in isolated browser fixture with served account stylesheet. Current Dev session lacks account-administration rights, so no permissions were expanded to inspect that private live table. Served panel deployment verified separately. Backup/root/ticket368-before-20261104; CP-onlyrestart. No complete claim that Mojang is always available: existing fallback remains.
