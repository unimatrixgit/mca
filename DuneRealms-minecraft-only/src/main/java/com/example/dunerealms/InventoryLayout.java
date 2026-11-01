package com.example.dunerealms;
import java.util.Arrays;
/** 1.21.8 appends BODY/SADDLE slots after the legacy 41 player slots. Never truncate. */
public final class InventoryLayout {
 private InventoryLayout(){}
 public static <T> T[] forCurrent(T[] saved,int currentSlots){
  return saved!=null&&saved.length==41&&currentSlots==43?Arrays.copyOf(saved,currentSlots):saved;
 }
}
