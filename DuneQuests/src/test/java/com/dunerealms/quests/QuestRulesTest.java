package com.dunerealms.quests;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
class QuestRulesTest {
 QuestStore.Event event(String status){return new QuestStore.Event(UUID.randomUUID(),UUID.randomUUID(),"buried-thunder","Hidden discovery","Real encounter",status,1000,301000,UUID.randomUUID());}
 @Test void completionRequiresWholeDurationAndCurrentEligibleOwner(){var e=event("assigned");assertEquals("waiting",QuestRules.outcome(e,true,300999));assertEquals("completed",QuestRules.outcome(e,true,301000));assertEquals("failed",QuestRules.outcome(e,false,301000));assertEquals("failed",QuestRules.outcome(e,false,15000));}
 @Test void stalePreparationCannotPretendAnEncounterOrPayoutOccurred(){assertEquals("waiting",QuestRules.outcome(event("preparing"),true,120999));assertEquals("failed",QuestRules.outcome(event("preparing"),true,121000));assertEquals("waiting",QuestRules.outcome(event("completed"),true,999999));assertEquals("waiting",QuestRules.outcome(event("skipped"),true,999999));}
 @Test void actualWormDefeatDoesNotCompleteForOfflineDeadOrDepartedOwner(){assertEquals("completed",QuestRules.defeated(true));assertEquals("failed",QuestRules.defeated(false));}
 @Test void playerHistoryNeverContainsUnassignedPreparationOrSkippedAttempts(){for(String s:new String[]{"assigned","completed","failed","discovered"})assertTrue(QuestRules.playerVisible(s));for(String s:new String[]{"preparing","skipped","hidden","unassigned","enabled"})assertFalse(QuestRules.playerVisible(s));}
}
