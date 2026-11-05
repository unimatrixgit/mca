"""Read-only template discovery, never a form-validation pass or rendered inventory."""
import argparse
import hashlib
import json
import pathlib
import re

ROOTS = {
    'support/client': '/home/dunerealms/apps/integrations/osticket/include/client',
    'azuriom/core': '/home/dunerealms/apps/azuriom/resources/views',
    'azuriom/shop': '/home/dunerealms/apps/azuriom/plugins/shop/resources/views',
}
EXCLUDED = {'vendor', 'node_modules', 'tests', 'fixtures', 'cache', 'compiled', 'mail', 'emails'}
DIMENSIONS = ['required-empty', 'types-and-enums', 'unicode-boundaries', 'authorisation-identity',
              'csrf-origin', 'uploads', 'replay-concurrency', 'keyboard-responsive']


def attr(tag, name):
    m = re.search(r'(?:^|\s)' + re.escape(name) + r'\s*=\s*([\"\x27])(.*?)\1', tag, re.S | re.I)
    return m[2] if m else None


def identifier(value):
    return value if value and re.fullmatch(r'[A-Za-z_][A-Za-z0-9_.:\[\]-]{0,100}', value) else None


def owner(source):
    if source.startswith('support/'):
        return 337
    if re.search(r'/(auth|profile)/|/user/login\.', source):
        return 338
    if re.search(r'coupon|discount|giftcard', source, re.I):
        return 343
    if source.startswith('azuriom/core/admin/'):
        return 340
    if source.startswith('azuriom/shop/admin/'):
        return 342 if re.search(r'gateway|payment|subscription', source) else 341
    return 342 if re.search(r'/cart/|/payments/|/subscriptions/', source) else 339


def forms(source, text):
    clean = re.sub(r'<!--.*?-->|\{\{--.*?--\}\}|/\*.*?\*/',
                   lambda m: re.sub(r'[^\r\n]', ' ', m[0]), text, flags=re.S)
    result = []
    # A Blade expression can contain PHP's -> inside a quoted action attribute.
    tag_attributes = r'''((?:"[^"]*"|'[^']*'|[^'">])*)>'''
    for ordinal, m in enumerate(re.finditer(r'<form\b' + tag_attributes + r'(.*?)(?:</form>|$)', clean, re.S | re.I), 1):
        tag, body = m[1], m[2]
        action = attr(tag, 'action') or ''
        route = re.search(r"\broute\(\s*['\"]([A-Za-z0-9_.-]+)['\"]", action)
        if route:
            handler = {'kind': 'named-route', 'name': route[1]}
        elif re.fullmatch(r'/[A-Za-z0-9_./-]*', action):
            handler = {'kind': 'literal-path', 'name': action}
        else:
            handler = {'kind': 'dynamic-or-current', 'name': None}
        fields = []
        for field in re.finditer(r'<(input|select|textarea|button)\b' + tag_attributes, body, re.I | re.S):
            raw = field[2]
            name = identifier(attr(raw, 'name'))
            if not name:
                continue
            kind = attr(raw, 'type') or ('text' if field[1].lower() == 'input' else field[1].lower())
            maximum = attr(raw, 'maxlength')
            fields.append({'name': name, 'type': identifier(kind),
                           'requiredAttributeCandidate': bool(re.search(r'(?:^|\s)required(?:\s|=|$)', raw)),
                           'maxLengthLiteral': int(maximum) if maximum and re.fullmatch(r'\d{1,6}', maximum) else None})
        result.append({'key': hashlib.sha256(f'native|{source}|{ordinal}'.encode()).hexdigest()[:16],
                       'source': source, 'line': clean[:m.start()].count('\n') + 1,
                       'occurrence': ordinal, 'identifier': identifier(attr(tag, 'id')),
                       'method': (attr(tag, 'method') or 'GET').upper() if (attr(tag, 'method') or 'GET').upper() in ['GET', 'POST'] else 'DYNAMIC',
                       'handler': handler, 'fields': fields, 'csrfDirectiveCandidate': '@csrf' in body,
                       'qaTicket': owner(source), 'runtimeStatus': 'unverified',
                       'validation': dict.fromkeys(DIMENSIONS, 'not-run')})
    return result


def collect(roots):
    records, templates, missing = [], [], []
    for alias, directory in sorted(roots.items()):
        base = pathlib.Path(directory)
        if not base.is_dir() or base.is_symlink():
            missing.append(alias)
            continue
        for file in sorted(base.rglob('*.php')):
            relative = file.relative_to(base)
            if any(part in EXCLUDED for part in relative.parts) or file.is_symlink():
                continue
            if any(parent.is_symlink() for parent in file.parents if parent != base):
                continue
            source = alias + '/' + relative.as_posix()
            data = file.read_bytes()
            found = forms(source, data.decode('utf-8', errors='replace'))
            if found:
                templates.append({'source': source, 'sha256': hashlib.sha256(data).hexdigest(), 'candidates': len(found)})
                records.extend(found)
    return {'version': 1, 'scope': 'Installed template candidates only; not rendered forms or passed validation.',
            'excluded': sorted(EXCLUDED | {'symlinks'}), 'missingRootAliases': missing,
            'remainingDiscovery': ['MyBB database templates', 'staff Support templates', 'dynamic forms', 'actual served-route reconciliation'],
            'templates': templates, 'records': records}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    data = collect(ROOTS)
    pathlib.Path(args.output).write_text(json.dumps(data, indent=2, ensure_ascii=True) + '\n', encoding='utf-8')
    print(f"Recorded {len(data['records'])} native candidates in {len(data['templates'])} templates; none marked passed.")
