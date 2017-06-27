/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.ocr.ui.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.support.v4.app.ActivityCompat;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 5.0以下相机API的封装。
 */
@SuppressWarnings("deprecation")
public class Camera1Control implements ICameraControl {

    private int displayOrientation = 0;
    private int cameraId = 0;
    private int flashMode;
    private AtomicBoolean takingPicture = new AtomicBoolean(false);
    private AtomicBoolean abortingScan = new AtomicBoolean(false);
    private Context context;
    private Camera camera;

    private Camera.Parameters parameters;
    private PermissionCallback permissionCallback;
    private Rect previewFrame = new Rect();

    private PreviewView previewView;
    private View displayView;
    private int rotation = 0;
    private OnDetectPictureCallback detectCallback;
    private int previewFrameCount = 0;
    private Camera.Size optSize;

    private final int MODEL_NOSCAN = 0;

    private final int MODEL_SCAN = 1;

    private int detectType = MODEL_NOSCAN;

    public int getCameraRotation() {
        return rotation;
    }

    public AtomicBoolean getAbortingScan() {
        return abortingScan;
    }

    @Override
    public void setDetectCallback(OnDetectPictureCallback callback) {
        detectType = MODEL_SCAN;
        detectCallback = callback;
    }

    private void onRequestDetect(byte[] data) {
        // 相机已经关闭
        if (camera == null || data == null) {
            return;
        }
        // 节流
        if (previewFrameCount++ % 5 != 0) {
            return;
        }

        YuvImage img = new YuvImage(data, ImageFormat.NV21, optSize.width, optSize.height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream(data.length);
        img.compressToJpeg(new Rect(0, 0, optSize.width, optSize.height), 80 , os);
        data = null;
        img = null;
        byte[] jpeg = os.toByteArray();
        int status = detectCallback.onDetect(jpeg, getCameraRotation());
        if (status == 0) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
        }
        jpeg = null;

    }

