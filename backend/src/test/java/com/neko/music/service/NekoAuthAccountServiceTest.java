package com.neko.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NekoAuthAccountServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void acceptsNumericAndDecimalStringSubject() throws Exception {
        assertEquals(2L, parse("2"));
        assertEquals(2L, parse("\"2\""));
        assertEquals(Long.MAX_VALUE, parse("\"" + Long.MAX_VALUE + "\""));
    }

    @Test
    void rejectsInvalidSubject() throws Exception {
        assertEquals(0L, parse("null"));
        assertEquals(0L, parse("0"));
        assertEquals(0L, parse("-1"));
        assertEquals(0L, parse("\"0\""));
        assertEquals(0L, parse("\"-1\""));
        assertEquals(0L, parse("\"+1\""));
        assertEquals(0L, parse("\" 1\""));
        assertEquals(0L, parse("\"1.0\""));
        assertEquals(0L, parse("true"));
        assertEquals(0L, parse("{}"));
        assertEquals(0L, parse("\"9223372036854775808\""));
    }

    private static long parse(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        return NekoAuthAccountService.subjectId(node);
    }
}
