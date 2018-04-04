package com.wix.RNCameraKit.camera;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.support.annotation.IntRange;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;

// import android.util.Log;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.wix.RNCameraKit.Utils;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.wix.RNCameraKit.camera.Orientation.getSupportedRotation;

@SuppressWarnings("MagicNumber deprecation") // We're still using Camera API 1, everything is deprecated
public class CameraViewManager extends SimpleViewManager<CameraView> {

    private static Camera camera = null;
    private static int currentCamera = 0;
    private static String flashMode = Camera.Parameters.FLASH_MODE_AUTO;
    private static Stack<CameraView> cameraViews = new Stack<>();
    private static ThemedReactContext reactContext;
    private static OrientationEventListener orientationListener;
    private static int currentRotation = 0;
    private static AtomicBoolean cameraReleased = new AtomicBoolean(false);

    public static Camera getCamera() {
        return camera;
    }

    @Override
    public String getName() {
        return "CameraView";
    }

    @Override
    protected CameraView createViewInstance(ThemedReactContext reactContext) {
        CameraViewManager.reactContext = reactContext;
        return new CameraView(reactContext);
    }

    static void setCameraView(CameraView cameraView) {
        if(!cameraViews.isEmpty() && cameraViews.peek() == cameraView) return;
        CameraViewManager.cameraViews.push(cameraView);
        connectHolder();
        createOrientationListener();
    }

    private static void createOrientationListener() {
        if (orientationListener != null) return;
        orientationListener = new OrientationEventListener(reactContext, SensorManager.SENSOR_DELAY_NORMAL) {
             @Override
             public void onOrientationChanged(@IntRange(from = -1, to = 359) int angle) {
                 if (angle == OrientationEventListener.ORIENTATION_UNKNOWN) return;
                 setCameraRotation(359 - angle, false);
             }
         };
         orientationListener.enable();
    }

    static boolean setFlashMode(String mode) {
        if (camera == null) {
            return false;
        }
        Camera.Parameters parameters = camera.getParameters();
        List supportedModes = parameters.getSupportedFlashModes();
        if (supportedModes != null && supportedModes.contains(mode)) {
            flashMode = mode;
            parameters.setFlashMode(flashMode);
            camera.setParameters(parameters);
            camera.startPreview();
            return true;
        } else {
            return false;
        }
    }

    static boolean changeCamera() {
        if (Camera.getNumberOfCameras() == 1) {
            return false;
        }
        currentCamera++;
        currentCamera = currentCamera % Camera.getNumberOfCameras();
        initCamera();
        connectHolder();

        return true;
    }

    private static void initCamera() {
        if (camera != null) {
            releaseCamera();
        }
        try {
            camera = Camera.open(currentCamera);
            updateCameraSize();
            cameraReleased.set(false);
            setCameraRotation(currentRotation, true);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private static void releaseCamera() {
        cameraReleased.set(true);
        camera.release();
    }

    private static void connectHolder() {
        if (cameraViews.isEmpty()  || cameraViews.peek().getHolder() == null) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                if(camera == null) {
                    initCamera();
                }

                if(cameraViews.isEmpty()) {
                    return;
                }

                cameraViews.peek().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            camera.stopPreview();
                            camera.setPreviewDisplay(cameraViews.peek().getHolder());
                            camera.startPreview();
                        } catch (IOException | RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }).start();
    }

    static void removeCameraView() {
        if(!cameraViews.isEmpty()) {
            cameraViews.pop();
        }
        if(!cameraViews.isEmpty()) {
            connectHolder();
        } else if(camera != null){
            releaseCamera();
            camera = null;
        }
        if (cameraViews.isEmpty()) {
            clearOrientationListener();
        }
    }

    private static void clearOrientationListener() {
        if (orientationListener != null) {
            orientationListener.disable();
            orientationListener = null;
        }
    }

    private static void setCameraRotation(int rotation, boolean force) {
        if (camera == null) return;
        int supportedRotation = getSupportedRotation(rotation);
        if (supportedRotation == currentRotation && !force) return;
        currentRotation = supportedRotation;

        if (cameraReleased.get()) return;
        Camera.Parameters parameters = camera.getParameters();
        parameters.setRotation(supportedRotation);
        parameters.setPictureFormat(PixelFormat.JPEG);
        camera.setDisplayOrientation(Orientation.getDeviceOrientation(reactContext.getCurrentActivity()));
        camera.setParameters(parameters);
    }

