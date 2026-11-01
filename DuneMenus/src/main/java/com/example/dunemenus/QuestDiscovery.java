package com.example.dunemenus;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Server-only authenticated discovery; currency can only be redeemed on the website. */
final class QuestDiscovery {
 static final String QUEST="imperial-portal-eye-ra-v1";
 record Reply(String code,boolean redeemed,String message){}
 static String proof(String secret,UUID uuid,String nonce,long expires)throws Exception{
  String canonical="quest-discovery-v1\n"+QUEST+"\n"+uuid+"\n"+nonce+"\n"+expires;
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
 }
 static Reply request(String secret,UUID actualPlayer,String nonce,long expires)throws Exception{
  if(secret.length()<32||!nonce.matches("[a-f0-9]{64}")||expires<=System.currentTimeMillis()||expires>System.currentTimeMillis()+45000)throw new IllegalArgumentException("Invalid discovery session");
  JsonObject body=new JsonObject();body.addProperty("uuid",actualPlayer.toString());body.addProperty("quest",QUEST);body.addProperty("nonce",nonce);body.addProperty("expires",expires);body.addProperty("proof",proof(secret,actualPlayer,nonce,expires));
  HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
  HttpRequest request=HttpRequest.newBuilder(URI.create("https://cp.dunerealms.com/api/game/quest/discover")).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+secret).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
  HttpResponse<byte[]> response=client.sendAsync(request,info->new LimitedBody()).orTimeout(10,TimeUnit.SECONDS).join();
  if(response.statusCode()==409)return new Reply("",false,"This discovery has already been found by another traveller.");
  if(response.statusCode()==403)return new Reply("",false,"Link your verified Minecraft account in Account Center, then return to this discovery.");
  if(response.statusCode()!=200)throw new IllegalStateException("Discovery service unavailable");
  JsonObject data=JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
  if(!data.has("ok")||!data.get("ok").getAsBoolean()||!data.has("amount")||data.get("amount").getAsInt()!=1000)throw new IllegalArgumentException("Invalid discovery response");
  boolean redeemed=data.has("redeemed")&&data.get("redeemed").getAsBoolean();
  if(redeemed)return new Reply("",true,"Your discovery reward has already been redeemed on the website.");
  String code=data.has("code")?data.get("code").getAsString():"";
  if(!code.matches("DRQ-[A-F0-9]{40}"))throw new IllegalArgumentException("Invalid discovery code");
  return new Reply(code,false,"You found an Imperial secret. Redeem your personal code on the website for 1,000 Dunemarks.");
 }
 static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final CompletableFuture<byte[]> result=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return result;}
  public void onSubscribe(Flow.Subscription s){if(subscription!=null){s.cancel();return;}subscription=s;s.request(1);}
  public void onNext(List<ByteBuffer> buffers){if(result.isDone())return;long remaining=0;for(ByteBuffer b:buffers)remaining+=b.remaining();if(remaining>32768-bytes.size()){subscription.cancel();result.completeExceptionally(new IllegalArgumentException("Response too large"));return;}for(ByteBuffer b:buffers){byte[] chunk=new byte[b.remaining()];b.get(chunk);bytes.writeBytes(chunk);}subscription.request(1);}
  public void onError(Throwable error){result.completeExceptionally(error);}public void onComplete(){result.complete(bytes.toByteArray());}
 }
}
