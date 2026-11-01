package com.example.dunemenus;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;

class QuestDiscoveryTest {
 @Test void proofMatchesTheWebsiteCanonicalHmacExactly()throws Exception{
  UUID id=UUID.fromString("00000000-0000-4000-8000-000000000001");String actual=QuestDiscovery.proof("x".repeat(32),id,"a".repeat(64),1790000000000L);
  assertEquals("557583f29f884a9211cc734b05a671822d933d65d27ac5d672e3d07310ca8621",actual);
  assertNotEquals(actual,QuestDiscovery.proof("x".repeat(32),UUID.randomUUID(),"a".repeat(64),1790000000000L));
 }
 @Test void streamLimitCancelsAnOversizedReplyBeforeItIsBuffered(){
  QuestDiscovery.LimitedBody body=new QuestDiscovery.LimitedBody();boolean[] cancelled={false};body.onSubscribe(new Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
  body.onNext(List.of(ByteBuffer.wrap(new byte[32769])));assertTrue(cancelled[0]);assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
 }
 @Test void noNetworkCallIsMadeForExpiredOrMalformedDiscovery()throws Exception{
  assertThrows(IllegalArgumentException.class,()->QuestDiscovery.request("x".repeat(32),UUID.randomUUID(),"a".repeat(64),1));
  assertThrows(IllegalArgumentException.class,()->QuestDiscovery.request("x".repeat(32),UUID.randomUUID(),"forged",System.currentTimeMillis()+10000));
 }
}
