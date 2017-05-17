package com.identify;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HMACSHA1 {

    private static final String HMAC_SHA1 = "HmacSHA1";

    public static byte[] getSignature(String data, String key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA1);
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(),
                mac.getAlgorithm());
        mac.init(signingKey);

        return mac.doFinal(data.getBytes());
    }
}
