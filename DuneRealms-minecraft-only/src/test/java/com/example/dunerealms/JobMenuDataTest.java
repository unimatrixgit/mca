package com.example.dunerealms;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobMenuDataTest {
    private Map<String,Object> offer(String id,Object quantity,String status){return Map.of("id",id,"type","contract","owner","Sponsor","quantity",quantity,"status",status);}
    @Test void rejectsMalformedOrNonContractOffersBeforeTheyBecomeClickActions(){
        String uuid="00000000-0000-4000-8000-000000000001";
        List<Object> offers=new ArrayList<>(List.of(offer(uuid,100,"open"),offer("name;op someone",100,"open"),offer(uuid,Double.NaN,"open"),offer(uuid,-1,"open"),offer(uuid,1.5,"open"),offer(uuid,100001,"open"),offer(uuid,100,"unknown"),Map.of("type","sell","id",uuid,"quantity",50),"unexpected"));
        var result=JobMenuData.listings(Map.of("offers",offers));assertEquals(1,result.size());assertEquals(UUID.fromString(uuid),result.getFirst().owner());assertEquals(100,result.getFirst().remaining());
    }
    @Test void availableListingsSortBeforeClaimedAndSnapshotsAreReadOnly(){
        var claimed=offer("00000000-0000-4000-8000-000000000001",50,"claimed");var open=offer("00000000-0000-4000-8000-000000000002",25,"open");
        List<Map<String,Object>> source=List.of(claimed,open);var result=JobMenuData.listings(Map.of("offers",source));assertTrue(result.getFirst().available());assertEquals("claimed",source.getFirst().get("status"));assertThrows(UnsupportedOperationException.class,()->result.clear());
    }
    @Test void shrinkingListsClampPagesAndCardsNeverOverlapFooter(){
        assertEquals(0,JobMenuData.page(999,0));assertEquals(0,JobMenuData.page(-1,100));assertEquals(1,JobMenuData.page(99,22));assertEquals(0,JobMenuData.page(1,21));
        Set<Integer> slots=new HashSet<>();for(int index=0;index<21;index++)assertTrue(slots.add(JobMenuData.cardSlot(index)));assertEquals(21,slots.size());assertTrue(slots.stream().allMatch(slot->slot>=10&&slot<=34&&slot%9!=0&&slot%9!=8));
    }
}
