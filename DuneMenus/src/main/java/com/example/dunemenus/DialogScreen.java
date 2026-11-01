package com.example.dunemenus;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.*;

/** Illustrated overview and a single review screen. Existing owner/revision/permission gates remain authoritative. */
final class DialogScreen {
 private static final TextColor GOLD=TextColor.color(0xe9ca80), SILVER=TextColor.color(0xc7d9d4);
 private DialogScreen(){}
 static void verifyDefinition(){
  var button=ActionButton.builder(Component.text("Return",GOLD)).width(180).build();
  Objects.requireNonNull(Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text("Staff Operations",GOLD)).body(List.of(art("\ue202"))).build()).type(DialogType.multiAction(List.of(button)).columns(2).exitAction(button).build())));
 }
 static void close(Player player){player.closeDialog();}
 static void show(DuneMenus plugin,Player player,DuneMenus.View view,int detail){
  if(detail<0){overview(plugin,player,view,0);return;}
  if(!plugin.current(player,view)||detail>=view.cards().size())return;
  long revision=view.revision().incrementAndGet();
  var card=view.cards().get(detail);
  List<DialogBody> body=new ArrayList<>();
  if(card.item()!=null)body.add(DialogBody.item(card.item(),DialogBody.plainMessage(Component.text(card.label(),GOLD),300),true,true,32,32));
  else body.add(art(card.glyph()));
  body.add(DialogBody.plainMessage(Component.text(card.body(),SILVER),380));
  List<ActionButton> buttons=new ArrayList<>();
  if(card.action()!=null)buttons.add(button(plugin,player,view,revision,"Continue",card.body(),"action",detail));
  buttons.add(button(plugin,player,view,revision,"Back to overview","Return without performing this action","page",detail/6));
  display(player,card.label(),body,buttons,button(plugin,player,view,revision,"Close","Return to the game","close",0));
 }
 static void overview(DuneMenus plugin,Player player,DuneMenus.View view,int page){
  if(!plugin.current(player,view))return;
  int pages=Math.max(1,(view.cards().size()+5)/6);
  if(page<0||page>=pages)return;
  long revision=view.revision().incrementAndGet();
  var theme=MenuTheme.of(view.title());
  Component crest=Component.text(theme.glyph()+"\n\n\n\n\n\n\n",TextColor.color(0xffffff)).font(Key.key("dunerealms:imperial_menus"));
  String name=view.title().toLowerCase(Locale.ROOT);
  if(name.contains("main menu")||name.contains("imperial navigator"))
   crest=crest.clickEvent(ClickEvent.runCommand("/dunemenus ornament "+view.gate().token+" 0 "+revision));
  List<DialogBody> body=List.of(DialogBody.plainMessage(crest,380),DialogBody.plainMessage(Component.text(theme.name(),GOLD),380),DialogBody.plainMessage(Component.text("Choose a service  ·  "+(page+1)+" / "+pages,SILVER),380));
  List<ActionButton> buttons=new ArrayList<>();
  for(int i=page*6;i<Math.min(page*6+6,view.cards().size());i++){
   var card=view.cards().get(i);
   buttons.add(button(plugin,player,view,revision,card.label(),card.body(),"detail",i));
  }
  if(page>0)buttons.add(button(plugin,player,view,revision,"← Previous","Previous services","page",page-1));
  if(page+1<pages)buttons.add(button(plugin,player,view,revision,"Next →","More services","page",page+1));
  display(player,view.title(),body,buttons,button(plugin,player,view,revision,"Close","Return to the game","close",0));
 }
 private static DialogBody art(String glyph){return DialogBody.plainMessage(Component.text(glyph+"\n\n\n\n\n",TextColor.color(0xffffff)).font(Key.key("dunerealms:imperial_menus")),380);}
 private static ActionButton button(DuneMenus plugin,Player owner,DuneMenus.View view,long revision,String label,String tooltip,String operation,int index){
  return ActionButton.builder(Component.text(label,GOLD)).width(180).tooltip(Component.text(tooltip,SILVER))
   .action(DialogAction.customClick((response,audience)->{
    if(audience instanceof Player actor&&actor.getUniqueId().equals(owner.getUniqueId()))
     Bukkit.getScheduler().runTask(plugin,()->plugin.navigate(actor,view,operation,index,revision));
   },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(30)).build())).build();
 }
 private static void display(Player player,String title,List<DialogBody> body,List<ActionButton> actions,ActionButton exit){
  player.showDialog(Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text(title,GOLD)).body(body).build())
   .type(DialogType.multiAction(actions).columns(2).exitAction(exit).build())));
 }
}
