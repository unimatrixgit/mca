# Central social sign-in setup

Status: **not enabled**. No Google or Facebook application credentials were found in the running account service or available private integration configuration. No working-looking provider buttons or independent MyBB login accounts have been introduced.

“Gmail registration” means signing in with a Google account. It does not provide access to Gmail messages. The central DuneRealms account remains the identity shared with MyBB and osTicket.

## Required operator setup

Create the applications in the providers' official developer consoles, configure the consent screen and permitted audience, and provide the client ID/application ID and client secret through the account service's private configuration. Never place secrets in the web directory, Git, screenshots or a public chat.

| Provider | Configuration names | Exact intended callback |
| --- | --- | --- |
| Google | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `https://cp.dunerealms.com/oauth/google/callback` |
| Facebook | `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET` | `https://cp.dunerealms.com/oauth/facebook/callback` |

The callback routes are reserved design targets, **not active endpoints**. Facebook's app must be eligible for public login before ordinary users can use it; a development application may be limited to its configured testers.

## Activation requirements

Implement and verify the central authorization-code flow with a maintained provider/OIDC library before enabling buttons:

- Store single-use, short-lived state bound to a Secure, HttpOnly, SameSite cookie. Use PKCE S256 and OIDC nonce where the provider supports them.
- Validate issuer, signature, audience, expiry and nonce. Use Google's stable `sub` and Facebook's app-scoped user ID as provider identity keys.
- Never link an existing account solely because the provider returns the same email address. Linking an existing account requires its authenticated DuneRealms session and explicit confirmation.
- New users still choose an available DuneRealms username and declare the required age band. Provider sign-in must not bypass age restrictions, account locks, parental controls, deletion blocks, Minecraft UUID verification or permission checks.
- Request only authentication/profile scopes needed for sign-in. No Gmail mailbox access, page administration or posting privileges.
- Enforce exact registered HTTPS callbacks, one-use code exchange, bounded requests and safe local return destinations. Never return access tokens or secrets to the browser or logs.
- Keep providers disabled when credentials are absent or validation fails. Test denied consent, expired/replayed state, wrong issuer/audience, missing email, duplicate provider identity and attempted account takeover before production activation.

## Primary references

- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
- [Google OpenID Connect reference](https://developers.google.com/identity/openid-connect/reference)
- [Facebook manual login flow](https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow/)
- [OAuth 2.0 security best current practice](https://www.rfc-editor.org/rfc/rfc9700.html)
