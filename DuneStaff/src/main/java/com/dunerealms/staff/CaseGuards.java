package com.dunerealms.staff;
import org.bukkit.entity.Player;
import java.util.*;

/** Existing authority nodes only; no new rank or implicit OP bypass. */
final class CaseGuards {
 private CaseGuards(){}
 static boolean entry(Player actor){return actor!=null&&actor.isOnline()&&actor.hasPermission("dune.tickets.staff");}
 static boolean subjectChoice(Player actor,Player target){return entry(actor)&&target!=null&&target.isOnline();}
 static boolean action(Player actor){return entry(actor)&&actor.hasPermission("dune.admin");}
 static boolean target(Player actor,Player target,boolean mutable){return action(actor)&&target!=null&&target.isOnline()&&(!mutable||!actor.getUniqueId().equals(target.getUniqueId())&&!target.isOp()&&!target.hasPermission("dune.moderation.protected")&&!target.hasPermission("dune.ban.protected"));}
 static boolean bank(Player actor,boolean credit){return action(actor)&&actor.hasPermission("dunebank.use")&&actor.hasPermission("dunebank.staff.view")&&actor.hasPermission(credit?"dunebank.staff.manage":"dunebank.staff.deposit");}
}

