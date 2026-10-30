package com.example.dunerealms;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;

/** Bounded metadata only. Native URLs are rebuilt from verified numeric IDs, not supplied by JSON. */
final class TicketCards {
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody(){return result;}
        @Override public void onSubscribe(Flow.Subscription s){if(subscription!=null){s.cancel();return;}subscription=s;s.request(1);}
        @Override public void onNext(List<ByteBuffer> buffers){
            if(result.isDone())return;
            long remaining=0;for(ByteBuffer buffer:buffers)remaining+=buffer.remaining();
            if(remaining>32768-bytes.size()){subscription.cancel();result.completeExceptionally(new IllegalArgumentException("Ticket response too large"));return;}
            for(ByteBuffer buffer:buffers){byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}subscription.request(1);
        }
        @Override public void onError(Throwable error){result.completeExceptionally(error);}
        @Override public void onComplete(){result.complete(bytes.toByteArray());}
    }
    record Card(long id,String number,String subject,String status,String department,String appealStatus,String url) {}
    record Page(boolean linked,List<Card> cards) {}
    private TicketCards() {}
    static String request(UUID actualPlayer,boolean staff){JsonObject body=new JsonObject();body.addProperty("uuid",Objects.requireNonNull(actualPlayer).toString());body.addProperty("view",staff?"staff":"own");return body.toString();}
    static String clean(JsonElement value,int limit){
        if(value==null||value.isJsonNull()||!value.isJsonPrimitive())return "";
        String text=value.getAsString().replaceAll("(?i)§[0-9A-FK-ORX]","").replaceAll("[\\p{Cntrl}]","").trim();
        return text.length()>limit?text.substring(0,limit-1)+"…":text;
    }
    static Page parse(String json,boolean staff){
        if(json==null||json.length()>32768)throw new IllegalArgumentException("Oversized ticket response");
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        if(!root.has("view")||!root.get("view").getAsString().equals(staff?"staff":"own")||!root.has("linked")||!root.get("linked").isJsonPrimitive()||!root.get("linked").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid ticket scope");
        boolean linked=root.get("linked").getAsBoolean();JsonArray rows=root.getAsJsonArray("tickets");if(rows==null||rows.size()>30)throw new IllegalArgumentException("Invalid ticket records");
        if(!linked&&rows.size()>0)throw new IllegalArgumentException("Unlinked ticket records");
        List<Card> cards=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(JsonElement element:rows){JsonObject row=element.getAsJsonObject();String raw=clean(row.get("id"),20);if(!raw.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("Invalid ticket ID");long id=Long.parseLong(raw);if(!seen.add(id))throw new IllegalArgumentException("Duplicate ticket ID");
            String number=clean(row.get("number"),32),subject=clean(row.get("subject"),80),status=clean(row.has("statusLabel")?row.get("statusLabel"):row.get("status"),40),department=clean(row.get("department"),64),appeal=clean(row.get("appealStatus"),24);
            if(!Set.of("","pending","approving","approved","denied","closed","unavailable").contains(appeal))appeal="unavailable";
            cards.add(new Card(id,number.isBlank()?raw:number,subject.isBlank()?"Support ticket":subject,status.isBlank()?"Unknown":status,department,appeal,"https://support.dunerealms.com/"+(staff?"scp/":"")+"tickets.php?id="+id));
        }
        return new Page(linked,List.copyOf(cards));
    }
}
