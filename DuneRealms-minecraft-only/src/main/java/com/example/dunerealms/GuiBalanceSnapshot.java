package com.example.dunerealms;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
/** Display-only snapshot: expires every server tick; transaction checks remain authoritative. */
final class GuiBalanceSnapshot {
 private static long tick=Long.MIN_VALUE;
 private static final Map<UUID,Double> values=new HashMap<>();
 static synchronized double get(UUID player,long currentTick,DoubleSupplier load){
  if(tick!=currentTick){values.clear();tick=currentTick;}
  Double value=values.get(player);
  if(value==null){value=load.getAsDouble();values.put(player,value);}
  return value;
 }
}
