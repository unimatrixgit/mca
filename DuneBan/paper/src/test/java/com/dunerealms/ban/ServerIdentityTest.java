package com.dunerealms.ban;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServerIdentityTest { @Test void everyConfiguredBackendIsAcceptedAndArbitraryScopesRejected(){for(String s:new String[]{"arrakis","kaitain","giedi_prime","caladan","sigma_draconis_iv","lobby"})assertTrue(DuneBan.validServer(s),s);for(String s:new String[]{"","network","proxy","CALADAN","sigma-draconis-iv","../caladan"})assertFalse(DuneBan.validServer(s),s);assertFalse(DuneBan.validServer(null));}}
