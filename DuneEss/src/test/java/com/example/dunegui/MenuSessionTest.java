package com.example.dunegui;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MenuSessionTest {
    @Test void rejectsAnotherPlayerWithoutConsumingTheOwnerAction() {
        UUID owner=UUID.randomUUID(); MenuSession session=new MenuSession(owner);
        assertFalse(session.claim(UUID.randomUUID())); assertTrue(session.claim(owner)); assertFalse(session.claim(owner));
    }
    @Test void openingANewMenuInvalidatesThePreviousConfirmation() {
        UUID owner=UUID.randomUUID(); MenuSession old=new MenuSession(owner), next=new MenuSession(owner);
        assertFalse(old.claim(owner)); assertTrue(next.claim(owner));
    }
    @Test void expiringOldMenuDoesNotInvalidateTheNewMenu() {
        UUID owner=UUID.randomUUID(); MenuSession old=new MenuSession(owner), next=new MenuSession(owner);
        old.expire(); assertTrue(next.claim(owner));
    }
    @Test void expiredMenuCannotTrade() {
        UUID owner=UUID.randomUUID(); MenuSession session=new MenuSession(owner); session.expire(); assertFalse(session.claim(owner));
    }
}
