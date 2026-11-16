package com.statarchive.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class BridgeOriginTest {
    @Test public void exactHttpsOriginOnly() {
        assertTrue(BridgeOrigin.isTrusted("https://stat-archive.lustats.workers.dev/"));
        assertTrue(BridgeOrigin.isTrusted("https://stat-archive.lustats.workers.dev:443/path?q=1"));
        for (String url : new String[]{null, "", "http://stat-archive.lustats.workers.dev/",
                "https://stat-archive.lustats.workers.dev:444/", "https://child.stat-archive.lustats.workers.dev/",
                "https://stat-archive.lustats.workers.dev.evil.test/", "https://user@stat-archive.lustats.workers.dev/",
                "file:///stat-archive.lustats.workers.dev", "javascript:alert(1)", "not a URL"}) {
            assertFalse(url, BridgeOrigin.isTrusted(url));
        }
    }
}
