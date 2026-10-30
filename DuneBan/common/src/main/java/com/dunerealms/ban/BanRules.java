package com.dunerealms.ban;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
public final class BanRules {
 private BanRules(){}
 public static String scope(String value){if(!"local".equals(value)&&!"global".equals(value))throw new IllegalArgumentException("Choose local or global scope.");return value;}
 public static String reason(String value){if(value==null||value.trim().length()<5||value.length()>500||value.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x00a7))throw new IllegalArgumentException("Use a single-line reason of 5–500 characters.");return value.trim();}
 public static String username(String value){if(value==null||!value.matches("[A-Za-z0-9_]{3,16}"))throw new IllegalArgumentException("Use an exact Java username, or select an online Bedrock player in the GUI.");return value;}
 public static long created(String value){try{long epoch=Instant.parse(value).toEpochMilli();if(epoch<=0)throw new IllegalArgumentException();return epoch;}catch(Exception e){throw new IllegalArgumentException("Use the exact created timestamp from the ban list.");}}
 public static Long duration(Duration duration){if(duration==null)return null;long millis;try{millis=duration.toMillis();}catch(ArithmeticException e){throw new IllegalArgumentException("Invalid duration.");}if(millis<1000||millis>Duration.ofDays(3650).toMillis())throw new IllegalArgumentException("Duration must be one second to ten years, or permanent.");return millis;}
 public static void target(UUID actor,UUID target,boolean op,boolean protectedTarget){if(actor==null||target==null||actor.equals(target)||op||protectedTarget)throw new IllegalArgumentException("You cannot punish yourself, an operator or a protected account.");}
}
