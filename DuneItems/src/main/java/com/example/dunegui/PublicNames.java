package com.example.dunegui;
import java.util.Map;
/** Presentation only; callers retain canonical faction/world keys for persistence and commands. */
public final class PublicNames {
 private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("Atreides","Aerdrecht"),Map.entry("Harkonnen","Harkveen"),Map.entry("Corrino","Korenholt"),Map.entry("Ordos","Oordwijk"),Map.entry("Fremen","Vrijduin"),Map.entry("Spacing Guild","Vaerkring"),Map.entry("Guild Navigators","Vaerkring Routekeepers"),Map.entry("Guild Navigator","Vaerkring Routekeeper"),Map.entry("CHOAM","Koopkring"),Map.entry("Solaris","Dunemarks"),Map.entry("Steward of Dune","Specerhuis"),Map.entry("House of Water","Welvaart House"));
 private static final Map<String,String> WORLDS=Map.of("Arrakis","Arrakduin","Caladan","Kaladam","Giedi Prime","Giedermark","Kaitain","Kaiterhof","Sigma Draconis IV","Sigmadraken IV","Stillsuit","Reclaimer suit","Sietch","Rockhaven");
 private static final Map<String,String> LEGACY=Map.of("Serevan","Arrakduin","Pelareth","Kaladam","Varkesh","Giedermark","Orivane","Kaiterhof","Nivareth","Sigmadraken IV");
 public static String text(String text){if(text==null)return null;for(var pair:LABELS.entrySet())text=text.replaceAll("\\b"+java.util.regex.Pattern.quote(pair.getKey())+"\\b",java.util.regex.Matcher.quoteReplacement(pair.getValue()));for(var pair:WORLDS.entrySet())text=text.replaceAll("\\b"+java.util.regex.Pattern.quote(pair.getKey())+"\\b",java.util.regex.Matcher.quoteReplacement(pair.getValue()));for(var pair:LEGACY.entrySet())text=text.replaceAll("\\b"+java.util.regex.Pattern.quote(pair.getKey())+"\\b",java.util.regex.Matcher.quoteReplacement(pair.getValue()));return text;}
 @SuppressWarnings("unchecked") public static <T>T text(T value){return value instanceof String string?(T)text(string):value;}
 private PublicNames(){}
}
