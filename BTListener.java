package com.example.myapplication;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

public class BTListener implements BluetoothProfile.ServiceListener {
    private static final String TAG = "BTListener";
    private static final int REQUEST_ENABLE_BT = 53126;

    private BluetoothHeadset mBluetoothHeadset;
    private Callback mCallback;

    BTListener ( Callback callback, Activity activity ) {
        this.mCallback = callback;
        if (checkBTState (activity)) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter ();
            bluetoothAdapter.getProfileProxy ( activity, this, BluetoothProfile.HEADSET );
        }
    }

    private boolean checkBTState(Activity activity) {
        BluetoothManager manager = (BluetoothManager) activity.getSystemService ( Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return false;
        }
        BluetoothAdapter mBluetoothAdapter = manager.getAdapter ();
        if ( mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }
        return true;
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        if (profile != BluetoothProfile.HEADSET) {
            return;
        }
        mBluetoothHeadset = (BluetoothHeadset) proxy;
        List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
        for (BluetoothDevice device : devices) {
            if (mBluetoothHeadset.startVoiceRecognition(device)) {
                mCallback.onBTConnected();
                break;
            }
        }

    }

    @Override
    public void onServiceDisconnected(int profile) {
        Log.e(TAG,"device disconnected");
    }

    void stop() {
        mCallback = null;
        if (mBluetoothHeadset == null) {
            Log.d(TAG,"devices  connected don't exist");
            return;
        }

        List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
        if (devices == null) {
            Log.d(TAG,"devices  connected don't exist");
            return;
        }
        for (BluetoothDevice device : devices) {
            mBluetoothHeadset.stopVoiceRecognition(device);
        }
    }

    interface Callback{
        void onBTConnected();
    }
}