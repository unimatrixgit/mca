package com.example.dunerealms;
import java.util.Locale;
import java.util.function.ToIntFunction;
/** Seconds of clear daylight exposure per point of normal durability damage. */
final class SunWearPolicy {
 private SunWearPolicy() {}
 static int interval(String quality, ToIntFunction<String> configured) {
  String selected = quality == null ? "common" : quality.toLowerCase(Locale.ROOT);
  String[] qualities = {"common", "uncommon", "rare", "epic", "legendary"};
  int previous = 0, result = 0;
  for (int i = 0; i < qualities.length; i++) {
   // Keep even malformed settings positive, bounded, and strictly increasing.
   int seconds = Math.max(previous + 1, Math.min(86400 - (qualities.length - 1 - i), configured.applyAsInt(qualities[i])));
   if (i == 0 || qualities[i].equals(selected)) result = seconds;
   previous = seconds;
  }
  return result;
 }
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
