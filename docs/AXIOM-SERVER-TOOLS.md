# Axiom server build tools

User requested Axiom on Arrakis, then all servers. Installed the official MIT
licensed **Axiom Paper Plugin 6.0.1+1.21.8** on all five Paper planet backends and
the lobby. The Velocity proxy is not a Paper server and receives no incompatible
plugin. Existing Axiom defaults are retained. No OP, wildcard, region bypass,
public editing permission, client mod or commercial licence was added.

Pinned official Modrinth release: `CelkNHJp`, project `evkiwA7V`.

Download:
https://cdn.modrinth.com/data/evkiwA7V/versions/CelkNHJp/AxiomPaperPlugin-6.0.1-for-MC1.21.8.jar

SHA512:
`93ffec862ec1d84741e15f46cf1407dae79ca86cd46e777b1c30b6ec7bd4dbe4b53925f4811db787b65e6d6d4839ba6e81f0a3ce1367002d58ebc33e2991821d`

All six backends were checked as Paper 1.21.8 before installation, restarted
normally, and verified through native startup and `version AxiomPaper` output.
All report Axiom 6.0.1 with LuckPerms and CoreProtect integrations enabled.
The existing owner permission already allows `axiom.use`; it was verified rather
than granting another broad permission. WorldGuard permissions were not changed.

Players also need the matching Axiom client mod. The upstream project requires
a commercial licence or multiplayer whitelist for multiplayer use:
https://modrinth.com/plugin/axiom-paper-plugin
https://axiomdocs.moulberry.com/other/whitelist.html

Installation does not activate or bypass that client-side entitlement. No Axiom
world-edit operation was run as part of installation verification.
