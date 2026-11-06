package com.example.dunerealms.conquest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.dunerealms.conquest.CaptureProgress.*;

class CaptureProgressTest {
    private State fresh(Mode mode) { return initial(new Rules(mode, 2, 2, 3), 7, 1); }
    private Result observe(State s, long time, int attackers, int enemies, boolean active, boolean prerequisites) {
        return advance(s, new Sample(s.lease(), s.generation(), s.version(), s.sequence() + 1,
                time, attackers, enemies, active, prerequisites), time);
    }
    private State tick(State s, long time) { return observe(s, time, 3, 0, true, true).state(); }
    private State capturing() { return tick(fresh(Mode.CAPTURE_AND_HOLD), 0); }
    private State occupying() { return tick(tick(capturing(), 1000), 2000); }

    @Test void firstObservationCannotCreditTimeBeforeObservation() {
        State s = tick(fresh(Mode.CAPTURE_ONLY), 900000);
        assertEquals(0, s.captureMillis()); assertEquals(Phase.CAPTURING, s.phase());
    }
    @Test void captureOnlyCompletesOnceAndDoesNotPayAgain() {
        State s = tick(tick(fresh(Mode.CAPTURE_ONLY), 0), 1000);
        Result win = observe(s, 2000, 3, 0, true, true);
        assertTrue(win.capturedNow()); assertTrue(win.completedNow());
        assertEquals(Phase.COMPLETED, win.state().phase());
        Result replay = observe(win.state(), 3000, 3, 0, true, true);
        assertSame(win.state(), replay.state()); assertFalse(replay.capturedNow()); assertFalse(replay.completedNow());
    }
    @Test void captureAndHoldRequireSeparateContinuousIntervals() {
        State s = occupying();
        assertTrue(s.captured()); assertEquals(0, s.holdMillis()); assertEquals(Phase.OCCUPYING, s.phase());
        s = tick(s, 3000); assertEquals(1000, s.holdMillis());
        Result done = observe(s, 4000, 3, 0, true, true);
        assertFalse(done.capturedNow()); assertTrue(done.completedNow());
    }
    @Test void additionalAttackersDoNotAccelerateCapture() {
        State a = fresh(Mode.CAPTURE_ONLY), b = fresh(Mode.CAPTURE_ONLY);
        for (long time : new long[]{0, 500, 1000}) {
            a = observe(a, time, 3, 0, true, true).state();
            b = observe(b, time, 12, 0, true, true).state();
        }
        assertEquals(a.captureMillis(), b.captureMillis()); assertEquals(1000, a.captureMillis());
    }
    @Test void contestPausesCaptureAndDoesNotCreditTheContestedInterval() {
        State s = tick(capturing(), 1000);
        s = observe(s, 2000, 3, 1, true, true).state();
        assertEquals(1000, s.captureMillis()); assertEquals(Phase.CONTESTED, s.phase());
        s = tick(s, 3000); assertEquals(1000, s.captureMillis()); assertFalse(s.captured());
        assertTrue(observe(s, 4000, 3, 0, true, true).capturedNow());
    }
    @Test void anyEnemyContestsEvenWhenAttackersAreBelowMinimum() {
        State s = observe(capturing(), 1000, 1, 1, true, true).state();
        assertEquals(Phase.CONTESTED, s.phase()); assertEquals(0, s.captureMillis());
    }
    @Test void defenderReversalCannotCaptureOrChangeOwnership() {
        State s = tick(capturing(), 1000);
        Result reversed = observe(s, 2000, 0, 3, true, true);
        assertEquals(0, reversed.state().captureMillis()); assertFalse(reversed.capturedNow());
        assertFalse(reversed.state().captured()); assertEquals(Phase.AVAILABLE, reversed.state().phase());
    }
    @Test void emptyAndInsufficientPresenceCannotEarnProgress() {
        State s = tick(capturing(), 1000);
        s = observe(s, 1500, 0, 0, true, true).state(); assertEquals(500, s.captureMillis());
        s = observe(s, 2000, 2, 0, true, true).state(); assertEquals(0, s.captureMillis());
        s = tick(s, 3000); assertEquals(0, s.captureMillis());
    }
    @Test void contestBreaksContinuousHold() {
        State s = tick(occupying(), 3000);
        s = observe(s, 4000, 3, 1, true, true).state(); assertEquals(0, s.holdMillis());
        s = tick(s, 5000); assertEquals(0, s.holdMillis()); assertTrue(s.captured());
        s = tick(s, 6000); assertEquals(1000, s.holdMillis()); assertEquals(Phase.OCCUPYING, s.phase());
    }
    @Test void insufficientOccupancyBreaksHoldWithoutReversingTerritorialCapture() {
        State s = tick(occupying(), 3000);
        s = observe(s, 4000, 2, 0, true, true).state();
        assertEquals(0, s.holdMillis()); assertTrue(s.captured());
        assertEquals(2000, s.captureMillis()); assertEquals(Phase.AVAILABLE, s.phase());
    }
    @Test void lostPrerequisiteResetsHoldAndReactivationOnlyAnchors() {
        State s = tick(occupying(), 3000);
        s = observe(s, 4000, 3, 0, true, false).state();
        assertEquals(Phase.LOCKED, s.phase()); assertEquals(0, s.holdMillis());
        s = tick(s, 5000); assertEquals(0, s.holdMillis());
    }
    @Test void inactiveCampaignCannotProgressCapture() {
        State s = observe(fresh(Mode.CAPTURE_ONLY), 0, 3, 0, false, true).state();
        s = observe(s, 1000, 3, 0, false, true).state();
        s = tick(s, 2000); assertEquals(0, s.captureMillis());
        assertEquals(1000, tick(s, 3000).captureMillis());
    }
    @Test void longGapDoesNotAwardOfflineCaptureOrHold() {
        State capture = tick(capturing(), 1000);
        assertEquals(1000, tick(capture, 100000).captureMillis());
        State hold = tick(occupying(), 3000);
        hold = tick(hold, 100000); assertEquals(0, hold.holdMillis());
        assertEquals(1000, tick(hold, 101000).holdMillis());
    }
    @Test void shortLagIsCappedAtOneSecondAndGapBoundaryIsConservative() {
        assertEquals(1000, tick(capturing(), 1500).captureMillis());
        assertEquals(0, tick(capturing(), 1501).captureMillis());
    }
    @Test void duplicateOrRegressingSequenceCannotAdvance() {
        State s = capturing();
        assertThrows(IllegalArgumentException.class, () -> advance(s, new Sample(7, 1, s.version(), 1, 1000, 3, 0, true, true), 1000));
        assertEquals(0, s.captureMillis());
    }
    @Test void equalOrRegressingObservationIsRejected() {
        State s = tick(capturing(), 1000);
        assertThrows(IllegalArgumentException.class, () -> tick(s, 1000));
        assertThrows(IllegalArgumentException.class, () -> tick(s, 999));
    }
    @Test void staleAndFutureObservationsAreRejected() {
        State s = fresh(Mode.CAPTURE_ONLY);
        Sample sample = new Sample(7, 1, 0, 1, 1000, 3, 0, true, true);
        assertThrows(IllegalArgumentException.class, () -> advance(s, sample, 999));
        assertThrows(IllegalArgumentException.class, () -> advance(s, sample, 3001));
        assertEquals(0, advance(s, sample, 3000).state().captureMillis());
    }
    @Test void wrongLeaseGenerationOrExpectedVersionIsRejected() {
        State s = capturing();
        for (Sample sample : new Sample[]{new Sample(6, 1, s.version(), 2, 1000, 3, 0, true, true),
                new Sample(7, 2, s.version(), 2, 1000, 3, 0, true, true),
                new Sample(7, 1, 0, 2, 1000, 3, 0, true, true)}) {
            assertThrows(IllegalArgumentException.class, () -> advance(s, sample, 1000));
        }
    }
    @Test void leaseRecoveryPreservesCaptureButResetsHoldAndClock() {
        State old = tick(occupying(), 3000);
        State recovered = resume(old, 8);
        assertTrue(recovered.captured()); assertEquals(0, recovered.holdMillis());
        assertEquals(old.version() + 1, recovered.version());
        recovered = tick(recovered, 10); assertEquals(0, recovered.holdMillis());
        State current = recovered;
        assertThrows(IllegalArgumentException.class, () -> advance(current,
                new Sample(7, 1, current.version(), 99, 1010, 3, 0, true, true), 1010));
        assertEquals(1000, tick(recovered, 1010).holdMillis());
    }
    @Test void recoveryCannotReuseLeaseOrReopenCompletedGeneration() {
        State s = fresh(Mode.CAPTURE_ONLY);
        assertThrows(IllegalArgumentException.class, () -> resume(s, 7));
        State done = tick(tick(tick(s, 0), 1000), 2000);
        assertThrows(IllegalArgumentException.class, () -> resume(done, 8));
    }
    @Test void invalidRulesCountsAndImpossibleStateAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Rules(Mode.CAPTURE_ONLY, 0, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new Rules(Mode.CAPTURE_ONLY, 2, 7201, 3));
        assertThrows(IllegalArgumentException.class, () -> new Rules(Mode.CAPTURE_ONLY, 2, 2, 13));
        assertThrows(IllegalArgumentException.class, () -> new Sample(7, 1, 0, 1, 0, 13, 0, true, true));
        assertThrows(IllegalArgumentException.class, () -> new Sample(7, 1, 0, 1, 0, 3, -1, true, true));
        assertThrows(IllegalArgumentException.class, () -> new State(new Rules(Mode.CAPTURE_ONLY, 2, 2, 3),
                7, 1, 0, 0, -1, 2000, 0, false, Phase.CAPTURING));
    }
}
