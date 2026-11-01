# Native community SEO

The presentation-only `native-seo.php` helper is installed through `scripts/install-native-seo.mjs`. The installer changes only the final return of `shared-header.php` and copies the public support crawl files. It is idempotent; it does not replace the shared helper or alter authentication/signature handlers.

## Public support pages

Only the public support landing page and service-targets page are in the support sitemap. Each gets a descriptive title, description, canonical URL, Open Graph metadata and factual WebPage JSON-LD. `/index.php` canonicalises to `/` without changing the existing URL or redirect history. Error responses never receive indexable metadata.

All other native support pages receive `noindex, nofollow, noarchive` in HTML and, where headers remain writable, the HTTP `X-Robots-Tag`. The support robots file excludes ticket, staff, profile, login and attachment routes. Authentication still protects private data; robots directives are not an access-control mechanism.

## Forum indexing limitation

The current `dune_forum_age_guard` sends every anonymous visitor to central sign-in. Public search crawlers therefore cannot retrieve forum threads. This task preserves that access/age restriction and marks native forum output noindex. No forum thread sitemap is published and no group-restricted title is disclosed.

Indexing public forums would require a separate authorised product decision to permit anonymous read-only access. That would let a user read those public areas without making an age declaration. A crawler-only exception would not solve that policy issue and is not implemented.

## Verification

Run `php native-seo-test.php /absolute/path/to/native-seo.php`. Checks cover public canonical normalisation, stale metadata removal, private/error noindex, original body preservation and unrelated-host isolation. The deployed support root, service-targets, robots and sitemap returned HTTP 200 on 2026-10-28; the login page returned the expected noindex header/meta. No Search Console submission or ranking result is claimed.
