package com.wix.RNCameraKit.camera;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.support.annotation.ColorInt;
import android.support.annotation.IntRange;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;

// import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.zxing.Result;
import com.wix.RNCameraKit.Utils;
import com.wix.RNCameraKit.camera.barcode.BarcodeScanner;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import static com.wix.RNCameraKit.camera.Orientation.getSupportedRotation;

@SuppressWarnings("MagicNumber deprecation")
// We're still using Camera API 1, everything is deprecated
public class CameraViewManager extends SimpleViewManager<CameraView> {

    private static Camera camera = null;
    private static int currentCamera = 0;
    private static String flashMode = Camera.Parameters.FLASH_MODE_AUTO;
    private static Stack<CameraView> cameraViews = new Stack<>();
    private static ThemedReactContext reactContext;
    private static OrientationEventListener orientationListener;
    private static int currentRotation = 0;
    private static AtomicBoolean cameraReleased = new AtomicBoolean(false);
    private static Camera.Size optimalPreviewSize;

    private static boolean shouldScan = false;

    private static BarcodeScanner scanner;
    private static Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(final byte[] data, final Camera camera) {
            Utils.runOnWorkerThread(new Runnable() {
                @Override
                public void run() {
                    if (scanner != null) {
                        scanner.onPreviewFrame(data, camera);
                    }
                }
            });
        }
    };

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
        if (!cameraViews.isEmpty() && cameraViews.peek() == cameraView) return;
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
        setBarcodeScanner();
    }

    private static void releaseCamera() {
        camera.setOneShotPreviewCallback(null);
        cameraReleased.set(true);
        camera.release();
    }

    private static void connectHolder() {
        if (cameraViews.isEmpty() || cameraViews.peek().getHolder() == null) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (camera == null) {
                    initCamera();
                }

                if (cameraViews.isEmpty()) {
                    return;
                }

                cameraViews.peek().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // プレビュー前にサーフェイスのサイズを設定してしまう
                            int width = getDefaultDisplaySize().x;
                            cameraViews.peek().setSurfaceLayout(
                                optimalPreviewSize.height, // 短い方
                                optimalPreviewSize.width, // 長い方
                                width,
                                width
                            );
                            camera.stopPreview();
                            camera.setPreviewDisplay(cameraViews.peek().getHolder());
                            camera.startPreview();
                            if (shouldScan) {
                                camera.setOneShotPreviewCallback(previewCallback);
                            }
                            cameraViews.peek().setSurfaceBgColor(Color.TRANSPARENT);
                            cameraViews.peek().showFrame();
                        } catch (IOException | RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }).start();
    }

    static void removeCameraView() {
        if (!cameraViews.isEmpty()) {
            cameraViews.pop();
        }
        if (!cameraViews.isEmpty()) {
            connectHolder();
        } else if (camera != null) {
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

    public static int getCurrentRotation() {
        return currentRotation;
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
        // 許容値を引き上げることで、より正しい画像サイズが得られるケースが生まれる。（Ex. Nexus 5X LG）
        final double ASPECT_TOLERANCE = 0.15;
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

            Point size = getDefaultDisplaySize();

            // Log.d( "DEBUG", String.format("**** DisplaySize: width=%5d, height=%5d, ratio=%5f", size.x, size.y, (double)size.x/size.y) );

            // Camera API 1 では、16:9よりも縦長の解像度をサポートしていないため、16:9に合わせる。
            size.y = Utils.convertDeviceHeightToSupportedAspectRatio(size.x, size.y);
            List<Camera.Size> supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
            List<Camera.Size> supportedPictureSizes = camera.getParameters().getSupportedPictureSizes();
            List<Camera.Size> validPreviewSizes = getValidPreviewSizes(supportedPreviewSizes,supportedPictureSizes);
            optimalPreviewSize = getOptimalPreviewSize(validPreviewSizes, size.x, size.y);
            Camera.Size optimalPictureSize = getOptimalPictureSize(optimalPreviewSize, supportedPictureSizes);
            if (optimalPreviewSize == null || optimalPictureSize == null) return;

            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(optimalPreviewSize.width, optimalPreviewSize.height);
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            parameters.setPictureSize(optimalPictureSize.width, optimalPictureSize.height);
            parameters.setFlashMode(flashMode);
            camera.setParameters(parameters);
        } catch (RuntimeException ignored) {
        }
    }

    public static void reconnect() {
        connectHolder();
    }

    public static Point getDefaultDisplaySize() {
        WindowManager wm = (WindowManager) reactContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        // 取得されるサイズは、解像度と同じサイズの横幅と、ヘッダーとフッターを除いた縦幅となる。
        display.getSize(size);
        return size;
    }

    public static int getRotationCount() {
        return currentRotation / 90;
    }

    public static void setBarcodeScanner() {
        scanner = new BarcodeScanner(previewCallback, new BarcodeScanner.ResultHandler() {
            @Override
            public void handleResult(Result result) {
                WritableMap event = Arguments.createMap();
                event.putString("codeStringValue", result.getText());
                if (!cameraViews.empty())
                    reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(cameraViews.peek().getId(), "onReadCode", event);
            }
        });
    }

    @Nullable
    @Override
    public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.<String, Object>builder()
                .put("onReadCode",
                        MapBuilder.of("registrationName", "onReadCode"))
                .build();
    }

    @ReactProp(name = "scanBarcode")
    public void setShouldScan(CameraView view, boolean scanBarcode) {
        shouldScan = scanBarcode;
        if (shouldScan && camera != null) {
            camera.setOneShotPreviewCallback(previewCallback);
        }
    }

    @ReactProp(name = "showFrame", defaultBoolean = false)
    public void setFrame(CameraView view, boolean show) {
        view.setShowFrame(show);
    }

    @ReactProp(name = "frameColor", defaultInt = Color.GREEN)
    public void setFrameColor(CameraView view, @ColorInt int color) {
        view.setFrameColor(color);
    }

    @ReactProp(name = "laserColor", defaultInt = Color.RED)
    public void setLaserColor(CameraView view, @ColorInt int color) {
        view.setLaserColor(color);
    }

    @ReactProp(name = "surfaceColor")
    public void setSurfaceBackground(CameraView view, @ColorInt int color) {
        view.setSurfaceBgColor(color);
    }

    public static synchronized Rect getFramingRectInPreview(int previewWidth, int previewHeight) {
        return cameraViews.peek().getFramingRectInPreview(previewWidth, previewHeight);
    }
}
