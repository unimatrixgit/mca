package com.example.dunerealms;
import java.util.Locale;
/** Seconds of clear daylight exposure per point of normal durability damage. */
final class SunWearPolicy {
 private SunWearPolicy() {}
 static int interval(String quality) {
  return switch (quality == null ? "common" : quality.toLowerCase(Locale.ROOT)) {
   case "uncommon" -> 30;
   case "rare" -> 45;
   case "epic" -> 60;
   case "legendary" -> 90;
   default -> 15;
  };
 }
}
