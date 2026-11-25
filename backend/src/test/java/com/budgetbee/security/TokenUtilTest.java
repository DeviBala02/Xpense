package com.budgetbee.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenUtilTest {

    @Test
    void tokensAreLongAndUnique() {
        String a = TokenUtil.newToken();
        String b = TokenUtil.newToken();
        assertEquals(43, a.length());
        assertFalse(a.equals(b));
    }

    @Test
    void tokensUseOnlyUrlSafeCharacters() {
        assertTrue(TokenUtil.newToken().matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void sha256MatchesTheKnownTestVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenUtil.sha256Hex("abc"));
    }

    @Test
    void hashIsAlways64CharactersAndNotTheToken() {
        String token = TokenUtil.newToken();
        String hash = TokenUtil.sha256Hex(token);
        assertEquals(64, hash.length());
        assertFalse(hash.contains(token));
    }
}
