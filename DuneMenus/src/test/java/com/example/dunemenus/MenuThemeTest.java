package com.example.dunemenus;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MenuThemeTest {
 @Test void serviceHeadersUseMatchingArtwork(){assertEquals("imperial-shop-v1.png",MenuTheme.of("Koopkring | equipment").file());assertEquals("imperial-jobs-v1.png",MenuTheme.of("Manage Job").file());assertEquals("HOUSE & CLAN",MenuTheme.of("Clan Shop").name());assertEquals("IMPERIAL STAFF",MenuTheme.of("Staff Operations").name());}
 @Test void unknownTitleRemainsReadable(){assertEquals("IMPERIAL NAVIGATOR",MenuTheme.of("Travel").name());}
}
