package com.example.dunerealms.conquest;

import java.util.Objects;

/** Pure progress calculation. The future SQL authority must commit its result before publishing events. */
public final class CaptureProgress {
    private CaptureProgress() {}

    public enum Mode { CAPTURE_ONLY, CAPTURE_AND_HOLD }
    public enum Phase { LOCKED, AVAILABLE, CAPTURING, CONTESTED, OCCUPYING, COMPLETED }

    public record Rules(Mode mode, int captureSeconds, int holdSeconds, int minimumAttackers) {
        public Rules {
            Objects.requireNonNull(mode);
            if (captureSeconds < 1 || captureSeconds > 7200 || holdSeconds < 1 || holdSeconds > 7200
                    || minimumAttackers < 1 || minimumAttackers > 12) {
                throw new IllegalArgumentException("Invalid capture rules");
            }
        }
    }

    /** Counts must come from an authoritative eligible-player observer, never a client request. */
    public record Sample(long lease, long generation, long expectedVersion, long sequence,
                         long observedMillis, int attackers, int enemies,
                         boolean campaignActive, boolean prerequisitesMet) {
        public Sample {
            if (lease < 1 || generation < 1 || expectedVersion < 0 || sequence < 1 || observedMillis < 0
                    || attackers < 0 || attackers > 12 || enemies < 0 || enemies > 12) {
                throw new IllegalArgumentException("Invalid capture sample");
            }
        }
    }

    public record State(Rules rules, long lease, long generation, long version,
                        long sequence, long observedMillis, long captureMillis, long holdMillis,
                        boolean captured, Phase phase) {
        public State {
            Objects.requireNonNull(rules);
            Objects.requireNonNull(phase);
            if (lease < 1 || generation < 1 || version < 0 || sequence < 0 || observedMillis < -1
                    || (sequence == 0) != (observedMillis == -1)
                    || captureMillis < 0 || captureMillis > rules.captureSeconds() * 1000L
                    || holdMillis < 0 || holdMillis > rules.holdSeconds() * 1000L
                    || (captured != (captureMillis == rules.captureSeconds() * 1000L))
                    || (!captured && holdMillis != 0)
                    || (rules.mode() == Mode.CAPTURE_ONLY && holdMillis != 0)
                    || (phase == Phase.OCCUPYING && (!captured || rules.mode() != Mode.CAPTURE_AND_HOLD))
                    || (phase == Phase.COMPLETED && (!captured
                        || (rules.mode() == Mode.CAPTURE_AND_HOLD && holdMillis != rules.holdSeconds() * 1000L)))) {
                throw new IllegalArgumentException("Invalid capture state");
            }
        }
    }

    public record Result(State state, boolean capturedNow, boolean completedNow) {}

    public static State initial(Rules rules, long lease, long generation) {
        return new State(rules, lease, generation, 0, 0, -1, 0, 0, false, Phase.LOCKED);
    }

    /** A new process/lease cannot continue a previous process's continuous hold or monotonic clock. */
    public static State resume(State previous, long newLease) {
        Objects.requireNonNull(previous);
        if (newLease <= previous.lease()) throw new IllegalArgumentException("Lease must increase");
        if (previous.phase() == Phase.COMPLETED) throw new IllegalArgumentException("Completed generation is terminal");
        return new State(previous.rules(), newLease, previous.generation(), Math.incrementExact(previous.version()),
                0, -1, previous.captureMillis(), 0, previous.captured(), Phase.LOCKED);
    }

    /**
     * receivedMillis uses the same backend monotonic clock/lease as observedMillis.
     * First samples establish an anchor. At most 1s is credited per accepted observation;
     * gaps over 1.5s establish a new anchor and break continuous occupation.
     */
    public static Result advance(State before, Sample sample, long receivedMillis) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(sample);
        if (sample.lease() != before.lease() || sample.generation() != before.generation()
                || sample.expectedVersion() != before.version() || sample.sequence() <= before.sequence()
                || receivedMillis < sample.observedMillis() || receivedMillis - sample.observedMillis() > 2000
                || (before.observedMillis() >= 0 && sample.observedMillis() <= before.observedMillis())) {
            throw new IllegalArgumentException("Stale, replayed or unfenced sample");
        }
        if (before.phase() == Phase.COMPLETED) return new Result(before, false, false);

        long gap = before.observedMillis() < 0 ? 0 : sample.observedMillis() - before.observedMillis();
        long elapsed = gap > 1500 ? 0 : Math.min(gap, 1000);
        long capture = before.captureMillis();
        long hold = gap > 1500 ? 0 : before.holdMillis();
        boolean captured = before.captured();
        boolean capturedNow = false;
        Phase phase;
        boolean enabled = sample.campaignActive() && sample.prerequisitesMet();
        boolean qualifying = sample.attackers() >= before.rules().minimumAttackers();

        if (!enabled) {
            hold = 0;
            phase = Phase.LOCKED;
        } else if (sample.enemies() > 0 && sample.attackers() > 0) {
            hold = 0;
            phase = Phase.CONTESTED;
        } else if (qualifying && sample.enemies() == 0) {
            if (!captured) {
                // An inactive/locked interval cannot become capture progress on reactivation.
                if (before.phase() == Phase.CAPTURING) capture = Math.min(before.rules().captureSeconds() * 1000L, capture + elapsed);
                captured = capture == before.rules().captureSeconds() * 1000L;
                capturedNow = captured;
            } else if (before.phase() == Phase.OCCUPYING) {
                hold = Math.min(before.rules().holdSeconds() * 1000L, hold + elapsed);
            }
            phase = !captured ? Phase.CAPTURING
                    : before.rules().mode() == Mode.CAPTURE_ONLY || hold == before.rules().holdSeconds() * 1000L
                        ? Phase.COMPLETED : Phase.OCCUPYING;
        } else {
            hold = 0;
            // Default defender reversal / empty-point decay; neither changes territorial ownership.
            if (!captured) capture = Math.max(0, capture - elapsed);
            phase = Phase.AVAILABLE;
        }
        State after = new State(before.rules(), before.lease(), before.generation(),
                Math.incrementExact(before.version()), sample.sequence(), sample.observedMillis(),
                capture, hold, captured, phase);
        return new Result(after, capturedNow, phase == Phase.COMPLETED);
    }
}
