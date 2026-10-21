package com.example.dunegui;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** One owner, one active screen, one accepted action, and a short expiry. */
public final class MenuSession {
    private static final ConcurrentHashMap<UUID,MenuSession> CURRENT=new ConcurrentHashMap<>();
    private final UUID owner;
    private final long expires;
    private final AtomicBoolean used=new AtomicBoolean();
    public MenuSession(UUID owner) { this.owner=owner; expires=System.nanoTime()+Duration.ofMinutes(2).toNanos(); CURRENT.put(owner,this); }
    public boolean claim(UUID actor) {
        return owner.equals(actor) && System.nanoTime()<expires && CURRENT.get(owner)==this
            && used.compareAndSet(false,true) && CURRENT.remove(owner,this);
    }
    public void expire() { CURRENT.remove(owner,this); }
}
