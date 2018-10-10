package com.wix.RNCameraKit.camera;

import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.ColorInt;
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
    private StatusBarBackgroundView statusBarBackground;

    private boolean showFrame;
    private Rect frameRect;
    private BarcodeFrame barcodeFrame;
    @ColorInt private int frameColor = Color.GREEN;
    @ColorInt private int laserColor = Color.RED;

    public CameraView(ThemedReactContext context) {
        super(context);
        surface = new SurfaceView(context);
        statusBarBackground = new StatusBarBackgroundView(context, Color.BLACK);
        setBackgroundColor(Color.BLACK);
        addView(surface, MATCH_PARENT, MATCH_PARENT);
        addView(statusBarBackground, MATCH_PARENT, MATCH_PARENT);
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

        // レイアウトの起点はカメラプレビューの左上なので、そこからナビゲーションバーとステータスバーの高さ分、上にずらしたものが、背景レイアウトの起点となります。
        float density = getResources().getDisplayMetrics().density;
        int navigationBarHeight = Math.round(50f * density); // 現在のナビゲーションバーのサイズは物理ピクセルで50固定。
        int statusBarHeight = Math.round(25f * density); // mdpi（等倍）の時のステータスバーの高さは25pxまたは24pxとなりますが、この場合は大きい方が問題が起きにくいので、そちらを採用します。
        statusBarBackground.layout(0, -statusBarHeight-navigationBarHeight, layoutWidth, -navigationBarHeight);
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
            addView(barcodeFrame);
            requestLayout();
        }
    }

    public Rect getFramingRectInPreview(int previewWidth, int previewHeight) {
        if (frameRect == null) {
            if (barcodeFrame != null) {
                Rect framingRect = new Rect(barcodeFrame.getFrameRect());
                int frameWidth = barcodeFrame.getWidth();
                int frameHeight = barcodeFrame.getHeight();

                if (previewWidth < frameWidth) {
                    framingRect.left = framingRect.left * previewWidth / frameWidth;
                    framingRect.right = framingRect.right * previewWidth / frameWidth;
                }
                if (previewHeight < frameHeight) {
                    framingRect.top = framingRect.top * previewHeight / frameHeight;
                    framingRect.bottom = framingRect.bottom * previewHeight / frameHeight;
                }

                frameRect = framingRect;
            } else {
                frameRect = new Rect(0, 0, previewWidth, previewHeight);
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
}
