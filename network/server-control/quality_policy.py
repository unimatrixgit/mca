"""Inactive quality-policy and migration planning primitives.

No route, writer, unlock grant or gameplay integration. Totals and legacy evidence
must come from authoritative stores, never from a browser or movement timer.
"""
import copy
import hashlib
import json
import re
import uuid
from datetime import datetime, timedelta, timezone
from functools import lru_cache
from pathlib import Path

POLICY_FILE = Path(__file__).with_name('quality-policy-v1.json')
PINNED_V1_SHA256 = 'de796ee906904330581da5ef0444228abfc4cc144d9f661af5d6e6dca61b0c7c'
METRICS = ('eligibleMinutes', 'activeDays', 'quests', 'weeklyContracts', 'storyChapters')
QUALITIES = ('common', 'uncommon', 'rare', 'very_rare', 'legendary')
MAX_EXACT = 9007199254740991
MAX_TIMESTAMP = 253402300799999
EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)


def integer(value, maximum=MAX_EXACT):
    if type(value) is not int or not 0 <= value <= maximum:
        raise ValueError('A bounded non-negative integer is required')
    return value


@lru_cache(maxsize=1)
def _policy():
    policy = json.loads(POLICY_FILE.read_text(encoding='utf-8'))
    encoded = json.dumps(policy, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    digest = hashlib.sha256(encoded).hexdigest()
    if digest != PINNED_V1_SHA256:
        raise ValueError('Quality policy v1 changed; record and review a new version')
    return policy


def policy_snapshot():
    return {'policy': copy.deepcopy(_policy()), 'sha256': PINNED_V1_SHA256, 'active': False}


def canonical_player(value):
    if not isinstance(value, str) or not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', value):
        raise ValueError('A verified canonical Minecraft UUID is required')
    if uuid.UUID(value).int == 0:
        raise ValueError('An actual verified Minecraft identity is required')
    return value


def utc_day(timestamp):
    return (EPOCH + timedelta(milliseconds=integer(timestamp, MAX_TIMESTAMP))).date().isoformat()


def utc_week(timestamp):
    day = (EPOCH + timedelta(milliseconds=integer(timestamp, MAX_TIMESTAMP))).date()
    year, week, _ = day.isocalendar()
    return '%04d-W%02d' % (year, week)


def daily_credit(current_minutes, eligible_minutes, scope='personal'):
    """Plan credit from already verified eligible activity; this does not verify activity."""
    if scope not in ('personal', 'group'):
        raise ValueError('Unknown progression scope')
    rule = _policy()[scope]
    current = integer(current_minutes, rule['dailyEligibleMinutesCap'])
    requested = integer(eligible_minutes)
    credited = min(requested, rule['dailyEligibleMinutesCap'] - current)
    total = current + credited
    return {'creditedMinutes': credited, 'totalMinutes': total,
            'activeDay': total >= rule['activeDayMinimumMinutes']}


def membership_eligible(joined_at, event_at):
    """Unknown history earns no group credit. Never infer tenure from a current roster."""
    event_at = integer(event_at, MAX_TIMESTAMP)
    if joined_at is None:
        return False
    joined_at = integer(joined_at, MAX_TIMESTAMP)
    if joined_at == 0 or joined_at > event_at:
        return False
    return event_at - joined_at >= _policy()['group']['minimumMembershipHours'] * 3600000


def known_qualities(values):
    if not isinstance(values, (list, tuple, set, frozenset)):
        raise ValueError('Verified unlock evidence is unavailable; do not migrate')
    if any(not isinstance(value, str) or value not in QUALITIES for value in values):
        raise ValueError('Unknown legacy quality; review before migration')
    return set(values)


def evaluate_totals(totals, retained=()):
    """Read-only gate projection, not evidence validation or a persisted unlock decision."""
    if not isinstance(totals, dict) or set(totals) != set(METRICS):
        raise ValueError('All authoritative progression totals are required')
    totals = {name: integer(totals[name]) for name in METRICS}
    retained = known_qualities(retained)
    rows = []
    for tier in _policy()['tiers']:
        remaining = {name: max(0, tier[name] - totals[name]) for name in METRICS}
        rows.append({'quality': tier['quality'], 'retained': tier['quality'] in retained,
                     'wouldQualify': not any(remaining.values()), 'remaining': remaining})
    return {'policyVersion': 1, 'policySha256': PINNED_V1_SHA256, 'active': False, 'tiers': rows}


def plan_legacy_unlocks(player_uuid, claimed_stages, explicit_qualities, already_earned=()):
    """Preserve the existing Sales arc and explicit grants; never writes or revokes.

    Existing arc stage0 unlocks the *next* tier (Uncommon), not just Common.
    An account grant without a verified current UUID stays at its original source.
    Missing sources or corrupt claim sequences stop planning, rather than replacing
    them with a smaller set of unlocks.
    """
    player_uuid = canonical_player(player_uuid)
    if not isinstance(claimed_stages, (list, tuple)) or any(type(i) is not int for i in claimed_stages):
        raise ValueError('Verified Sales arc claims are required')
    if list(claimed_stages) != list(range(len(claimed_stages))) or len(claimed_stages) > 5:
        raise ValueError('Corrupt Sales arc sequence; retain the existing authority for review')
    explicit = known_qualities(explicit_qualities)
    earned = known_qualities(already_earned)
    arc = set(QUALITIES[:min(5, len(claimed_stages) + 1)])
    preserve = arc | explicit | earned
    return {'playerUuid': player_uuid, 'policyVersion': 1, 'policySha256': PINNED_V1_SHA256,
            'mode': 'dry-run-union-only', 'active': False,
            'preserveQualities': [quality for quality in QUALITIES if quality in preserve],
            'revokeQualities': [], 'activityBackfillMinutes': 0}
