package com.example.dunemenus;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** One authoritative virtual view; never tied to an open inventory container. */
final class ViewGate {
 final UUID owner;final Object model;final String token;final long expires;final BooleanSupplier allowed;
 private boolean claimed;
 ViewGate(UUID owner,Object model,String token,long expires,BooleanSupplier allowed){this.owner=owner;this.model=model;this.token=token;this.expires=expires;this.allowed=allowed;}
 boolean current(UUID actor,Object exact,long now){return !claimed&&owner.equals(actor)&&model==exact&&now<expires&&allowed.getAsBoolean();}
 synchronized boolean claim(UUID actor,String supplied,long now){
  if(claimed||!owner.equals(actor)||!token.equals(supplied)||now>=expires||!allowed.getAsBoolean())return false;
  claimed=true;return true;
 }
 synchronized void cancel(){claimed=true;}
 boolean allowedAfterClaim(UUID actor,long now){return owner.equals(actor)&&now<expires&&allowed.getAsBoolean();}
}
