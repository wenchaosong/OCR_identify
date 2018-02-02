package com.zxing.decode;

import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;
import com.zxing.android.CaptureActivity;

import java.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class DecodeThread extends Thread {

    private final CaptureActivity activity;
    private final Hashtable<DecodeHintType, Object> hints;
    private final Vector<BarcodeFormat> decodeFormats;
    private Handler handler;
    private final CountDownLatch handlerInitLatch;

    public DecodeThread(CaptureActivity activity, ResultPointCallback resultPointCallback) {

        this.activity = activity;
        handlerInitLatch = new CountDownLatch(1);

        hints = new Hashtable<>();

        decodeFormats = new Vector<BarcodeFormat>();
        decodeFormats.addAll(DecodeFormatManager.ONE_D_FORMATS);
        decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
        decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);

    }

    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException ie) {
            // continue?
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new DecodeHandler(activity, hints);
        handlerInitLatch.countDown();
        Looper.loop();
    }

}
