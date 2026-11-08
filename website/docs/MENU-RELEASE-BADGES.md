# Release badges after deployment

Run `node website/scripts/menu-updates.mjs --receipt <receipt.json>` only after backup, successful deployment and live verification. Deploy the resulting web/menu-new-items.json and patched navigation asset using the normal reviewed workflow. Ordinary builds do not record changes.

Receipt fields: environment=production, verified=true, version (four components), commit (full deployed Git SHA), paths (only actually deployed source paths), newFeatures (explicit canonical main-menu page URLs with newly delivered functionality). Do not infer a new feature just because a file was added. Unchanged pages, tests and unrelated game code do not receive badges. Keep actual verification evidence alongside the receipt.

NEW wins over UPDATE during the same release. Old release markers disappear automatically when the site's release changes. Current source main-menu map controls scope. CP sidebar release metadata remains separately maintained; it is not automatically populated by this command.

This command is a deployment finalization hook, not a background watcher. Deployments that bypass it must finalize badges explicitly. Never mark planned or merged-only work as delivered.
