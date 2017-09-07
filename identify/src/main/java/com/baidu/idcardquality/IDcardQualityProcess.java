/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.idcardquality;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.baidu.idl.authority.AlgorithmOnMainThreadException;
import com.baidu.idl.authority.IDLAuthorityException;
import com.baidu.idl.license.License;
import com.baidu.idl.util.UIThread;

public class IDcardQualityProcess {

    private static IDcardQualityProcess mInstance;
    private static String tokenString;
    private static int mAuthorityStatus;
    private static Throwable loadNativeException = null;
    private static volatile boolean hasReleased;

    public IDcardQualityProcess() {
    }

    public static synchronized IDcardQualityProcess getInstance() {
        if (null == mInstance) {
            mInstance = new IDcardQualityProcess();
        }

        return mInstance;
    }

    public native byte[] convertRGBImage(int[] colors, int width, int height);

    public native int idcardQualityModelInit(AssetManager var1, String var2);

    public native int idcardQualityCaptchaRelease();

    public native int idcardQualityProcess(byte[] var1, int var2, int var3, boolean var4, int var5);

    public static synchronized int init(String token) throws AlgorithmOnMainThreadException, IDLAuthorityException {
        if (UIThread.isUITread()) {
            throw new AlgorithmOnMainThreadException();
        } else {
            tokenString = token;

            try {
                mAuthorityStatus = License.getInstance().init(tokenString);
            } catch (Exception var2) {
                var2.printStackTrace();
            }

            return mAuthorityStatus;
        }
    }

    public int idcardQualityInit(AssetManager assetManager, String modelPath) {
        if (mAuthorityStatus == 0) {
            hasReleased = false;
            return this.idcardQualityModelInit(assetManager, modelPath);
        } else {
            return mAuthorityStatus;
        }
    }

    public int idcardQualityRelease() {
        if (mAuthorityStatus == 0) {
            hasReleased = true;
            this.idcardQualityCaptchaRelease();
            return 0;
        } else {
            return mAuthorityStatus;
        }
    }

    public int idcardQualityDetectionImg(Bitmap img, boolean isfont) {
        if (mAuthorityStatus == 0) {
            if (hasReleased) {
                return -1;
            }
            int imgHeight = img.getHeight();
            int imgWidth = img.getWidth();
            byte[] imageData = this.getRGBImageData(img);
            return this.idcardQualityProcess(imageData, imgHeight, imgWidth, isfont, 3);
        } else {
            return mAuthorityStatus;
        }
    }

    public static Throwable getLoadSoException() {
        return loadNativeException;
    }

    public byte[] getRGBImageData(Bitmap img) {
        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();
        int[] pixels = new int[imgWidth * imgHeight];
        img.getPixels(pixels, 0, imgWidth, 0, 0, imgWidth, imgHeight);
        byte[] imageData = convertRGBImage(pixels, imgWidth, imgHeight);
        return imageData;
    }

    public void releaseModel() {
        this.idcardQualityRelease();
    }

    static {
        try {
            System.loadLibrary("idl_license");
            System.loadLibrary("idcard_quality.1.1.1");
        } catch (Throwable e) {
            loadNativeException = e;
        }
        mInstance = null;
        mAuthorityStatus = 256;
    }
}
