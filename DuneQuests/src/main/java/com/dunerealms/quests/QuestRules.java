package com.dunerealms.quests;
/** Terminal outcomes never imply a payout; the engine has no currency writer. */
public final class QuestRules {
 private QuestRules(){}
 public static String outcome(QuestStore.Event event,boolean eligible,long databaseNow){if(event.status().equals("preparing"))return databaseNow-event.assigned()>=120000?"failed":"waiting";if(!event.status().equals("assigned"))return "waiting";if(!eligible)return "failed";return databaseNow>=event.deadline()?"completed":"waiting";}
 public static String defeated(boolean eligible){return eligible?"completed":"failed";}
 public static boolean playerVisible(String status){return java.util.Set.of("assigned","completed","failed","discovered").contains(status);}
}
