package com.statarchive.app;

import java.net.URI;

/** Exact origin policy, shared by navigation and native message dispatch. */
final class BridgeOrigin {
    static boolean isTrusted(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "stat-archive.lustats.workers.dev".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawUserInfo() == null;
        } catch (Exception invalid) { return false; }
    }
}
