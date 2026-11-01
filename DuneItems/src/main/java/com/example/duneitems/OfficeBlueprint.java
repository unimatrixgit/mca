package com.example.duneitems;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import org.bukkit.Color;
import java.sql.*;
import java.util.*;
/** Untrusted SQL blueprint payload: cosmetics only, never item/NBT/attributes. */
record OfficeBlueprint(String name,Color color,List<String> lore){
 static void initialize(Statement s)throws SQLException{s.executeUpdate("CREATE TABLE IF NOT EXISTS dune_office_kit_definitions (owner_uuid VARCHAR(36) NOT NULL,office_key VARCHAR(96) NOT NULL,term_id VARCHAR(190) NOT NULL,version INTEGER NOT NULL,kit_name VARCHAR(64) NOT NULL,color VARCHAR(7) NOT NULL,lore_json TEXT NOT NULL,updated_at BIGINT NOT NULL,PRIMARY KEY(owner_uuid,office_key,term_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}
 static String plain(String value,int max){
  if(value==null)throw new IllegalArgumentException("Missing blueprint text");String clean=value.strip();
  if(clean.isEmpty()||clean.codePointCount(0,clean.length())>max)throw new IllegalArgumentException("Invalid blueprint text length");
  for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<32||c==127||c=='<'||c=='>'||c=='\u00a7')throw new IllegalArgumentException("Blueprint must be plain text");}
  return clean;
 }
 static OfficeBlueprint parse(String name,String color,String json,int version){
  if(version<1||color==null||!color.matches("#[0-9A-Fa-f]{6}")||json==null||json.length()>16384)throw new IllegalArgumentException("Invalid blueprint");
  List<String> lore=new ArrayList<>();
  try(JsonReader reader=new JsonReader(new StringReader(json))){
   reader.setLenient(false);reader.beginArray();
   while(reader.hasNext()){if(lore.size()>=6||reader.peek()!=JsonToken.STRING)throw new IllegalArgumentException("Blueprint lore must be at most six plain strings");lore.add(plain(reader.nextString(),120));}
   reader.endArray();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing blueprint data");
  }catch(IOException|IllegalStateException ex){throw new IllegalArgumentException("Invalid blueprint lore",ex);}
  return new OfficeBlueprint(plain(name,64),Color.fromRGB(Integer.parseInt(color.substring(1),16)),List.copyOf(lore));
 }
 static Optional<OfficeBlueprint> load(Connection c,UUID owner,String office,String term)throws SQLException{
  try(PreparedStatement q=c.prepareStatement("SELECT version,kit_name,color,lore_json FROM dune_office_kit_definitions WHERE owner_uuid=? AND office_key=? AND term_id=? FOR SHARE")){
   q.setString(1,owner.toString());q.setString(2,office);q.setString(3,term);
   try(ResultSet r=q.executeQuery()){return r.next()?Optional.of(parse(r.getString("kit_name"),r.getString("color"),r.getString("lore_json"),r.getInt("version"))):Optional.empty();}
  }
 }
}
