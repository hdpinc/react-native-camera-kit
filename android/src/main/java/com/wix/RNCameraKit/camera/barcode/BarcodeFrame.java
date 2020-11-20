package com.wix.RNCameraKit.camera.barcode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.annotation.ColorInt;
import android.view.View;


import com.wix.RNCameraKit.R;

public class BarcodeFrame extends View {

    private static final int STROKE_WIDTH = 3;
    private Paint borderPaint;
    private Rect frameRect;
    private int size;
    private int width;
    private int height;
    private float scale;

    public BarcodeFrame(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        scale = context.getResources().getDisplayMetrics().density;
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(STROKE_WIDTH * scale);
        frameRect = new Rect();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        size = (int)(size * scale);

        width = getMeasuredWidth();
        height = getMeasuredHeight();

        frameRect.left = (width - size)/2;
        frameRect.right = frameRect.left + size;
        frameRect.top = (height - size)/2;
        frameRect.bottom = frameRect.top + size;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(frameRect, borderPaint);
    }


    public Rect getFrameRect() {
        return frameRect;
    }

    public void setFrameColor(@ColorInt int borderColor) {
        borderPaint.setColor(borderColor);
    }

    public void setLaserColor(@ColorInt int laserColor) { }

    public void setFrameHeight(int height) {
        size = height;
    }
}
