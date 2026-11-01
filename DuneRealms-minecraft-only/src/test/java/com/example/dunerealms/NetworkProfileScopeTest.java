package com.example.dunerealms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NetworkProfileScopeTest {
 @Test void existingPlanetsRemainProfileEnabledRegardlessOfLobbyOptIn(){for(String p:new String[]{"arrakis","giedi_prime","kaitain","caladan","sigma_draconis_iv","GIEDI-PRIME"}){assertTrue(NetworkProfileScope.allowed(p,false));assertTrue(NetworkProfileScope.allowed(p,true));}}
 @Test void lobbyAliasesRequireExplicitOptIn(){for(String p:new String[]{"spacing-guild","spacing_guild","lobby","guild_lobby","GUILD-LOBBY"}){assertFalse(NetworkProfileScope.allowed(p,false));assertTrue(NetworkProfileScope.allowed(p,true));}}
 @Test void unknownNullAndPathLikeIdentifiersRemainExcluded(){for(String p:new String[]{null,"","world","lobby2","unknown"," spacing-guild","arrakis ","../lobby"}){assertFalse(NetworkProfileScope.allowed(p,false));assertFalse(NetworkProfileScope.allowed(p,true));}}
}
