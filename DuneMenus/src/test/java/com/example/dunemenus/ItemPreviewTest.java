package com.example.dunemenus;
import org.junit.jupiter.api.Test;
import org.bukkit.inventory.ItemStack;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ItemPreviewTest {
 @Test void previewIsDetachedFromAuthoritativeInventory(){
  ItemStack original=mock(ItemStack.class),snapshot=mock(ItemStack.class),returned=mock(ItemStack.class);
  when(original.clone()).thenReturn(snapshot);when(snapshot.clone()).thenReturn(returned);
  Runnable action=()->{};
  var card=new DuneMenus.Card("Water vial","Price: 100 DM",action,null,"",original);
  assertSame(returned,card.item());assertSame(action,card.action());
  verify(original).clone();verify(snapshot).clone();verifyNoMoreInteractions(original);
 }
 @Test void textOnlyChoicesRemainCompatible(){assertNull(new DuneMenus.Card("Back","",null,null,"").item());}
}
