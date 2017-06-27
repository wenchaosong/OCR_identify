/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.ocr.ui.camera;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Control implements ICameraControl {
    @Override
    public void setDetectCallback(OnDetectPictureCallback callback) {
        // TODO 暂时只用camera
    }

    @Override
    public AtomicBoolean getAbortingScan() {
        // TODO 暂时只用camera
        return null;
    }

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int MAX_PREVIEW_SIZE = 2048;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_FOR_LOCK = 1;
    private static final int STATE_WAITING_FOR_CAPTURE = 2;
    private static final int STATE_CAPTURING = 3;
    private static final int STATE_PICTURE_TAKEN = 4;

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private int flashMode;
    private int orientation = 0;
    private int state = STATE_PREVIEW;

    private Context context;
    private OnTakePictureCallback onTakePictureCallback;
    private PermissionCallback permissionCallback;

    private String cameraId;
    private TextureView textureView;
    private Size previewSize;
    private Rect previewFrame = new Rect();

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader imageReader;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;

    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;

    private Semaphore cameraLock = new Semaphore(1);
    private int sensorOrientation;

    @Override
    public void start() {
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void stop() {
        textureView.setSurfaceTextureListener(null);
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    public void pause() {
        setFlashMode(FLASH_MODE_OFF);
    }

    @Override
    public void resume() {
        state = STATE_PREVIEW;
    }

    @Override
    public View getDisplayView() {
        return textureView;
    }

    @Override
    public Rect getPreviewFrame() {
        return previewFrame;
    }

    @Override
    public void takePicture(OnTakePictureCallback callback) {
        this.onTakePictureCallback = callback;
        // 拍照第一步，对焦
        lockFocus();
    }

    @Override
    public void setPermissionCallback(PermissionCallback callback) {
        this.permissionCallback = callback;
    }

    @Override
    public void setDisplayOrientation(@CameraView.Orientation int displayOrientation) {
        this.orientation = displayOrientation / 90;
    }

    @Override
    public void refreshPermission() {
        openCamera(textureView.getWidth(), textureView.getHeight());
    }

    @Override
    public void setFlashMode(@FlashMode int flashMode) {
        if (this.flashMode == flashMode) {
            return;
        }
        this.flashMode = flashMode;
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            updateFlashMode(flashMode, previewRequestBuilder);
            previewRequest = previewRequestBuilder.build();
            captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getFlashMode() {
        return flashMode;
    }

    public Camera2Control(Context activity) {
        this.context = activity;
        textureView = new TextureView(activity);
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                    configureTransform(width, height);
                    previewFrame.left = 0;
                    previewFrame.top = 0;
                    previewFrame.right = width;
                    previewFrame.bottom = height;
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    stop();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                }
            };

    private void openCamera(int width, int height) {
        // 6.0+的系统需要检查系统权限 。
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraId, deviceStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private final CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            cameraLock.release();
            Camera2Control.this.cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraLock.release();
            cameraDevice.close();
            Camera2Control.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraLock.release();
            cameraDevice.close();
            Camera2Control.this.cameraDevice = null;
        }
    };

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            updateFlashMode(this.flashMode, previewRequestBuilder);
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }
                            captureSession = cameraCaptureSession;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(previewRequest,
                                        captureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            // TODO
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    if (onTakePictureCallback != null) {
                        Image image = reader.acquireNextImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);

                        image.close();
                        onTakePictureCallback.onPictureTaken(bytes);
                    }
                }
            };

    private CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult result) {
                    switch (state) {
                        case STATE_PREVIEW: {
                            break;
                        }
                        case STATE_WAITING_FOR_LOCK: {
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == null) {
                                captureStillPicture();
                                return;
                            }
                            switch (afState) {
                                case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                                case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                                case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED: {
                                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                    if (aeState == null
                                            || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                        captureStillPicture();
                                    } else {
                                        runPreCaptureSequence();
                                    }
                                    break;
                                }
                                default:
                                    captureStillPicture();
                            }
                            break;
                        }
                        case STATE_WAITING_FOR_CAPTURE: {
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null
                                    || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                                    || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                                state = STATE_CAPTURING;
                            } else {
                                if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                    captureStillPicture();
                                }
                            }
                            break;
                        }
                        case STATE_CAPTURING: {
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                                captureStillPicture();
                            }
                            break;
                        }
                        default:
                            break;
                    }
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
                    process(partialResult);
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    process(result);
                }

            };

    private Size getOptimalSize(Size[] choices, int textureViewWidth,
                                int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight
                    && option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth
                        && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, sizeComparator);
        }

        for (Size option : choices) {
            if (option.getWidth() >= maxWidth && option.getHeight() >= maxHeight) {
                return option;
            }
        }

        if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, sizeComparator);
        }

        return choices[0];
    }

    private Comparator<Size> sizeComparator = new Comparator<Size>() {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    };

    private void requestCameraPermission() {
        if (permissionCallback != null) {
            permissionCallback.onRequestPermission();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                //                int preferredPictureSize = (int) (Math.max(textureView.getWidth(), textureView
                // .getHeight()) * 1.5);
                //                preferredPictureSize = Math.min(preferredPictureSize, 2560);

                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                Point screenSize = new Point();
                windowManager.getDefaultDisplay().getSize(screenSize);
                int maxImageSize = Math.max(MAX_PREVIEW_SIZE, screenSize.y * 3 / 2);

                Size size = getOptimalSize(map.getOutputSizes(ImageFormat.JPEG), textureView.getWidth(),
                        textureView.getHeight(), maxImageSize, maxImageSize, new Size(4, 3));

                imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                        ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(
                        onImageAvailableListener, backgroundHandler);

                int displayRotation = orientation;
                // noinspection ConstantConditions
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                }

                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = screenSize.x;
                int maxPreviewHeight = screenSize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = screenSize.y;
                    maxPreviewHeight = screenSize.x;
                }

                maxPreviewWidth = Math.min(maxPreviewWidth, MAX_PREVIEW_WIDTH);
                maxPreviewHeight = Math.min(maxPreviewHeight, MAX_PREVIEW_HEIGHT);

                previewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, size);
                this.cameraId = cameraId;
                return;
            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            cameraLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraLock.release();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ocr_camera");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == previewSize || null == context) {
            return;
        }
        int rotation = orientation;
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    // 拍照前，先对焦
    private void lockFocus() {
        if (captureSession != null && state == STATE_PREVIEW) {
            try {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START);
                state = STATE_WAITING_FOR_LOCK;
                captureSession.capture(previewRequestBuilder.build(), captureCallback,
                        backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void runPreCaptureSequence() {
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            state = STATE_WAITING_FOR_CAPTURE;
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 拍照session
    private void captureStillPicture() {
        try {
            if (null == context || null == cameraDevice) {
                return;
            }
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(orientation));
            updateFlashMode(this.flashMode, captureBuilder);
            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            unlockFocus();
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                    @NonNull CaptureRequest request,
                                                    @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                        }
                    };

            // 停止预览
            captureSession.stopRepeating();
            captureSession.capture(captureBuilder.build(), captureCallback, backgroundHandler);
            state = STATE_PICTURE_TAKEN;
            //            unlockFocus();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }

    // 停止对焦
    private void unlockFocus() {
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
            state = STATE_PREVIEW;
            // 预览
            captureSession.setRepeatingRequest(previewRequest, captureCallback,
                    backgroundHandler);
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updateFlashMode(@FlashMode int flashMode, CaptureRequest.Builder builder) {
        switch (flashMode) {
            case FLASH_MODE_TORCH:
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                break;
            case FLASH_MODE_OFF:
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case ICameraControl.FLASH_MODE_AUTO:
            default:
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
                break;
        }
    }
}
