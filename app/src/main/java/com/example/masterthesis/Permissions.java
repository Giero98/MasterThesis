package com.example.masterthesis;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Permissions extends MainActivity{

    @SuppressLint("StaticFieldLeak")
    static Context context;

    public Permissions (Context context)
    {
        Permissions.context = context;
    }
    public static boolean checkAPI29() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean checkAPI31() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean checkAPI33() {return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;}

    //region checkPermission

    public static boolean checkBtConnect() {
        if (checkAPI31()) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        else return true;
    }
    public static boolean checkBtScan() {
        if (checkAPI31()) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        else return true;
    }
    public static boolean checkBtAdvertise() {
        if (checkAPI31()) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        }
        else return true;
    }
    public static boolean checkAccessFineLocation() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkChangeWiFiState() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkAccessWiFiState() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkInternet() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkNearbyWiFiDevices() {
        if(checkAPI33()) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }
        else return true;
    }

    public static boolean checkAccessNetworkState() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkChangeNetworkState() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    //endregion

    //region getPermission

    public static void getPermissionBtConnect() {
        if (checkAPI31()) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, Constants.REQUEST_BT_CONNECT);
        }
    }
    public static void getPermissionBtScan() {
        if (checkAPI31()) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_SCAN}, Constants.REQUEST_BT_SCAN);
        }
    }
    public static void getPermissionBtAdvertise() {
        if (checkAPI31()) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, Constants.REQUEST_BT_ADVERTISE);
        }
    }
    public static void getPermissionAccessFineLocation() {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Constants.REQUEST_ACCESS_FINE_LOCATION);
    }

    public static void getPermissionChangeWiFiState() {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CHANGE_WIFI_STATE}, Constants.REQUEST_CHANGE_WIFI_STATE);
    }

    public static void getPermissionAccessWiFiState() {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_WIFI_STATE}, Constants.REQUEST_ACCESS_WIFI_STATE);
    }

    public static void getPermissionInternet() {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.INTERNET}, Constants.REQUEST_INTERNET);
    }

    public static void getPermissionNearbyWiFiDevices() {
        if (checkAPI33()) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, Constants.REQUEST_NEARBY_WIFI_DEVICES);
        }
    }

    public static void getPermissionAccessNetworkState()
    {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_NETWORK_STATE}, Constants.REQUEST_ACCESS_NETWORK_STATE);
    }

    public static void getPermissionChangeNetworkState()
    {
        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CHANGE_NETWORK_STATE}, Constants.REQUEST_CHANGE_NETWORK_STATE);
    }

    //endregion


}
