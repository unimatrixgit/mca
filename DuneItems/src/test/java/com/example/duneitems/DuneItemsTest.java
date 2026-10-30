package com.example.duneitems;
import org.bukkit.event.block.CrafterCraftEvent;
import org.junit.jupiter.api.Test;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.*;
import org.bukkit.potion.PotionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.InventoryView;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DuneItemsTest {
 @Test void exactCanonicalMetadata(){
  PotionMeta meta=mock(PotionMeta.class);PersistentDataContainer pdc=mock(PersistentDataContainer.class);when(meta.getPersistentDataContainer()).thenReturn(pdc);
  DuneItems.decorate(meta);
  verify(meta).setBasePotionType(PotionType.WATER);verify(meta).setItemModel(DuneItems.KEY);verify(meta).setCustomModelData(13101);verify(meta).setMaxStackSize(16);
  verify(meta).displayName(Component.text("Vial of Water",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false));
  ArgumentCaptor<List<Component>> lore=ArgumentCaptor.forClass(List.class);verify(meta).lore(lore.capture());
  assertEquals(Component.text("Water is the gift of life",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,true),lore.getValue().getFirst());
  assertEquals(Component.text("Quality: Common",NamedTextColor.WHITE).decoration(TextDecoration.ITALIC,false),lore.getValue().get(1));assertEquals(3,lore.getValue().size());
  verify(pdc).set(DuneItems.KEY,PersistentDataType.BYTE,(byte)1);
 }
 @Test void smugglerDistinctMetadata(){PotionMeta meta=mock(PotionMeta.class);PersistentDataContainer pdc=mock(PersistentDataContainer.class);when(meta.getPersistentDataContainer()).thenReturn(pdc);DuneItems.decorate(meta,true);verify(meta).setItemModel(DuneItems.SMUGGLER_KEY);verify(meta).setCustomModelData(13102);verify(pdc).set(DuneItems.SMUGGLER_KEY,PersistentDataType.BYTE,(byte)1);verify(pdc).remove(DuneItems.KEY);ArgumentCaptor<List<Component>> lore=ArgumentCaptor.forClass(List.class);verify(meta).lore(lore.capture());assertEquals(Component.text("Quality: Uncommon",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false),lore.getValue().get(1));}
 @Test void qualityLabels(){assertEquals(List.of("Common","Uncommon","Rare","Very Rare","Legendary"),Arrays.stream(Quality.values()).map(Quality::label).toList());assertEquals(List.of("common","uncommon","rare","very_rare","legendary"),Arrays.stream(Quality.values()).map(Quality::id).toList());}
 @Test void everyClickFailsClosedWithoutLicense(){
  DuneItems plugin=mock(DuneItems.class,CALLS_REAL_METHODS);Player player=mock(Player.class);doReturn(null).when(plugin).authorizedResult(player);
  ShapelessRecipe recipe=mock(ShapelessRecipe.class);when(recipe.getKey()).thenReturn(DuneItems.KEY);
  for(ClickType click:ClickType.values()){
   CraftItemEvent event=mock(CraftItemEvent.class);CraftingInventory inv=mock(CraftingInventory.class);when(event.getRecipe()).thenReturn(recipe);when(event.getWhoClicked()).thenReturn(player);when(event.getInventory()).thenReturn(inv);when(event.getClick()).thenReturn(click);
   plugin.craft(event);verify(event).setCancelled(true);verify(inv).setResult(null);
  }
 }
 @Test void changedLicenseOrVariantCannotCraftStalePreview(){
  DuneItems plugin=mock(DuneItems.class,CALLS_REAL_METHODS);Player player=mock(Player.class);ItemStack expected=mock(ItemStack.class),stale=mock(ItemStack.class);doReturn(expected).when(plugin).authorizedResult(player);
  ShapelessRecipe recipe=mock(ShapelessRecipe.class);when(recipe.getKey()).thenReturn(DuneItems.KEY);CraftItemEvent event=mock(CraftItemEvent.class);CraftingInventory inv=mock(CraftingInventory.class);when(event.getRecipe()).thenReturn(recipe);when(event.getWhoClicked()).thenReturn(player);when(event.getInventory()).thenReturn(inv);when(event.getCurrentItem()).thenReturn(stale);when(stale.getAmount()).thenReturn(2);when(stale.isSimilar(expected)).thenReturn(false);
  plugin.craft(event);verify(event).setCancelled(true);verify(inv).setResult(expected);
 }
 @Test void authorizedCurrentResultLeavesVanillaCraftAndRemaindersAlone(){
  DuneItems plugin=mock(DuneItems.class,CALLS_REAL_METHODS);Player player=mock(Player.class);ItemStack expected=mock(ItemStack.class),current=mock(ItemStack.class);doReturn(expected).when(plugin).authorizedResult(player);
  ShapelessRecipe recipe=mock(ShapelessRecipe.class);when(recipe.getKey()).thenReturn(DuneItems.KEY);when(current.getAmount()).thenReturn(2);when(current.isSimilar(expected)).thenReturn(true);
  for(ClickType click:ClickType.values()){
   CraftItemEvent event=mock(CraftItemEvent.class);CraftingInventory inv=mock(CraftingInventory.class);when(event.getRecipe()).thenReturn(recipe);when(event.getWhoClicked()).thenReturn(player);when(event.getInventory()).thenReturn(inv);when(event.getCurrentItem()).thenReturn(current);when(event.getClick()).thenReturn(click);
   plugin.craft(event);verify(event,never()).setCancelled(anyBoolean());verify(inv,never()).setMatrix(any());verify(inv,never()).setResult(any());
  }
 }
 @Test void prepareUsesAuthoritativeRoleResult(){
  DuneItems plugin=mock(DuneItems.class,CALLS_REAL_METHODS);Player player=mock(Player.class);ItemStack expected=mock(ItemStack.class);doReturn(expected).when(plugin).authorizedResult(player);
  PrepareItemCraftEvent event=mock(PrepareItemCraftEvent.class);ShapelessRecipe recipe=mock(ShapelessRecipe.class);when(recipe.getKey()).thenReturn(DuneItems.KEY);when(event.getRecipe()).thenReturn(recipe);CraftingInventory inv=mock(CraftingInventory.class);when(event.getInventory()).thenReturn(inv);InventoryView view=mock(InventoryView.class);when(event.getView()).thenReturn(view);when(view.getPlayer()).thenReturn(player);
  plugin.prepare(event);verify(inv).setResult(expected);
 }
 @Test void automatedCrafterCannotBypassPlayerLicenseGate(){
  DuneItems plugin=mock(DuneItems.class,CALLS_REAL_METHODS);ShapelessRecipe vial=mock(ShapelessRecipe.class);when(vial.getKey()).thenReturn(DuneItems.KEY);CrafterCraftEvent blocked=mock(CrafterCraftEvent.class);when(blocked.getRecipe()).thenReturn(vial);plugin.automatedCraft(blocked);verify(blocked).setCancelled(true);
  ShapelessRecipe unrelated=mock(ShapelessRecipe.class);when(unrelated.getKey()).thenReturn(new org.bukkit.NamespacedKey("minecraft","cake"));CrafterCraftEvent normal=mock(CrafterCraftEvent.class);when(normal.getRecipe()).thenReturn(unrelated);plugin.automatedCraft(normal);verify(normal,never()).setCancelled(anyBoolean());
 }
 @Test void giveCountBounds(){for(String n:List.of("0","-1","1025","2147483648","bad"))assertThrows(IllegalArgumentException.class,()->DuneItems.count(n));assertEquals(1,DuneItems.count("1"));assertEquals(1024,DuneItems.count("1024"));}
 @Test void invalidStackRejectedBeforeBukkitAccess(){assertThrows(IllegalArgumentException.class,()->DuneItems.waterVial(0));assertThrows(IllegalArgumentException.class,()->DuneItems.waterVial(17));}
}
