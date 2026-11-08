#!/usr/bin/python3
"""Postfix policy: internal SASL identities send only to internal destinations.

Automatic verified alias forwarding happens after acceptance and is unaffected.
No sender-supplied header is trusted, and ordinary external mail is unchanged.
"""
import re
import sys

INTERNAL = re.compile(r'^[a-z0-9][a-z0-9-]*\.internal@dunerealms\.com$', re.I)


def decide(attributes):
    user = attributes.get('sasl_username', '')
    sender = attributes.get('sender', '')
    recipient = attributes.get('recipient', '')
    if INTERNAL.fullmatch(user):
        if sender.lower() != user.lower():
            return 'REJECT 5.7.1 Use your authenticated internal sender identity'
        if not INTERNAL.fullmatch(recipient):
            return 'REJECT 5.7.1 Internal mail cannot send to external recipients'
    elif INTERNAL.fullmatch(sender):
        return 'REJECT 5.7.1 Internal sender identity requires matching authentication'
    return 'DUNNO'


def main():
    attributes = {}
    for raw in sys.stdin:
        line = raw.rstrip('\r\n')
        if not line:
            print('action=' + decide(attributes) + '\n', flush=True)
            attributes = {}
        elif len(line) <= 8192 and '=' in line:
            key, value = line.split('=', 1)
            if len(attributes) < 100:
                attributes[key] = value


if __name__ == '__main__':
    main()
