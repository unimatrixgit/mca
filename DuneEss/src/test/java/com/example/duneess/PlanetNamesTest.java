package com.example.duneess;
import com.example.dunegui.PlanetSigils;
import com.example.dunegui.PublicNames;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlanetNamesTest {
 @Test void newDisplayNamesRetainSavedIdentifiersAndFullCapitalization(){
  assertEquals("Arrakduin",PlanetSigils.label("arrakis"));assertEquals("Kaladam",PlanetSigils.label("caladan"));
  assertEquals("Giedermark",PlanetSigils.label("giedi_prime"));assertEquals("Kaiterhof",PlanetSigils.label("kaitain"));
  assertEquals("Sigmadraken IV",PlanetSigils.label("sigma_draconis_iv"));
  assertEquals("arrakis_world_id",PublicNames.text("arrakis_world_id"));
  assertEquals("/duneess travel arrakis",PublicNames.text("/duneess travel arrakis"));
  assertEquals("Arrakduin and Kaladam",PublicNames.text("Serevan and Pelareth"));
 }
}
