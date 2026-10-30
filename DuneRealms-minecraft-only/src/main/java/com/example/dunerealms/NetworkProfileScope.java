package com.example.dunerealms;
import java.util.Locale;
import java.util.Set;

/** Explicit opt-in for the existing network profile guard on the Guild lobby. */
final class NetworkProfileScope {
 static boolean allowed(String localPlanet,boolean lobbyEnabled){
  if(localPlanet==null)return false;
  String server=localPlanet.toLowerCase(Locale.ROOT).replace('-','_');
  return Set.of("arrakis","giedi_prime","kaitain","caladan","sigma_draconis_iv").contains(server)
   ||lobbyEnabled&&Set.of("spacing_guild","lobby","guild_lobby").contains(server);
 }
}