    public static Camera.CameraInfo getCameraInfo() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(currentCamera, info);
        return info;
    }

    // 簡易的な計算機イプシロンの比較関数を追加
    private final static double EPSILON = 0.00001;
    private static boolean nearlyEquals(double a, double b) {
        return a == b || Math.abs(a - b) < EPSILON;
    }

    private static List<Camera.Size> getValidPreviewSizes(List<Camera.Size> previewSizes, List<Camera.Size> pictureSizes) {
        if (previewSizes == null) return null;
        if (pictureSizes == null) return null;

        List<Camera.Size> validPreviewSizes = new ArrayList<Camera.Size>();
        for (Camera.Size previewSize : previewSizes) {
            double previewRatio = (double)previewSize.width/previewSize.height;

            // Log.d( "DEBUG", String.format(".... OriginalPreviewSize: width=%5d, height=%5d, ratio=%5f", previewSize.width, previewSize.height, (double)previewSize.height/previewSize.width) );
           
            for (Camera.Size pictureSize : pictureSizes) {
                double pictureRatio = (double)pictureSize.width/pictureSize.height;
                // 同じ比率のものが双方に存在する場合のみ、有効なPreviewSizeとする。
                if (nearlyEquals(previewRatio, pictureRatio)) {

                    // 特定の機種の特定の解像度は、比率があっていてもずれてしまうケースがあるため、ここで個別に弾く。
                    // TODO:タスク980

                    validPreviewSizes.add(previewSize);
                    break;
                }
            }
        }
        return validPreviewSizes;
    }

    private static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        if (sizes == null) return null;
        final double ASPECT_TOLERANCE = 0.1;
        // 縦長を想定した比較対象の比率を取得する。
        double targetRatio=(double)h / w;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
            // PreviewSizeは、widthの方が大きい状態で入力されているため、比較対象と合わせるためには下記のようになる。
            double ratio = (double) size.width / size.height;

            // Log.d( "DEBUG", String.format(".... ValidPreviewSize: width=%5d, height=%5d, ratio=%5f, diff=%5f", size.width, size.height, (double)size.height/size.width, Math.abs(ratio - targetRatio)) );

            // 比率差が許容値よりも大きなものはスキップする。
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            // PreviewSizeの小さな方と、描画サイズの大きな方の差分を取り、一番近いサイズを取得する。
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }
        // 許容値によりすべてのサイズが合わなかった場合
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            double minRatio = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                double ratio = (double) size.width / size.height;
                // 比率の差が同じか小さい場合のみ処理を行う。
                double diff = Math.abs(ratio - targetRatio);
                if (diff < minRatio || nearlyEquals(diff, minRatio)) {
                    minRatio = diff;
                    // サイズがより近いものを選択する。
                    if (Math.abs(size.height - h) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - h);
                    }
                }
            }
        }

        // Log.d( "DEBUG", String.format("**** OptimalSize: width=%5d, height=%5d, ratio=%5f", optimalSize.width, optimalSize.height, (double)optimalSize.height/optimalSize.width) );

        return optimalSize;
    }

    private static Camera.Size getOptimalPictureSize(Camera.Size previewSize, List<Camera.Size> pictureSizes) {
        if (previewSize == null) return null;
        if (pictureSizes == null) return null;
       
        Camera.Size optimalSize = null;
        double previewRatio = (double)previewSize.width/previewSize.height;
        double minDiff = Double.MAX_VALUE;
        for (Camera.Size pictureSize : pictureSizes) {
            double pictureRatio = (double)pictureSize.width/pictureSize.height;

            // Log.d( "DEBUG", String.format(".... OriginalPictureSize: width=%5d, height=%5d, ratio=%5f", pictureSize.width, pictureSize.height, (double)pictureSize.height/pictureSize.width) );

            // 同じ比率のものしか認めない。
            if (nearlyEquals(previewRatio, pictureRatio)) {
                // サイズがより近いものを選択する。
                if (Math.abs(previewSize.height - pictureSize.height) < minDiff) {
                    optimalSize = pictureSize;
                    minDiff = Math.abs(previewSize.height - pictureSize.height);
                }
            }
        }

        // Log.d( "DEBUG", String.format("**** OptimalSize: width=%5d, height=%5d, ratio=%5f", optimalSize.width, optimalSize.height, (double)optimalSize.height/optimalSize.width) );

        return optimalSize;
    }

    private static void updateCameraSize() {
        try {
            Camera camera = CameraViewManager.getCamera();
            if (camera == null) return;

            WindowManager wm = (WindowManager) reactContext.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            // 取得されるサイズは、解像度と同じサイズの横幅と、ヘッダーとフッターを除いた縦幅となる。
            display.getSize(size);

            // Log.d( "DEBUG", String.format("**** DisplaySize: width=%5d, height=%5d, ratio=%5f", size.x, size.y, (double)size.x/size.y) );

            // Camera API 1 では、16:9よりも縦長の解像度をサポートしていないため、16:9に合わせる。
            size.y = Utils.convertDeviceHeightToSupportedAspectRatio(size.x, size.y);
            List<Camera.Size> supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
            List<Camera.Size> supportedPictureSizes = camera.getParameters().getSupportedPictureSizes();
            List<Camera.Size> validPreviewSizes = getValidPreviewSizes(supportedPreviewSizes,supportedPictureSizes);
            Camera.Size optimalPreviewSize = getOptimalPreviewSize(validPreviewSizes, size.x, size.y);
            Camera.Size optimalPictureSize = getOptimalPictureSize(optimalPreviewSize, supportedPictureSizes);
            if (optimalPreviewSize == null || optimalPictureSize == null) return;

            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(optimalPreviewSize.width, optimalPreviewSize.height);
            parameters.setPictureSize(optimalPictureSize.width, optimalPictureSize.height);
            parameters.setFlashMode(flashMode);
            camera.setParameters(parameters);
        } catch (RuntimeException ignored) {}
    }

    public static void reconnect() {
        connectHolder();
    }
}
