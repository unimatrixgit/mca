package com.dunerealms.ban;
import java.time.Instant;
import java.util.UUID;
public record BanRecord(UUID id,UUID player,String name,UUID actor,String actorName,String reason,long created,Long expires,String source) {
 public String createdText(){return Instant.ofEpochMilli(created).toString();}
 public String expiresText(){return expires==null?"forever":Instant.ofEpochMilli(expires).toString();}
 public String notice(){return "DuneRealms network ban\n"+reason+"\nReference: "+id+"\nExpires: "+expiresText()+"\nAppeal: https://support.dunerealms.com/appeal.php";}
}
