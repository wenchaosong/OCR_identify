/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.ocr.ui.camera;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ruanshimin on 2017/6/6.
 */

public class CameraThreadPool {

    private static int poolCount = Runtime.getRuntime().availableProcessors();

    private static ExecutorService fixedThreadPool = Executors.newFixedThreadPool(poolCount);

    public static void execute(Runnable runnable) {
        fixedThreadPool.execute(runnable);
    }
}
