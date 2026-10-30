package com.dunerealms.staff;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import java.util.*;
class CaseGuardsTest {
 Player player(){Player p=mock(Player.class);when(p.isOnline()).thenReturn(true);when(p.getUniqueId()).thenReturn(UUID.randomUUID());return p;}
 @Test void supportCanChooseActualOnlineSuspectWithoutGameAdminButCannotMutateTarget(){Player a=player(),t=player();when(a.hasPermission("dune.tickets.staff")).thenReturn(true);when(t.isOp()).thenReturn(true);assertTrue(CaseGuards.subjectChoice(a,t));assertFalse(CaseGuards.action(a));assertFalse(CaseGuards.target(a,t,true));when(t.isOnline()).thenReturn(false);assertFalse(CaseGuards.subjectChoice(a,t));when(t.isOnline()).thenReturn(true);when(a.hasPermission("dune.tickets.staff")).thenReturn(false);assertFalse(CaseGuards.subjectChoice(a,t));}
 @Test void operatorFlagAloneDoesNotConferCaseAuthority(){Player p=player();when(p.isOp()).thenReturn(true);assertFalse(CaseGuards.entry(p));assertFalse(CaseGuards.action(p));}
 @Test void staffPermissionIsRequiredAlongsideBroadAdmin(){Player p=player();when(p.hasPermission("dune.admin")).thenReturn(true);assertFalse(CaseGuards.action(p));when(p.hasPermission("dune.tickets.staff")).thenReturn(true);assertTrue(CaseGuards.action(p));}
 @Test void offlineActorOrTargetAndProtectedOrSelfInventoryReject(){Player a=player(),t=player();when(a.hasPermission("dune.admin")).thenReturn(true);when(a.hasPermission("dune.tickets.staff")).thenReturn(true);assertTrue(CaseGuards.target(a,t,true));assertFalse(CaseGuards.target(a,a,true));when(t.isOp()).thenReturn(true);assertFalse(CaseGuards.target(a,t,true));assertTrue(CaseGuards.target(a,t,false));when(t.isOp()).thenReturn(false);when(t.hasPermission("dune.ban.protected")).thenReturn(true);assertFalse(CaseGuards.target(a,t,true));when(t.hasPermission("dune.ban.protected")).thenReturn(false);when(t.isOnline()).thenReturn(false);assertFalse(CaseGuards.target(a,t,false));when(t.isOnline()).thenReturn(true);when(a.isOnline()).thenReturn(false);assertFalse(CaseGuards.target(a,t,true));}
 @Test void walletCorrectionNeedsDirectionSpecificBankPermissions(){Player a=player();when(a.hasPermission("dune.tickets.staff")).thenReturn(true);when(a.hasPermission("dune.admin")).thenReturn(true);assertFalse(CaseGuards.bank(a,true));when(a.hasPermission("dunebank.use")).thenReturn(true);when(a.hasPermission("dunebank.staff.view")).thenReturn(true);when(a.hasPermission("dunebank.staff.deposit")).thenReturn(true);assertTrue(CaseGuards.bank(a,false));assertFalse(CaseGuards.bank(a,true));when(a.hasPermission("dunebank.staff.manage")).thenReturn(true);assertTrue(CaseGuards.bank(a,true));}
}

