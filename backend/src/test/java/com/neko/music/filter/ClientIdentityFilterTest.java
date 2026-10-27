package com.neko.music.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIdentityFilterTest {

    @Test
    void accessLogSseIsExemptBecauseEventSourceCannotSendIdentityHeaders() {
        assertFalse(ClientIdentityFilter.isProtected("/api/admin/logs/access/stream", "GET"));
        assertFalse(ClientIdentityFilter.isProtected("/api/admin/logs/access/stream/", "GET"));
    }

    @Test
    void regularAdminLogQueriesStillRequireClientIdentity() {
        assertTrue(ClientIdentityFilter.isProtected("/api/admin/logs/access", "GET"));
        assertTrue(ClientIdentityFilter.isProtected("/api/admin/logs/access/stats", "GET"));
    }
}
