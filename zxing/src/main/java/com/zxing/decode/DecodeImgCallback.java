package com.zxing.decode;

import com.google.zxing.Result;

/**
 * 解析图片的回调
 */
public interface DecodeImgCallback {

    void onImageDecodeSuccess(Result result);

    void onImageDecodeFailed();
}
