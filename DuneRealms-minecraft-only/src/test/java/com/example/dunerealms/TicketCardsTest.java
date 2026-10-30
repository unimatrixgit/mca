package com.example.dunerealms;

import com.google.gson.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketCardsTest {
    @Test void requestsContainOnlyActualUuidAndFixedView(){
        UUID id=UUID.randomUUID();JsonObject body=JsonParser.parseString(TicketCards.request(id,true)).getAsJsonObject();assertEquals(Set.of("uuid","view"),body.keySet());assertEquals(id.toString(),body.get("uuid").getAsString());assertEquals("staff",body.get("view").getAsString());assertEquals("own",JsonParser.parseString(TicketCards.request(id,false)).getAsJsonObject().get("view").getAsString());
    }
    private String response(String view){return "{\"linked\":true,\"view\":\""+view+"\",\"tickets\":[{\"id\":\"11\",\"number\":\"713113\",\"subject\":\"§cDemo\\nAppeal\",\"status\":\"open\",\"statusLabel\":\"Open\",\"department\":\"Ban appeals\",\"appealStatus\":\"pending\",\"url\":\"javascript:steal()\",\"email\":\"private@example.org\"}]}";}
    @Test void onlyBoundedMetadataIsRenderedAndNativeUrlsCannotBeSuppliedByResponse(){
        TicketCards.Page own=TicketCards.parse(response("own"),false);assertEquals("DemoAppeal",own.cards().get(0).subject());assertEquals("https://support.dunerealms.com/tickets.php?id=11",own.cards().get(0).url());assertEquals("https://support.dunerealms.com/scp/tickets.php?id=11",TicketCards.parse(response("staff"),true).cards().get(0).url());assertFalse(own.toString().contains("private@example.org"));assertFalse(own.toString().contains("javascript:"));
    }
    @Test void mismatchedScopeUnlinkedRecordsMalformedIdsAndOversizedDataFailClosed(){
        assertThrows(IllegalArgumentException.class,()->TicketCards.parse(response("staff"),false));assertThrows(IllegalArgumentException.class,()->TicketCards.parse(response("own").replace("\"linked\":true","\"linked\":false"),false));
        assertThrows(IllegalArgumentException.class,()->TicketCards.parse(response("own").replace("\"11\"","\"../staff\""),false));assertThrows(IllegalArgumentException.class,()->TicketCards.parse("x".repeat(32769),false));
        assertFalse(TicketCards.parse("{\"linked\":false,\"view\":\"own\",\"tickets\":[]}",false).linked());
    }
    @Test void httpBodySubscriberEnforcesByteCapBeforeBufferingOversizedData(){
        TicketCards.LimitedBody body=new TicketCards.LimitedBody();Flow.Subscription subscription=mock(Flow.Subscription.class);body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(new byte[32769])));verify(subscription).cancel();assertThrows(CompletionException.class,()->body.getBody().toCompletableFuture().join());
        TicketCards.LimitedBody valid=new TicketCards.LimitedBody();valid.onSubscribe(mock(Flow.Subscription.class));valid.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2,3})));valid.onComplete();assertArrayEquals(new byte[]{1,2,3},valid.getBody().toCompletableFuture().join());
    }
    @Test void asyncTicketResultsRequireSamePlayerInventoryConnectionAndCurrentViewPermission(){
        UUID id=UUID.randomUUID();Player p=mock(Player.class);Inventory loading=mock(Inventory.class);InventoryView view=mock(InventoryView.class);when(p.isOnline()).thenReturn(true);when(p.getUniqueId()).thenReturn(id);when(p.getOpenInventory()).thenReturn(view);when(view.getTopInventory()).thenReturn(loading);when(p.hasPermission("dune.tickets.view")).thenReturn(true);
        assertTrue(ModerationGui.ticketResultCurrent(p,id,loading,false));assertFalse(ModerationGui.ticketResultCurrent(p,UUID.randomUUID(),loading,false));assertFalse(ModerationGui.ticketResultCurrent(p,id,loading,true));
        when(p.hasPermission("dune.tickets.view")).thenReturn(false);assertFalse(ModerationGui.ticketResultCurrent(p,id,loading,false));when(p.hasPermission("dune.tickets.view")).thenReturn(true);when(view.getTopInventory()).thenReturn(mock(Inventory.class));assertFalse(ModerationGui.ticketResultCurrent(p,id,loading,false));when(view.getTopInventory()).thenReturn(loading);when(p.isOnline()).thenReturn(false);assertFalse(ModerationGui.ticketResultCurrent(p,id,loading,false));
    }
}
