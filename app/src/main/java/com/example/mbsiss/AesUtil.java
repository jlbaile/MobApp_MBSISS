package com.example.mbsiss;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AesUtil — solves InfinityFree/ByetHost anti-bot cookie challenge.
 *
 * The host sends this JS challenge:
 *   var a = toNumbers("f655ba9d09a112d4968c63579db590b4")  // key (constant)
 *   var b = toNumbers("98344c2eee86c3994890592585b49f80")  // iv  (constant)
 *   var c = toNumbers("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")  // ciphertext (changes per request)
 *   document.cookie = "__test=" + toHex(slowAES.decrypt(c, 2, a, b))
 *
 * slowAES.decrypt(c, 2, a, b) = AES-128-CBC decrypt(ciphertext=c, key=a, iv=b)
 * We replicate this in Java so Android can compute the correct cookie value.
 */
public class AesUtil {

    /**
     * Decrypts the AES-128-CBC challenge and returns the hex cookie value.
     * @param cipherHex  the hex string from variable 'c' in the challenge script
     * @return           hex string to set as __test cookie, or null on failure
     */
    public static String decryptChallenge(String cipherHex) {
        try {
            // Fixed key and IV — these are constant across all InfinityFree accounts
            byte[] key    = hexToBytes("f655ba9d09a112d4968c63579db590b4");
            byte[] iv     = hexToBytes("98344c2eee86c3994890592585b49f80");
            byte[] cipher = hexToBytes(cipherHex);

            Cipher aes = Cipher.getInstance("AES/CBC/NoPadding");
            aes.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));

            byte[] decrypted = aes.doFinal(cipher);
            return bytesToHex(decrypted);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts the ciphertext hex string (variable 'c') from the challenge HTML.
     * Looks for the pattern: toNumbers("XXXXXXXX") assigned to variable c
     */
    public static String extractCipherHex(String html) {
        // Pattern: ,c=toNumbers("HEX_STRING")
        String marker = ",c=toNumbers(\"";
        int start = html.indexOf(marker);
        if (start == -1) return null;
        start += marker.length();
        int end = html.indexOf("\"", start);
        if (end == -1) return null;
        return html.substring(start, end);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}