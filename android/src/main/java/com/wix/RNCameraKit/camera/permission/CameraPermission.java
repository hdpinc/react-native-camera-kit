package com.wix.RNCameraKit.camera.permission;

import android.Manifest;
import android.app.Activity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import com.facebook.react.ReactActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.bridge.Promise;
import com.wix.RNCameraKit.SharedPrefs;

public class CameraPermission {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1002;
    private static final int PERMISSION_GRANTED = 1;
    private static final int PERMISSION_NOT_DETERMINED = -1;
    private static final int PERMISSION_DENIED = 0;

    private Promise requestAccessPromise;

    public void requestAccess(Activity activity, Promise promise) {
        if (isPermissionGranted(activity)) {
            promise.resolve(true);
            return;
        }
        requestAccessPromise = promise;
        permissionRequested(activity, Manifest.permission.CAMERA);

        // このクラスに元々用意されているコールバック関数は、ここに来るまでセットされておらず、反応しません。
        // そのため、コールバックを指定できるrequestPermissions()を使用して、許可を押した際に処理が次へ流れるようにしました。
        if (activity instanceof ReactActivity) {
            ReactActivity reactActivity = (ReactActivity)activity;
            reactActivity.requestPermissions(
                new String[]{Manifest.permission.CAMERA}, 
                CAMERA_PERMISSION_REQUEST_CODE, 
                new PermissionListener() {
                    @Override
                    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                        if (isCameraPermission(requestCode, permissions)) {
                            if (requestAccessPromise != null) {
                                requestAccessPromise.resolve(grantResults[0] == PermissionChecker.PERMISSION_GRANTED);
                                requestAccessPromise = null;
                            }
                        }
                        return false;
                    }
                });
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (isCameraPermission(requestCode, permissions)) {
            if (requestAccessPromise != null) {
                requestAccessPromise.resolve(grantResults[0] == PermissionChecker.PERMISSION_GRANTED);
                requestAccessPromise = null;
            }
        }
    }

    private boolean isCameraPermission(int requestCode, String[] permissions) {
        if (permissions.length > 0) {
            return requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
                    Manifest.permission.CAMERA.equals(permissions[0]);
        }
        return false;
    }

    public int checkAuthorizationStatus(Activity activity) {
        final int statusCode = PermissionChecker.checkCallingOrSelfPermission(activity, Manifest.permission.CAMERA);
        if (statusCode == PermissionChecker.PERMISSION_GRANTED) {
            return PERMISSION_GRANTED;
        }
        if (requestingPermissionForFirstTime(activity, Manifest.permission.CAMERA)) {
            return PERMISSION_NOT_DETERMINED;
        }
        return PERMISSION_DENIED;
    }

    private boolean requestingPermissionForFirstTime(Activity activity, String permissionName) {
        return !SharedPrefs.getBoolean(activity, permissionName);
    }

    private void permissionRequested(Activity activity, String permissionName) {
        SharedPrefs.putBoolean(activity, permissionName, true);
    }

    private boolean isPermissionGranted(Activity activity) {
        // PermissionChecker.PERMISSION_GRANTEDの値は0であり、比較対象の値と異なっていたため、正しい比較になるよう修正しました。
        return checkAuthorizationStatus(activity) == PERMISSION_GRANTED;
    }
}
