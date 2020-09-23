package com.wix.RNCameraKit.gallery.permission;

import android.Manifest;
import android.app.Activity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import com.facebook.react.ReactActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.bridge.Promise;
import com.wix.RNCameraKit.SharedPrefs;

public class StoragePermission {
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1001;
    private static final int PERMISSION_GRANTED = 1;
    private static final int PERMISSION_NOT_DETERMINED = -1;
    private static final int PERMISSION_DENIED = 0;
    private Promise requestAccessPromise;

    public void requestAccess(Activity activity, Promise promise) {
        if (isReadWritePermissionsGranted(activity)) {
            promise.resolve(true);
            return;
        }
        requestAccessPromise = promise;
        permissionRequested(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        permissionRequested(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // このクラスに元々用意されているコールバック関数は、ここに来るまでセットされておらず、反応しません。
        // そのため、コールバックを指定できるrequestPermissions()を使用して、許可を押した際に処理が次へ流れるようにしました。
        if (activity instanceof ReactActivity) {
            ReactActivity reactActivity = (ReactActivity)activity;
            reactActivity.requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_REQUEST_CODE, 
                new PermissionListener() {
                    @Override
                    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                        if (isStoragePermission(requestCode, permissions)) {
                            if (requestAccessPromise != null) {
                                requestAccessPromise.resolve(grantResults[0] == PermissionChecker.PERMISSION_GRANTED && grantResults[1] == PermissionChecker.PERMISSION_GRANTED);
                                requestAccessPromise = null;
                            }
                        }
                        return false;
                    }
                });
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (isStoragePermission(requestCode, permissions)) {
            if (requestAccessPromise != null) {
                requestAccessPromise.resolve(grantResults[0] == PermissionChecker.PERMISSION_GRANTED && grantResults[1] == PermissionChecker.PERMISSION_GRANTED);
                requestAccessPromise = null;
            }
        }
    }

    private boolean isStoragePermission(int requestCode, String[] permissions) {
        return requestCode == STORAGE_PERMISSION_REQUEST_CODE &&
               Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[0]) &&
               Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[1]);
    }

    public int checkAuthorizationStatus(Activity activity) {
        final int readStatus = checkPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        final int writeStatus = checkPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (readStatus == PERMISSION_GRANTED && writeStatus == PERMISSION_GRANTED) {
            return PERMISSION_GRANTED;
        }
        if (readStatus == PERMISSION_NOT_DETERMINED && writeStatus == PERMISSION_NOT_DETERMINED) {
            return PERMISSION_NOT_DETERMINED;
        }
        return PERMISSION_DENIED;
    }

    private int checkPermission(Activity activity, String permissionName) {
        final int statusCode = PermissionChecker.checkCallingOrSelfPermission(activity, permissionName);
        if (statusCode == PermissionChecker.PERMISSION_GRANTED) {
            return PERMISSION_GRANTED;
        }
        if (requestingPermissionForFirstTime(activity, permissionName)) {
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

    private boolean isReadWritePermissionsGranted(Activity activity) {
        return checkAuthorizationStatus(activity) == PERMISSION_GRANTED;
    }
}
