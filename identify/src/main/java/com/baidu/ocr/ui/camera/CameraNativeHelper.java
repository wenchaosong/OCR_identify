/*
 * Copyright (C) 2018 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.ocr.ui.camera;

import android.content.Context;

import com.baidu.idcardquality.IDcardQualityProcess;

/**
 * Created by ruanshimin on 2018/1/23.
 */

public class CameraNativeHelper {

    public interface CameraNativeInitCallback {
        /**
         * 加载本地库异常回调
         *
         * @param errorCode 错误代码
         * @param e 如果加载so异常则会有异常对象传入
         */
        void onError(int errorCode, Throwable e);
    }

    public static void init(final Context ctx, final String token, final CameraNativeInitCallback cb) {
        CameraThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                int status;
                // 加载本地so失败, 异常返回getloadSoException
                if (IDcardQualityProcess.getLoadSoException() != null) {
                    status = CameraView.NATIVE_SOLOAD_FAIL;
                    cb.onError(status, IDcardQualityProcess.getLoadSoException());
                    return;
                }
                // 授权状态
                int authStatus = IDcardQualityProcess.init(token);
                if (authStatus != 0) {
                    cb.onError(CameraView.NATIVE_AUTH_FAIL, null);
                    return;
                }

                // 加载模型状态
                int initModelStatus = IDcardQualityProcess.getInstance()
                        .idcardQualityInit(ctx.getAssets(),
                                "models");

                if (initModelStatus != 0) {
                    cb.onError(CameraView.NATIVE_INIT_FAIL, null);
                }
            }
        });
    }

    public static void release() {
        IDcardQualityProcess.getInstance().releaseModel();
    }
}
