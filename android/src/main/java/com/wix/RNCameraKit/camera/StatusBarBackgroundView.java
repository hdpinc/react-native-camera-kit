package com.wix.RNCameraKit.camera;

import android.graphics.PixelFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

import com.facebook.react.uimanager.ThemedReactContext;

// このクラスは、ステータスバーの背景色を指定するクラスです。
// 一部のAndroid端末で、ステータスバーの背景部分に、カメラのプレビューが表示されてしまうという問題に対処するために作られました。
// ただし、レイアウト（表示位置）を設定する処理は、タイミングの関係上このクラスに含まれておらず、CameraViewで行われています。
public class StatusBarBackgroundView extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder surfaceHolder;
    private int backgroundColor;

    public StatusBarBackgroundView(ThemedReactContext context, int backgroundColor) {
        super(context);

        this.backgroundColor = backgroundColor;

        surfaceHolder = this.getHolder();
        surfaceHolder.addCallback(this);

        // ----> これらの処理はコンストラクタのタイミングで行う必要がある。 
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        // <---- ここまで。
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        canvas.drawColor(backgroundColor);
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // 処理がない場合も定義は必要。
    }
 
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // 処理がない場合も定義は必要。
    }
}