    @Override
    public void setDisplayOrientation(@CameraView.Orientation int displayOrientation) {
        this.displayOrientation = displayOrientation;
        switch (displayOrientation) {
            case CameraView.ORIENTATION_PORTRAIT:
                rotation = 90;
                break;
            case CameraView.ORIENTATION_HORIZONTAL:
                rotation = 0;
                break;
            case CameraView.ORIENTATION_INVERT:
                rotation = 180;
                break;
            default:
                rotation = 0;
        }
        previewView.requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshPermission() {
        startPreview(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFlashMode(@FlashMode int flashMode) {
        if (this.flashMode == flashMode) {
            return;
        }
        this.flashMode = flashMode;
        updateFlashMode(flashMode);
    }

    @Override
    public int getFlashMode() {
        return flashMode;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void pause() {
        if (camera != null) {
            camera.stopPreview();
        }
        setFlashMode(FLASH_MODE_OFF);
    }

    @Override
    public void resume() {
        takingPicture.set(false);
        if (camera == null) {
            openCamera();
        } else {
            previewView.textureView.setSurfaceTextureListener(surfaceTextureListener);
            if (previewView.textureView.isAvailable()) {
                camera.startPreview();
            }
        }
    }

    @Override
    public View getDisplayView() {
        return displayView;
    }

    @Override
    public void takePicture(final OnTakePictureCallback onTakePictureCallback) {
        if (takingPicture.get()) {
            return;
        }

        switch (displayOrientation) {
            case CameraView.ORIENTATION_PORTRAIT:
                parameters.setRotation(90);
                break;
            case CameraView.ORIENTATION_HORIZONTAL:
                parameters.setRotation(0);
                break;
            case CameraView.ORIENTATION_INVERT:
                parameters.setRotation(180);
                break;
        }
        Camera.Size picSize = getOptimalSize(camera.getParameters().getSupportedPictureSizes());
        parameters.setPictureSize(picSize.width, picSize.height);
        camera.setParameters(parameters);
        takingPicture.set(true);
        camera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                camera.cancelAutoFocus();
                try {
                    camera.takePicture(null, null, new Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            camera.startPreview();
                            takingPicture.set(false);
                            if (onTakePictureCallback != null) {
                                onTakePictureCallback.onPictureTaken(data);
                            }
                        }
                    });
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    camera.startPreview();
                    takingPicture.set(false);
                }
            }
        });
    }

    @Override
    public void setPermissionCallback(PermissionCallback callback) {
        this.permissionCallback = callback;
    }

    public Camera1Control(Context context) {
        this.context = context;
        previewView = new PreviewView(context);
        openCamera();
    }

    private void openCamera() {
        setupDisplayView();
    }

    private void setupDisplayView() {
        final TextureView textureView = new TextureView(context);
        previewView.textureView = textureView;
        previewView.setTextureView(textureView);
        displayView = previewView;
        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    private SurfaceTexture surfaceCache;

    private void setPreviewCallbackImpl() {
        if (camera != null && detectType == MODEL_SCAN) {
            camera.setPreviewCallback(previewCallback);
        }
    }

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(final byte[] data, Camera camera) {
            // 扫描成功阻止打开新线程处理
            if (abortingScan.get()) {
                return;
            }
            CameraThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    Camera1Control.this.onRequestDetect(data);
                }
            });
        }
    };


    private void initCamera() {
        try {
            if (camera == null) {
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        cameraId = i;
                    }
                }
                try {
                    camera = android.hardware.Camera.open(cameraId);
                } catch (Throwable e) {
                    e.printStackTrace();
                    startPreview(true);
                    return;
                }

            }
            if (parameters == null) {
                parameters = camera.getParameters();
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                parameters.setPreviewFormat(ImageFormat.NV21);
            }
            opPreviewSize(previewView.getWidth(), previewView.getHeight());
            camera.setPreviewTexture(surfaceCache);
            setPreviewCallbackImpl();
            startPreview(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            surfaceCache = surface;
            initCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            opPreviewSize(previewView.getWidth(), previewView.getHeight());
            setPreviewCallbackImpl();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            setPreviewCallbackImpl();
        }
    };

    // 开启预览
    private void startPreview(boolean checkPermission) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (checkPermission && permissionCallback != null) {
                permissionCallback.onRequestPermission();
            }
            return;
        }
        if (camera == null) {
            initCamera();
        } else {
            camera.startPreview();
        }
    }

    private void opPreviewSize(int width, @SuppressWarnings("unused") int height) {

        if (parameters != null && camera != null && width > 0) {
            optSize = getOptimalSize(camera.getParameters().getSupportedPreviewSizes());

            parameters.setPreviewSize(optSize.width, optSize.height);
            previewView.setRatio(1.0f * optSize.width / optSize.height);

            camera.setDisplayOrientation(getSurfaceOrientation());
            camera.stopPreview();
            try {
                camera.setParameters(parameters);
            } catch (RuntimeException e) {
                e.printStackTrace();

            }
            camera.startPreview();
        }
    }

    private Camera.Size getOptimalSize(List<Camera.Size> sizes) {
        int width = previewView.textureView.getWidth();
        int height = previewView.textureView.getHeight();

        Camera.Size pictureSize = sizes.get(0);

        List<Camera.Size> candidates = new ArrayList<>();

        for (Camera.Size size : sizes) {
            if (size.width >= width && size.height >= height && size.width * height == size.height * width) {
                // 比例相同
                candidates.add(size);
            } else if (size.height >= width && size.width >= height && size.width * width == size.height * height) {
                // 反比例
                candidates.add(size);
            }
        }
        if (!candidates.isEmpty()) {
            return Collections.min(candidates, sizeComparator);
        }

        for (Camera.Size size : sizes) {
            if (size.width > width && size.height > height) {
                return size;
            }
        }

        return pictureSize;
    }

    private Comparator<Camera.Size> sizeComparator = new Comparator<Camera.Size>() {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return Long.signum((long) lhs.width * lhs.height - (long) rhs.width * rhs.height);
        }
    };

    private void updateFlashMode(int flashMode) {
        switch (flashMode) {
            case FLASH_MODE_TORCH:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                break;
            case FLASH_MODE_OFF:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                break;
            case ICameraControl.FLASH_MODE_AUTO:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                break;
            default:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                break;
        }
        camera.setParameters(parameters);
    }

    private int getSurfaceOrientation() {
        @CameraView.Orientation
        int orientation = displayOrientation;
        switch (orientation) {
            case CameraView.ORIENTATION_PORTRAIT:
                return 90;
            case CameraView.ORIENTATION_HORIZONTAL:
                return 0;
            case CameraView.ORIENTATION_INVERT:
                return 180;
            default:
                return 90;
        }
    }

    /**
     * 有些相机匹配不到完美的比例。比如。我们的layout是4:3的。预览只有16:9
     * 的，如果直接显示图片会拉伸，变形。缩放的话，又有黑边。所以我们采取的策略
     * 是，等比例放大。这样预览的有一部分会超出屏幕。拍照后再进行裁剪处理。
     */
    private class PreviewView extends FrameLayout {

        private TextureView textureView;

        private float ratio = 0.75f;

        void setTextureView(TextureView textureView) {
            this.textureView = textureView;
            removeAllViews();
            addView(textureView);
        }

        void setRatio(float ratio) {
            this.ratio = ratio;
            requestLayout();
            relayout(getWidth(), getHeight());
        }

        public PreviewView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            relayout(w, h);
        }

        private void relayout(int w, int h) {
            int width = w;
            int height = h;
            if (w < h) {
                // 垂直模式，高度固定。
                height = (int) (width * ratio);
            } else {
                // 水平模式，宽度固定。
                width = (int) (height * ratio);
            }

            int l = (getWidth() - width) / 2;
            int t = (getHeight() - height) / 2;

            previewFrame.left = l;
            previewFrame.top = t;
            previewFrame.right = l + width;
            previewFrame.bottom = t + height;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            textureView.layout(previewFrame.left, previewFrame.top, previewFrame.right, previewFrame.bottom);
        }
    }

    @Override
    public Rect getPreviewFrame() {
        return previewFrame;
    }
}
