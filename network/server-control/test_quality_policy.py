import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
import quality_policy as policy

JAVA = 'a1111111-b111-4111-8111-c11111111111'
BEDROCK = '00000000-0000-0000-0009-000000000001'


def millis(value):
    delta = datetime.fromisoformat(value).replace(tzinfo=timezone.utc) - policy.EPOCH
    return (delta.days * 86400 + delta.seconds) * 1000 + delta.microseconds // 1000


class QualityPolicyTest(unittest.TestCase):
    def totals(self, value=0):
        return dict.fromkeys(policy.METRICS, value)

    def test_recorded_defaults_are_inactive_and_preserve_legacy_authority(self):
        snapshot = policy.policy_snapshot()
        self.assertFalse(snapshot['active'])
        self.assertEqual(snapshot['policy']['version'], 1)
        self.assertEqual(snapshot['policy']['migration']['unlockStrategy'], 'union-only')
        self.assertTrue(snapshot['policy']['migration']['retainLegacySalesArc'])
        self.assertFalse(snapshot['policy']['migration']['inferHistoricalActivity'])
        self.assertFalse(snapshot['policy']['scope']['legendaryPlusQuestTier'])
        self.assertEqual([row['quality'] for row in snapshot['policy']['tiers']], list(policy.QUALITIES))

    def test_all_five_milestone_gates_are_required_at_each_boundary(self):
        for tier in policy.policy_snapshot()['policy']['tiers'][1:]:
            totals = {key: tier[key] for key in policy.METRICS}
            row = next(r for r in policy.evaluate_totals(totals)['tiers'] if r['quality'] == tier['quality'])
            self.assertTrue(row['wouldQualify'])
            for metric in policy.METRICS:
                with self.subTest(quality=tier['quality'], metric=metric):
                    below = {**totals, metric: totals[metric] - 1}
                    row = next(r for r in policy.evaluate_totals(below)['tiers'] if r['quality'] == tier['quality'])
                    self.assertFalse(row['wouldQualify'])
                    self.assertEqual(row['remaining'][metric], 1)

    def test_large_group_or_quest_total_cannot_bypass_the_calendar_horizon(self):
        totals = self.totals(1000000)
        totals['activeDays'] = 179
        row = policy.evaluate_totals(totals)['tiers'][-1]
        self.assertFalse(row['wouldQualify'])
        self.assertEqual(row['remaining']['activeDays'], 1)
        self.assertEqual(policy.daily_credit(119, 1000000, 'group')['creditedMinutes'], 1)

    def test_personal_and_group_daily_caps_and_active_day_boundary(self):
        for scope in ['personal', 'group']:
            with self.subTest(scope=scope):
                self.assertEqual(policy.daily_credit(0, 240, scope), {'creditedMinutes': 120, 'totalMinutes': 120, 'activeDay': True})
                self.assertEqual(policy.daily_credit(120, 100, scope)['creditedMinutes'], 0)
                self.assertFalse(policy.daily_credit(0, 29, scope)['activeDay'])
                self.assertTrue(policy.daily_credit(29, 1, scope)['activeDay'])
                self.assertEqual(policy.daily_credit(119, 5, scope)['totalMinutes'], 120)

    def test_malformed_counters_and_corrupt_over_cap_state_are_rejected(self):
        for value in [True, False, -1, 1.5, '30', None, 9007199254740992]:
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    policy.daily_credit(0, value)
                with self.assertRaises(ValueError):
                    policy.evaluate_totals({**self.totals(), 'quests': value})
        with self.assertRaises(ValueError):
            policy.daily_credit(121, 0)
        with self.assertRaises(ValueError):
            policy.daily_credit(0, 1, 'other')
        with self.assertRaises(ValueError):
            policy.evaluate_totals({'eligibleMinutes': 300})
        with self.assertRaises(ValueError):
            policy.evaluate_totals({**self.totals(), 'clientOverride': True})

    def test_utc_day_iso_week_and_year_boundaries(self):
        sunday = millis('2026-11-08T23:59:59.999')
        monday = sunday + 1
        self.assertEqual(policy.utc_day(sunday), '2026-11-08')
        self.assertEqual(policy.utc_day(monday), '2026-11-09')
        self.assertEqual(policy.utc_week(sunday), '2026-W45')
        self.assertEqual(policy.utc_week(monday), '2026-W46')
        self.assertEqual(policy.utc_week(millis('2027-01-01T12:00:00')), '2026-W53')
        self.assertEqual(policy.utc_day(millis('2028-02-29T12:00:00')), '2028-02-29')
        self.assertEqual(policy.utc_day(policy.MAX_TIMESTAMP), '9999-12-31')
        for invalid in [-1, True, policy.MAX_TIMESTAMP + 1]:
            with self.assertRaises(ValueError):
                policy.utc_week(invalid)

    def test_membership_requires_verified_seven_day_tenure(self):
        joined = millis('2026-11-01T00:00:00')
        eligible = joined + 7 * 86400000
        self.assertFalse(policy.membership_eligible(joined, eligible - 1))
        self.assertTrue(policy.membership_eligible(joined, eligible))
        self.assertFalse(policy.membership_eligible(joined, joined - 1))
        self.assertFalse(policy.membership_eligible(None, eligible))
        self.assertFalse(policy.membership_eligible(0, eligible))
        with self.assertRaises(ValueError):
            policy.membership_eligible(True, eligible)

    def test_existing_owner_override_survives_zero_new_progress(self):
        plan = policy.plan_legacy_unlocks(JAVA, [], list(policy.QUALITIES))
        self.assertEqual(plan['preserveQualities'], list(policy.QUALITIES))
        self.assertEqual(plan['revokeQualities'], [])
        self.assertEqual(plan['activityBackfillMinutes'], 0)
        projection = policy.evaluate_totals(self.totals(), plan['preserveQualities'])
        self.assertTrue(all(row['retained'] for row in projection['tiers']))
        self.assertFalse(projection['tiers'][-1]['wouldQualify'])
        self.assertFalse(plan['active'])

    def test_sales_arc_claim_unlocks_the_next_tier_and_never_revokes(self):
        for count in range(6):
            with self.subTest(claims=count):
                plan = policy.plan_legacy_unlocks(JAVA, list(range(count)), [])
                self.assertEqual(plan['preserveQualities'], list(policy.QUALITIES[:min(5, count + 1)]))
                self.assertEqual(plan['revokeQualities'], [])
        earned = policy.plan_legacy_unlocks(JAVA, [], [], ['legendary'])
        self.assertEqual(earned['preserveQualities'], ['common', 'legendary'])

    def test_unknown_evidence_and_corrupt_claim_sequences_stop_migration(self):
        for claims in [None, [True], [1], [0, 2], [0, 0], list(range(6)), '0']:
            with self.subTest(claims=claims), self.assertRaises(ValueError):
                policy.plan_legacy_unlocks(JAVA, claims, [])
        for grants in [None, ['legendary_plus'], ['administrator'], ['rare', 1]]:
            with self.subTest(grants=grants), self.assertRaises(ValueError):
                policy.plan_legacy_unlocks(JAVA, [], grants)

    def test_java_and_floodgate_identities_are_not_guessed_from_names(self):
        for player in [JAVA, BEDROCK]:
            self.assertEqual(policy.plan_legacy_unlocks(player, [], [])['playerUuid'], player)
        for player in [None, 'mr_blueray', '.Player', JAVA.upper(), JAVA.replace('-', ''), '00000000-0000-0000-0000-000000000000']:
            with self.subTest(player=player), self.assertRaises(ValueError):
                policy.plan_legacy_unlocks(player, [], [])

    def test_public_snapshot_cannot_mutate_policy_and_v1_edits_are_rejected(self):
        snapshot = policy.policy_snapshot()
        snapshot['policy']['tiers'][-1]['eligibleMinutes'] = 0
        self.assertEqual(policy.policy_snapshot()['policy']['tiers'][-1]['eligibleMinutes'], 21600)
        original = policy.POLICY_FILE
        try:
            with tempfile.TemporaryDirectory(prefix='quality-policy-qa-') as folder:
                changed = Path(folder) / 'changed.json'
                changed.write_text(json.dumps(snapshot['policy']))
                policy.POLICY_FILE = changed
                policy._policy.cache_clear()
                with self.assertRaises(ValueError):
                    policy.policy_snapshot()
        finally:
            policy.POLICY_FILE = original
            policy._policy.cache_clear()


if __name__ == '__main__':
    unittest.main()
