package com.wix.RNCameraKit.camera;

import android.graphics.Color;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.facebook.react.uimanager.ThemedReactContext;
import com.wix.RNCameraKit.Utils;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class CameraView extends FrameLayout implements SurfaceHolder.Callback {
    private ThemedReactContext context;
    private SurfaceView surface;
    private StatusBarBackgroundView statusBarBackground;

    public CameraView(ThemedReactContext context) {
        super(context);
        this.context = context;


        surface = new SurfaceView(context);
        statusBarBackground = new StatusBarBackgroundView(context, Color.BLACK);
        setBackgroundColor(Color.BLACK);
        addView(surface, MATCH_PARENT, MATCH_PARENT);
        addView(statusBarBackground, MATCH_PARENT, MATCH_PARENT);
        surface.getHolder().addCallback(this);
        surface.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CameraViewManager.getCamera() != null) {
                    try {
                        CameraViewManager.getCamera().autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {
                            }
                        });
                    } catch (Exception e) {

                    }
                }
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actualPreviewWidth = getResources().getDisplayMetrics().widthPixels;
        int actualPreviewHeight = getResources().getDisplayMetrics().heightPixels;
        int height = Utils.convertDeviceHeightToSupportedAspectRatio(actualPreviewWidth, actualPreviewHeight);
        setSurfaceLayout(actualPreviewWidth, height, right - left, bottom - top);
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
}