package com.example.duneitems;
import org.junit.jupiter.api.Test;
import org.bukkit.Color;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OfficeBlueprintTest{
 @Test void validBlueprintHasOnlyPlainCosmeticFields(){OfficeBlueprint b=OfficeBlueprint.parse("  Imperial Dawn  ","#123aBc","[\"First line\",\"Second line\"]",2);assertEquals("Imperial Dawn",b.name());assertEquals(Color.fromRGB(0x123abc),b.color());assertEquals(List.of("First line","Second line"),b.lore());assertThrows(UnsupportedOperationException.class,()->b.lore().add("x"));}
 @Test void malformedBlueprintFailsClosed(){for(String json:List.of("{}","null","[1]","[{}]","[null]","[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"]","[\""+"x".repeat(121)+"\"]"))assertThrows(RuntimeException.class,()->OfficeBlueprint.parse("Name","#123456",json,1));for(String name:List.of("", "x".repeat(65),"<click:run_command>","line\nnext","bad\u00a7cname"))assertThrows(IllegalArgumentException.class,()->OfficeBlueprint.parse(name,"#123456","[]",1));assertThrows(IllegalArgumentException.class,()->OfficeBlueprint.parse("Name","#123456;DROP","[]",1));assertThrows(IllegalArgumentException.class,()->OfficeBlueprint.parse("Name","#123456","[]",0));}
 @Test void unicodeLimitsMatchWebsiteCodePoints(){assertEquals(64,OfficeBlueprint.plain("\ud83c\udf1f".repeat(64),64).codePointCount(0,128));assertThrows(IllegalArgumentException.class,()->OfficeBlueprint.plain("\ud83c\udf1f".repeat(65),64));}
 @Test void databaseReadBindsExactOwnerOfficeTermAndSharedLock()throws Exception{Connection c=mock(Connection.class);PreparedStatement q=mock(PreparedStatement.class);ResultSet r=mock(ResultSet.class);String sql="SELECT version,kit_name,color,lore_json FROM dune_office_kit_definitions WHERE owner_uuid=? AND office_key=? AND term_id=? FOR SHARE";when(c.prepareStatement(sql)).thenReturn(q);when(q.executeQuery()).thenReturn(r);when(r.next()).thenReturn(false);UUID owner=UUID.fromString("00000000-0000-4000-8000-000000000001");assertTrue(OfficeBlueprint.load(c,owner,"emperor","term").isEmpty());verify(q).setString(1,owner.toString());verify(q).setString(2,"emperor");verify(q).setString(3,"term");}
 @Test void onlyEmperorCanUseExplicitImperialFallback(){assertEquals("Corrino",OfficeGear.kitFaction(null,"emperor"));assertEquals("Corrino",OfficeGear.kitFaction("guild","emperor"));assertEquals("Atreides",OfficeGear.kitFaction("atreides","emperor"));for(String office:List.of("house-leader:Atreides","water-steward:Atreides","spice-steward:Atreides"))assertThrows(IllegalStateException.class,()->OfficeGear.kitFaction(null,office));}
}
