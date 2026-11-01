package com.dunerealms.bank;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.entity.Player;
import com.example.dunegui.NativeMenus;
import java.util.*;
import java.math.BigDecimal;
import java.lang.reflect.Method;
class NativePermissionsTest{
 @Test void personalRequiresCanonicalUseButNoStaffNode(){Player p=mock(Player.class);when(p.hasPermission("dunebank.use")).thenReturn(true);assertTrue(DuneBank.entryAllowed(p,false));assertTrue(DuneBank.transferAllowed(p,false,false));assertTrue(DuneBank.transferAllowed(p,false,true));assertFalse(DuneBank.entryAllowed(p,true));verify(p,never()).hasPermission("dune.bank.view");}
 @Test void staffViewDepositAndManageAreIndependentAndRechecked(){Player p=mock(Player.class);when(p.hasPermission("dunebank.use")).thenReturn(true);when(p.hasPermission("dunebank.staff.view")).thenReturn(true);assertTrue(DuneBank.entryAllowed(p,true));assertFalse(DuneBank.transferAllowed(p,true,false));assertFalse(DuneBank.transferAllowed(p,true,true));when(p.hasPermission("dunebank.staff.deposit")).thenReturn(true);assertTrue(DuneBank.transferAllowed(p,true,false));assertFalse(DuneBank.transferAllowed(p,true,true));when(p.hasPermission("dunebank.staff.manage")).thenReturn(true);assertTrue(DuneBank.transferAllowed(p,true,true));when(p.hasPermission("dunebank.staff.view")).thenReturn(false);assertFalse(DuneBank.transferAllowed(p,true,false));assertFalse(DuneBank.transferAllowed(p,true,true));}
 @Test void actualStaffChoicesHideNativeDeniedActionsEvenIfSqlAllowed()throws Exception{DuneBank plugin=mock(DuneBank.class,CALLS_REAL_METHODS);Player p=mock(Player.class);when(p.hasPermission("dunebank.use")).thenReturn(true);when(p.hasPermission("dunebank.staff.view")).thenReturn(true);when(p.hasPermission("dunebank.staff.manage")).thenReturn(true);var v=new DuneBank.View(new BigDecimal("100"),new BigDecimal("250"),List.of(),new BankStore.Access(true,true,true));Method show=DuneBank.class.getDeclaredMethod("show",Player.class,boolean.class,DuneBank.View.class);show.setAccessible(true);try(var menus=mockStatic(NativeMenus.class)){menus.when(()->NativeMenus.show(eq(plugin),eq(p),anyString(),anyString(),anyList())).thenAnswer(call->{List<NativeMenus.Choice> choices=call.getArgument(4);assertFalse(choices.stream().anyMatch(c->c.label().startsWith("Deposit ")));assertTrue(choices.stream().anyMatch(c->c.label().startsWith("Withdraw ")));return null;});show.invoke(plugin,p,true,v);menus.verify(()->NativeMenus.show(eq(plugin),eq(p),anyString(),anyString(),anyList()),times(1));when(p.hasPermission("dunebank.staff.view")).thenReturn(false);show.invoke(plugin,p,true,v);menus.verifyNoMoreInteractions();}}
}
