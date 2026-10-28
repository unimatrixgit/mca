# DuneRealms server controls

The separate server-operations page is https://cp.dunerealms.com/servers, linked from account Administration. It preserves the account panel at the root. It manages the three existing systemd services: Arrakis, the Vaerkring lobby and the Velocity gateway. It does not create or reset worlds.

Capabilities: status, player counts, service memory, recent journal output, start/stop/restart, and one-line console commands for the two Paper servers. Proxy console input is deliberately unavailable because that service has no console FIFO. Actions are asynchronous and their outcome appears in status. Console command acceptance means delivery to the server; inspect logs for command success.

Access requires the current website session, staff role and an explicit username in private `SERVER_CONTROL_OPERATORS`. Registration/staff membership alone cannot gain server control. Mutations require the exact CP Origin. The backend replaces any supplied actor with the signed-in username and audits action type/target before dispatch; command text and credentials are not written into account audit rows.

`agent.py` is a standard-library Python service on game-host loopback port 25573. It accepts a separate 64-character bearer from `/etc/dunerealms-server-control.secret`. Only fixed units and fixed FIFO paths are available; no shell command execution or arbitrary file paths. FIFO writes use non-blocking, no-follow opens and validate the descriptor type. Each server accepts one operation at a time with a cooldown. Journal credential patterns are redacted.

The existing SSH tunnel now also forwards website-loopback 15433 to game-loopback 25573. Its restricted account key permits only PostgreSQL 5432 and control 25573 destinations. Agent port 25573 is not public. Website private settings: `SERVER_CONTROL_TOKEN` and `SERVER_CONTROL_OPERATORS`. No credentials belong in this directory.

Agent systemd service: `dunerealms-server-control`; existing tunnel: `dunerealms-inventory-tunnel`. The agent needs system service control and console FIFO access, and runs under a read-only filesystem sandbox with private temporary files. Deploy agent changes to `/opt/dunerealms/server-control/agent.py` and restart its service. Tests: `python3 -m unittest discover -s network/server-control -p test_agent.py` and the website test suite.

Live deployment 28 October 2026: website release `20261028-server-panel`, 60 website tests and 4 Python agent tests passed. Authenticated status, logs, a harmless `list` command and an idempotent `start` of the active Arrakis service passed through the public HTTPS API. Foreign-origin writes returned 403; unauthenticated controls returned 401. Browser review confirmed the operator interface. No real restart was required for the panel acceptance test.

The normal account dashboard also embeds these controls under a Servers navigation tab. The embedded view uses the same session and operator checks, hides duplicate site chrome, and reports only its layout height to its same-origin parent. The CP framing policy explicitly permits itself and the main website; other origins remain blocked.
