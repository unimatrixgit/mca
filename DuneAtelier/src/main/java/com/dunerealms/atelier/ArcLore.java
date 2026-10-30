package com.dunerealms.atelier;
import com.google.gson.*;import java.io.*;import java.nio.charset.StandardCharsets;
final class ArcLore {
 private static final JsonArray CHAPTERS=load();
 private static JsonArray load(){try(InputStream in=ArcLore.class.getResourceAsStream("/kit-quest-arc-v1.json")){if(in==null)throw new IllegalStateException("Canonical quest story missing");JsonArray rows=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("chapters");if(rows.size()!=5)throw new IllegalStateException("Invalid chapter count");for(int i=0;i<5;i++){JsonObject row=rows.get(i).getAsJsonObject();if(row.get("index").getAsInt()!=i||!row.get("quality").getAsString().equals(DeliveryStore.TIERS.get(i))||row.get("targetSales").getAsInt()!=ArcStore.TARGETS[i]||row.get("minimumPriceDunemarks").getAsLong()*100!=ArcStore.MINIMUM[i]||row.get("rewardDunemarks").getAsLong()*100!=ArcStore.REWARDS[i])throw new IllegalStateException("Canonical quest economy mismatch");}return rows;}catch(IOException e){throw new IllegalStateException("Canonical story unavailable",e);}}
 static String description(int index){return CHAPTERS.get(index).getAsJsonObject().get("description").getAsString();}
 static String templateName(int index){return CHAPTERS.get(index).getAsJsonObject().getAsJsonObject("kitTemplate").get("name").getAsString();}
}
