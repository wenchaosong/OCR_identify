package com.identify;

import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Author Administrator
 * on 2017/5/17.
 */
public class Youtu {

    private final static String API_YOUTU_END_POINT = "http://api.youtu.qq.com/youtu/";
    private String m_appid;
    private String m_secret_id;
    private String m_secret_key;
    private String m_end_point;

    private static Youtu mYoutu;

    public static void initSDK(String appid, String secret_id, String secret_key) {
        mYoutu = new Youtu(appid, secret_id, secret_key, API_YOUTU_END_POINT);
    }

    public static Youtu getInstance() {
        return mYoutu;
    }

    /**
     * PicCloud 构造方法
     *
     * @param appid      授权appid
     * @param secret_id  授权secret_id
     * @param secret_key 授权secret_key
     */
    public Youtu(String appid, String secret_id, String secret_key, String end_point) {
        m_appid = appid;
        m_secret_id = secret_id;
        m_secret_key = secret_key;
        m_end_point = end_point;
    }

    /**
     * bitmap转为base64
     */
    public static String bitmapToBase64(Bitmap bitmap) {

        String result = null;
        ByteArrayOutputStream baos = null;
        try {
            if (bitmap != null) {
                baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

                baos.flush();
                baos.close();

                byte[] bitmapBytes = baos.toByteArray();
                result = Base64.encodeToString(bitmapBytes, Base64.DEFAULT);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.flush();
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private JSONObject SendHttpRequest(JSONObject postData, String mothod)
            throws IOException, JSONException, KeyManagementException, NoSuchAlgorithmException {

        StringBuffer mySign = new StringBuffer();
        YoutuSign.appSign(m_appid, m_secret_id, m_secret_key,
                System.currentTimeMillis() / 1000 + 2592000,
                "", mySign);

        System.setProperty("sun.net.client.defaultConnectTimeout", "5000");
        System.setProperty("sun.net.client.defaultReadTimeout", "5000");
        URL url = new URL(m_end_point + mothod);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // set header
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Host", "api.youtu.qq.com");
        connection.setRequestProperty("Authorization", mySign.toString());
        connection.setRequestProperty("Content-Type", "text/json");

        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.connect();

        // POST请求
        DataOutputStream out = new DataOutputStream(connection.getOutputStream());

        postData.put("app_id", m_appid);
        out.write(postData.toString().getBytes("utf-8"));
        out.flush();
        out.close();
        // 读取响应
        InputStream isss = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(isss));
        String lines;
        StringBuilder resposeBuffer = new StringBuilder("");
        while ((lines = reader.readLine()) != null) {
            lines = new String(lines.getBytes(), "utf-8");
            resposeBuffer.append(lines);
        }
        reader.close();
        // 断开连接
        connection.disconnect();

        return new JSONObject(resposeBuffer.toString());

    }

    /***
     * @param cardType 0正面，1反面
     */
    public JSONObject IdcardOcr(Bitmap bitmap, int cardType) throws JSONException, NoSuchAlgorithmException, IOException, KeyManagementException {
        JSONObject data = new JSONObject();
        String imageData = bitmapToBase64(bitmap);
        data.put("image", imageData);
        data.put("card_type", cardType);

        return SendHttpRequest(data, "ocrapi/idcardocr");
    }
}
