package com.example.duneess;
import com.example.dunegui.DunemarkItems;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DunemarkDenominationsTest {
 @Test void walletArtSelectsOnlySupportedDenominationsWithoutRoundingUp(){
  assertEquals(1,DunemarkItems.denomination(0)); assertEquals(1,DunemarkItems.denomination(4.99));
  assertEquals(5,DunemarkItems.denomination(5)); assertEquals(5,DunemarkItems.denomination(99.99));
  assertEquals(100,DunemarkItems.denomination(100));assertEquals(1000,DunemarkItems.denomination(4999.99));
  assertEquals(5000,DunemarkItems.denomination(5000));
 }
}
