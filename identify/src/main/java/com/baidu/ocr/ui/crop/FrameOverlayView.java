/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.ocr.ui.crop;


import com.baidu.ocr.ui.util.DimensionUtil;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class FrameOverlayView extends View {

    interface OnFrameChangeListener {
        void onFrameChange(RectF newFrame);
    }

    public Rect getFrameRect() {
        Rect rect = new Rect();
        rect.left = (int) frameRect.left;
        rect.top = (int) frameRect.top;
        rect.right = (int) frameRect.right;
        rect.bottom = (int) frameRect.bottom;
        return rect;
    }

    public FrameOverlayView(Context context) {
        super(context);
        init();
    }

    public FrameOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FrameOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private GestureDetector.SimpleOnGestureListener onGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            translate(distanceX, distanceY);
            return true;
        }

    };

    private static final int CORNER_LEFT_TOP = 1;
    private static final int CORNER_RIGHT_TOP = 2;
    private static final int CORNER_RIGHT_BOTTOM = 3;
    private static final int CORNER_LEFT_BOTTOM = 4;

    private int currentCorner = -1;
    int margin = 20;
    int cornerLength = 100;
    int cornerLineWidth = 6;

    private int maskColor = Color.argb(180, 0, 0, 0);

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GestureDetector gestureDetector;
    private RectF touchRect = new RectF();
    private RectF frameRect = new RectF();

    private OnFrameChangeListener onFrameChangeListener;

    {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);

        eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setOnFrameChangeListener(OnFrameChangeListener onFrameChangeListener) {
        this.onFrameChangeListener = onFrameChangeListener;
    }

    private void init() {
        gestureDetector = new GestureDetector(getContext(), onGestureListener);
        cornerLength = DimensionUtil.dpToPx(18);
        cornerLineWidth = DimensionUtil.dpToPx(3);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetFrameRect(w, h);
    }

    private void resetFrameRect(int w, int h) {
        if (shapeType == 1) {
            frameRect.left = (int) (w * 0.05);
            frameRect.top = (int) (h * 0.25);
        } else {
            frameRect.left = (int) (w * 0.2);
            frameRect.top = (int) (h * 0.2);
        }
        frameRect.right = w - frameRect.left;
        frameRect.bottom = h - frameRect.top;
    }

    private int shapeType = 0;

    public void setTypeWide() {
        shapeType = 1;
    }



    private void translate(float x, float y) {
        if (x > 0) {
            // moving left;
            if (frameRect.left - x < margin) {
                x = frameRect.left - margin;
            }
        } else {
            if (frameRect.right - x > getWidth() - margin) {
                x = frameRect.right - getWidth() + margin;
            }
        }

        if (y > 0) {
            if (frameRect.top - y < margin) {
                y = frameRect.top - margin;
            }
        } else {
            if (frameRect.bottom - y > getHeight() - margin) {
                y = frameRect.bottom - getHeight() + margin;
            }
        }
        frameRect.offset(-x, -y);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(maskColor);

        paint.setStrokeWidth(DimensionUtil.dpToPx(1));
        canvas.drawRect(frameRect, paint);
        canvas.drawRect(frameRect, eraser);
        drawCorners(canvas);
    }

    private void drawCorners(Canvas canvas) {
        paint.setStrokeWidth(cornerLineWidth);
        // left top
        drawLine(canvas, frameRect.left - cornerLineWidth / 2, frameRect.top, cornerLength, 0);
        drawLine(canvas, frameRect.left, frameRect.top, 0, cornerLength);

        // right top
        drawLine(canvas, frameRect.right + cornerLineWidth / 2, frameRect.top, -cornerLength, 0);
        drawLine(canvas, frameRect.right, frameRect.top, 0, cornerLength);

        // right bottom
        drawLine(canvas, frameRect.right, frameRect.bottom, 0, -cornerLength);
        drawLine(canvas, frameRect.right + cornerLineWidth / 2, frameRect.bottom, -cornerLength, 0);

        // left bottom
        drawLine(canvas, frameRect.left - cornerLineWidth / 2, frameRect.bottom, cornerLength, 0);
        drawLine(canvas, frameRect.left, frameRect.bottom, 0, -cornerLength);
    }

    private void drawLine(Canvas canvas, float x, float y, int dx, int dy) {
        canvas.drawLine(x, y, x + dx, y + dy, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = handleDown(event);
        float ex = 60;
        RectF rectExtend = new RectF(frameRect.left - ex, frameRect.top - ex,
                frameRect.right + ex, frameRect.bottom + ex);
        if (!result) {
            if (rectExtend.contains(event.getX(), event.getY())) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        }
        return result;
    }

    private boolean handleDown(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                currentCorner = -1;
                break;
            case MotionEvent.ACTION_DOWN: {
                float radius = cornerLength;
                touchRect.set(event.getX() - radius, event.getY() - radius, event.getX() + radius,
                        event.getY() + radius);
                if (touchRect.contains(frameRect.left, frameRect.top)) {
                    currentCorner = CORNER_LEFT_TOP;
                    return true;
                }

                if (touchRect.contains(frameRect.right, frameRect.top)) {
                    currentCorner = CORNER_RIGHT_TOP;
                    return true;
                }

                if (touchRect.contains(frameRect.right, frameRect.bottom)) {
                    currentCorner = CORNER_RIGHT_BOTTOM;
                    return true;
                }

                if (touchRect.contains(frameRect.left, frameRect.bottom)) {
                    currentCorner = CORNER_LEFT_BOTTOM;
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE:
                return handleScale(event);
            default:

        }
        return false;
    }

    private boolean handleScale(MotionEvent event) {
        switch (currentCorner) {
            case CORNER_LEFT_TOP:
                scaleTo(event.getX(), event.getY(), frameRect.right, frameRect.bottom);
                return true;
            case CORNER_RIGHT_TOP:
                scaleTo(frameRect.left, event.getY(), event.getX(), frameRect.bottom);
                return true;
            case CORNER_RIGHT_BOTTOM:
                scaleTo(frameRect.left, frameRect.top, event.getX(), event.getY());
                return true;
            case CORNER_LEFT_BOTTOM:
                scaleTo(event.getX(), frameRect.top, frameRect.right, event.getY());
                return true;
            default:
                return false;
        }
    }

    private void scaleTo(float left, float top, float right, float bottom) {
        if (bottom - top < getMinimumFrameHeight()) {
            top = frameRect.top;
            bottom = frameRect.bottom;
        }
        if (right - left < getMinimumFrameWidth()) {
            left = frameRect.left;
            right = frameRect.right;
        }
        left = Math.max(margin, left);
        top = Math.max(margin, top);
        right = Math.min(getWidth() - margin, right);
        bottom = Math.min(getHeight() - margin, bottom);

        frameRect.set(left, top, right, bottom);
        invalidate();
    }

    private void notifyFrameChange() {
        if (onFrameChangeListener != null) {
            onFrameChangeListener.onFrameChange(frameRect);
        }
    }

    private float getMinimumFrameWidth() {
        return 2.4f * cornerLength;
    }

    private float getMinimumFrameHeight() {
        return 2.4f * cornerLength;
    }
}
