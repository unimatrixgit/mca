import importlib.util
import json
import pathlib
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('inventory', pathlib.Path(__file__).parents[1] / 'scripts/native-form-inventory.py')
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


class NativeInventoryTest(unittest.TestCase):
    def test_private_values_and_urls_are_never_exported(self):
        text = '''<form action="https://person:secret@host/path?token=hidden" method="POST">
        <input name="_token" value="NEVER_EXPORT"><input name="password" type="password" value="PRIVATE">
        <input name="notes" maxlength="160" required><input name="{{ dynamic_secret }}"></form>'''
        rows = inventory.forms('azuriom/shop/cart/index.blade.php', text)
        encoded = json.dumps(rows)
        for secret in ['person', 'secret', 'hidden', 'NEVER_EXPORT', 'PRIVATE']:
            self.assertNotIn(secret, encoded)
        self.assertEqual(rows[0]['handler']['kind'], 'dynamic-or-current')
        self.assertEqual(rows[0]['fields'][-1]['maxLengthLiteral'], 160)
        self.assertTrue(rows[0]['fields'][-1]['requiredAttributeCandidate'])
        self.assertEqual(rows[0]['qaTicket'], 342)

    def test_blade_handlers_comments_stable_identity_and_unverified_state(self):
        text = '''{{-- <form id="ignored"></form> --}}
        <form action="{{ route('shop.subscriptions.select', $gateway->type) }}" method="POST">@csrf
        <button type="submit">Subscribe</button></form>'''
        rows = inventory.forms('azuriom/shop/packages/show.blade.php', text)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['handler'], {'kind': 'named-route', 'name': 'shop.subscriptions.select'})
        self.assertTrue(rows[0]['csrfDirectiveCandidate'])
        self.assertEqual(rows[0]['key'], inventory.forms(rows[0]['source'], '\n' + text)[0]['key'])
        self.assertTrue(all(value == 'not-run' for value in rows[0]['validation'].values()))
        self.assertEqual(rows[0]['runtimeStatus'], 'unverified')

    def test_allowlist_exclusions_symlinks_missing_and_reproducibility(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / 'auth').mkdir()
            (root / 'tests').mkdir()
            (root / 'auth/login.blade.php').write_text('<form method="POST"><input name="username"></form>')
            (root / 'tests/private.php').write_text('<form><input value="PRIVATE"></form>')
            (root / 'linked.php').symlink_to(root / 'auth/login.blade.php')
            roots = {'azuriom/core': root, 'missing': root / 'absent'}
            data = inventory.collect(roots)
            self.assertEqual(data, inventory.collect(roots))
            self.assertEqual(len(data['records']), 1)
            self.assertEqual(data['records'][0]['qaTicket'], 338)
            self.assertEqual(data['missingRootAliases'], ['missing'])
            self.assertNotIn(directory, json.dumps(data))
            self.assertNotIn('PRIVATE', json.dumps(data))


if __name__ == '__main__':
    unittest.main()
