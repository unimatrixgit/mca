package com.dunerealms.staff;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Private, bounded server-to-website adapter. Secrets and private responses are never logged. */
final class CaseBridge {
 record Context(long ticket,long version,UUID assigned,UUID creator,UUID suspect,boolean take,boolean setSuspect,boolean teleport,boolean inspect,boolean edit,boolean economy,boolean faction){}
 record Ticket(long id,String number,String subject){}
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final String secret;
 CaseBridge(String origin,String secret){if(!origin.equals("https://cp.dunerealms.com")||secret.length()<32)throw new IllegalArgumentException("Verified bridge configuration required");this.secret=secret;}
 Context context(UUID actor,long ticket,String action,long version,UUID suspect)throws Exception{
  JsonObject body=new JsonObject();body.addProperty("actorUuid",actor.toString());body.addProperty("ticketId",Long.toString(ticket));body.addProperty("action",action);if(!action.equals("read"))body.addProperty("version",version);if(suspect!=null)body.addProperty("suspectUuid",suspect.toString());
  return parse(post("/api/game/ticket-context",body));
 }
 List<Ticket> tickets(UUID actor)throws Exception{
  JsonObject body=new JsonObject();body.addProperty("uuid",actor.toString());body.addProperty("view","staff");JsonObject reply=post("/api/game/tickets",body);List<Ticket> result=new ArrayList<>();JsonArray rows=reply.getAsJsonArray("tickets");if(rows==null||rows.size()>30)throw new IllegalArgumentException("Invalid ticket list");
  for(JsonElement value:rows){JsonObject row=value.getAsJsonObject();long id=positive(row,"id");result.add(new Ticket(id,text(row,"number",32),text(row,"subject",200)));}return List.copyOf(result);
 }
 private JsonObject post(String path,JsonObject body)throws Exception{
  HttpRequest request=HttpRequest.newBuilder(URI.create("https://cp.dunerealms.com"+path)).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+secret).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
  HttpResponse<byte[]> response=client.sendAsync(request,info->new LimitedBody()).orTimeout(10,TimeUnit.SECONDS).get(11,TimeUnit.SECONDS);
  if(response.statusCode()!=200)throw new IllegalStateException("Case context could not be confirmed");return JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();
 }
 static Context parse(JsonObject row)throws Exception{
  JsonObject actions=row.getAsJsonObject("actions");if(actions==null)throw new IllegalArgumentException("Action flags required");
  return new Context(positive(row,"ticketId"),nonnegative(row,"contextVersion"),id(row,"assignedAgentUuid"),id(row,"creatorUuid"),id(row,"suspectUuid"),flag(row,"canTake"),flag(row,"canSetSuspect"),flag(actions,"teleport"),flag(actions,"inspectInventory"),flag(actions,"editInventory"),flag(actions,"adjustEconomy"),flag(actions,"editFaction"));
 }
 static String text(JsonObject row,String key,int limit){String value=row.get(key).getAsString().replaceAll("[\\x00-\\x1f\\x7f§]"," ");if(value.length()>limit)throw new IllegalArgumentException("Oversized metadata");return value;}
 static long positive(JsonObject row,String key){long value=nonnegative(row,key);if(value==0)throw new IllegalArgumentException("Positive reference required");return value;}
 static long nonnegative(JsonObject row,String key){String value=row.get(key).getAsString();if(!value.matches("0|[1-9][0-9]{0,17}"))throw new IllegalArgumentException("Canonical bounded reference required");return Long.parseLong(value);}
 static UUID id(JsonObject row,String key)throws Exception{return !row.has(key)||row.get(key).isJsonNull()?null:CaseStore.identity(row.get(key).getAsString());}
 static boolean flag(JsonObject row,String key){JsonElement value=row.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Boolean authority flag required");return value.getAsBoolean();}
 static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final CompletableFuture<byte[]> future=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
  public CompletionStage<byte[]> getBody(){return future;}
  public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
  public void onNext(List<ByteBuffer> buffers){for(ByteBuffer buffer:buffers){if(bytes.size()+buffer.remaining()>262144){subscription.cancel();future.completeExceptionally(new IllegalArgumentException("Oversized private metadata"));return;}byte[] data=new byte[buffer.remaining()];buffer.get(data);bytes.writeBytes(data);}subscription.request(1);}
  public void onError(Throwable error){future.completeExceptionally(error);}
  public void onComplete(){future.complete(bytes.toByteArray());}
 }
}


