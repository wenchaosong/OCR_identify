package com.identify;

import java.util.Random;

public class YoutuSign {

    /**
     * app_sign    时效性签名
     *
     * @param appId      http://open.youtu.qq.com/上申请的业务ID
     * @param secret_id  http://open.youtu.qq.com/上申请的密钥id
     * @param secret_key http://open.youtu.qq.com/上申请的密钥key
     * @param expired    签名过期时间
     * @param userid     业务账号系统,没有可以不填
     * @param mySign     生成的签名
     * @return 0表示成功
     */
    public static int appSign(String appId, String secret_id, String secret_key, long expired, String userid, StringBuffer mySign) {

        if (empty(secret_id) || empty(secret_key)) {
            return -1;
        }

        String puserid = "";
        if (!empty(userid)) {
            if (userid.length() > 64) {
                return -2;
            }
            puserid = userid;
        }

        long now = System.currentTimeMillis() / 1000;
        int rdm = Math.abs(new Random().nextInt());
        String plain_text = "a=" + appId + "&k=" + secret_id + "&e=" + expired + "&t=" + now + "&r=" + rdm + "&u=" + puserid;

        byte[] bin = hashHmac(plain_text, secret_key);

        byte[] all = new byte[bin.length + plain_text.getBytes().length];
        System.arraycopy(bin, 0, all, 0, bin.length);
        System.arraycopy(plain_text.getBytes(), 0, all, bin.length, plain_text.getBytes().length);

        mySign.append(Base64Util.encode(all));

        return 0;

    }

    private static byte[] hashHmac(String plain_text, String accessKey) {

        try {
            return HMACSHA1.getSignature(plain_text, accessKey);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean empty(String s) {
        return s == null || s.trim().equals("") || s.trim().equals("null");
    }

}
