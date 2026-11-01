package com.example.dunemenus;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ViewGateTest {
 @Test void exactOwnerAndViewAreMandatory(){UUID a=UUID.randomUUID();Object exact=new Object();ViewGate gate=new ViewGate(a,exact,"abc",100,()->true);assertTrue(gate.current(a,exact,99));assertFalse(gate.current(UUID.randomUUID(),exact,99));assertFalse(gate.current(a,new Object(),99));}
 @Test void expiryBoundaryFailsClosed(){UUID a=UUID.randomUUID();Object exact=new Object();ViewGate gate=new ViewGate(a,exact,"abc",100,()->true);assertFalse(gate.current(a,exact,100));assertFalse(gate.claim(a,"abc",100));}
 @Test void opaqueTokenAndSingleUseRequired(){UUID a=UUID.randomUUID();ViewGate gate=new ViewGate(a,new Object(),"abc",100,()->true);assertFalse(gate.claim(a,"different",1));assertTrue(gate.claim(a,"abc",1));assertFalse(gate.claim(a,"abc",1));assertFalse(gate.current(a,gate.model,1));}
 @Test void permissionRevocationBeforeAndAfterClaimIsObserved(){UUID a=UUID.randomUUID();AtomicBoolean allow=new AtomicBoolean(true);ViewGate gate=new ViewGate(a,new Object(),"abc",100,allow::get);assertTrue(gate.claim(a,"abc",1));allow.set(false);assertFalse(gate.allowedAfterClaim(a,2));}
 @Test void cancellationInvalidatesPendingChoice(){UUID a=UUID.randomUUID();ViewGate gate=new ViewGate(a,new Object(),"abc",100,()->true);gate.cancel();assertFalse(gate.claim(a,"abc",1));assertFalse(gate.current(a,gate.model,1));}
 @Test void tokensAreOpaqueIndependentAndFixedSize(){Set<String> values=new HashSet<>();for(int i=0;i<200;i++){String s=DuneMenus.token();assertTrue(s.matches("[a-f0-9]{64}"));assertTrue(values.add(s));}}
 @Test void longUnbanReasonAndMetadataAreKeptAcrossReadableChunks(){String text="A".repeat(900)+"\nExact previous reason";List<String> parts=DuneMenus.chunks(text,32);String all=String.join("",parts).replace("\n","");assertEquals(text.replace("\n",""),all);assertTrue(parts.stream().allMatch(p->p.length()<=32*9));}
 @Test void bedrockFactionButtonsUseFiveDistinctPublishedCrestsAndGenericPortal(){Set<String> urls=new HashSet<>();for(int i=0;i<5;i++){String url=DuneMenus.image(String.valueOf((char)(0xe210+i)));assertTrue(url.startsWith("https://www.dunerealms.com/assets/gui/faction-"));assertTrue(url.endsWith("-heraldic.png"));assertTrue(urls.add(url));}assertTrue(DuneMenus.image("\ue202").endsWith("/imperial-portal.png"));}
}
