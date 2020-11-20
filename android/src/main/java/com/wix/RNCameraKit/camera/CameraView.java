package com.wix.RNCameraKit.camera;

import android.graphics.Color;
import android.graphics.Rect;
import androidx.annotation.ColorInt;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.facebook.react.uimanager.ThemedReactContext;
import com.wix.RNCameraKit.Utils;
import com.wix.RNCameraKit.camera.barcode.BarcodeFrame;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class CameraView extends FrameLayout implements SurfaceHolder.Callback {
    private SurfaceView surface;

    private boolean showFrame;
    private Rect frameRect;
    private BarcodeFrame barcodeFrame;
    @ColorInt private int frameColor = Color.WHITE;
    @ColorInt private int laserColor = Color.RED;
    private int width;
    private int height;
    private int barcodeFrameHeight = 200;

    public CameraView(ThemedReactContext context) {
        super(context);
        surface = new SurfaceView(context);
        setBackgroundColor(Color.BLACK);
        addView(surface, MATCH_PARENT, MATCH_PARENT);
        surface.getHolder().addCallback(this);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actualPreviewWidth = getResources().getDisplayMetrics().widthPixels;
        int actualPreviewHeight = getResources().getDisplayMetrics().heightPixels;
        int height = Utils.convertDeviceHeightToSupportedAspectRatio(actualPreviewWidth, actualPreviewHeight);
        setSurfaceLayout(actualPreviewWidth, height, right - left, bottom - top);
        if (barcodeFrame != null) {
            ((View) barcodeFrame).layout(0, 0, actualPreviewWidth, height);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = getMeasuredWidth();
        height = getMeasuredHeight();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        CameraViewManager.setCameraView(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        CameraViewManager.setCameraView(this);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        CameraViewManager.removeCameraView();
    }


    public SurfaceHolder getHolder() {
        return surface.getHolder();
    }

    public void setSurfaceLayout(int previewWidth, int previewHeight, int layoutWidth, int layoutHeight) {
        // サーフェイスのレイアウトサイズはプレビューサイズ以下でないと余白が見えてしまう。
        // 中央を表示するよう位置を調整する。
        int left = -(previewWidth - layoutWidth)/2;
        int top = -(previewHeight - layoutHeight)/2;
        int right = left + previewWidth;
        int bottom = top + previewHeight;
        surface.layout(left, top, right, bottom);
    }

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    public void setShowFrame(boolean showFrame) {
        this.showFrame = showFrame;
    }

    public void showFrame() {
        if (showFrame) {
            barcodeFrame = new BarcodeFrame(getContext());
            barcodeFrame.setFrameColor(frameColor);
            barcodeFrame.setLaserColor(laserColor);
            barcodeFrame.setFrameHeight(barcodeFrameHeight);
            addView(barcodeFrame);
            requestLayout();
        }
    }

    public Rect getFramingRectInPreview(int previewWidth, int previewHeight) {
        if (frameRect == null) {
            frameRect = new Rect(0, 0, previewWidth, previewHeight);

            if (barcodeFrame != null) {
                float scaleX = (float) width / previewWidth;
                float scaleY = (float) height / previewHeight;
                float scale = scaleX > scaleY ? scaleX : scaleY;

                Rect framingRect = new Rect(barcodeFrame.getFrameRect());
                int scanFrameSize = (int)(framingRect.width() * (1 / scale));
                frameRect.left = (previewWidth - scanFrameSize)/2;
                frameRect.right = frameRect.left + scanFrameSize;
                frameRect.top = (previewHeight - scanFrameSize)/2;
                frameRect.bottom = frameRect.top + scanFrameSize;
            }
        }
        return frameRect;
    }

    public void setFrameColor(@ColorInt int color) {
        this.frameColor = color;
        if (barcodeFrame != null) {
            barcodeFrame.setFrameColor(color);
        }
    }

    public void setLaserColor(@ColorInt int color) {
        this.laserColor = color;
        if (barcodeFrame != null) {
            barcodeFrame.setLaserColor(laserColor);
        }
    }

    /**
     * Set background color for Surface view on the period, while camera is not loaded yet.
     * Provides opportunity for user to hide period while camera is loading
     * @param color - color of the surfaceview
     */
    public void setSurfaceBgColor(@ColorInt int color) {
        surface.setBackgroundColor(color);
    }

    public void setBarcodeFrameHeight(int height) {
        this.barcodeFrameHeight = height;
        if (barcodeFrame != null) {
            barcodeFrame.setFrameHeight(height);
        }
    }
}
