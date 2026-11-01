package com.example.dunemenus;
import java.util.Locale;
/** Presentation categories only; never grants access or changes callback behavior. */
final class MenuTheme {
 record Theme(String name,String glyph,String file){}
 static Theme of(String title){String s=DuneMenus.plain(title).toLowerCase(Locale.ROOT);
 if(s.contains("job")||s.contains("contract")||s.contains("miner"))return new Theme("SPICE GUILD","\ue221","imperial-jobs-v1.png");
 if(s.contains("staff")||s.contains("moderation")||s.contains("ban")||s.contains("ticket"))return new Theme("IMPERIAL STAFF","\ue224","imperial-portal.png");
 if(s.contains("clan")||s.contains("faction")||s.contains("house"))return new Theme("HOUSE & CLAN","\ue223","faction-atreides-aerdrecht-heraldic.png");
 if(s.contains("shop")||s.contains("exchange")||s.contains("koopkring")||s.contains("market")||s.contains("buy")||s.contains("sell"))return new Theme("IMPERIAL MARKET","\ue220","imperial-shop-v1.png");
 return new Theme("IMPERIAL NAVIGATOR","\ue222","imperial-portal.png");}
}
