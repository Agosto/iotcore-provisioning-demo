package com.agosto.iotcoreprovisioning.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;

/**
 * utility class for handling permissions.
 */

public class PermissionHandler implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "PermissionHandler";
    private static final int PERMISSION_REQUEST = 1974;

    private Callback mCallback;
    private String[] mPermissions;

    public PermissionHandler() {

    }

    public interface Callback {
        void onPermission(boolean grantedNow);
    }

    public void run(@NonNull Activity activity, String[] permissions, @StringRes int rationale, @NonNull PermissionHandler.Callback callback) {
        this.mCallback = callback;
        String permission = permissions[0];
        this.mPermissions = permissions;
        Log.d(TAG, "Checking for " + permission + " permission");
        if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                rationaleDialog(activity,rationale);
            } else {
                Log.d(TAG, "Requesting for " + permission + " permission");
                ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQUEST);
            }
        } else {
            Log.d(TAG, "Already have " + permission + " permission");
            callback.onPermission(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        String permission = permissions[0];
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, permission +  " permission has now been granted.");
                mCallback.onPermission(true);
            } else {
                Log.d(TAG, permission + " permission was NOT granted.");
            }
        }
    }

    private void rationaleDialog(@NonNull final Activity activity, @StringRes int rationale) {
        String permission = mPermissions[0];
        Log.d(TAG, "Showing rationale for " + permission + " permission");

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(rationale)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ActivityCompat.requestPermissions(activity, mPermissions, PERMISSION_REQUEST);
                    }
                })
                .setNegativeButton("No", null).show();

    }

}
