# Ticket367 — transparent reward ingot

Replaced only the Discovery Rewards heading image with a sibling transparent PNG derived from the existing pixel-art ingot. Original `menu-0.webp` retained. CP explicitly serves the new PNG with its existing PNG content type; no reward or redemption logic changed.

14 tests pass:12 existing discovery/redemption behaviour checks and2 artwork/asset checks. PNG alpha is decoded in the asset test: all four corners fully transparent, substantial empty background and visible nearly opaque ingot pixels. Live CP image loaded successfully and inspected against the green card background, including mobile view.

Backup: /root/ticket367-before-20261104. Scoped rollout: new asset, one heading URL and CP static asset allowlist; CP restart only. No database writes or actual redemption. Evidence output/ticket367-20261104/live-transparent.png.
