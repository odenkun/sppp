package com.example.myapplication;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;



public class MainActivity extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback,
        VoiceRecorder.Callback {
    private static final String TAG = "MainActivity";

    static final int REQUEST_CODE = 1;

    BluetoothHeadset mBluetoothHeadset;
    private VoiceRecorder mVoiceRecorder;

    private VoiceTransmitter transmitter;

    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset) proxy;
                List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
                if(devices.size() > 0){
                    mBluetoothHeadset.startVoiceRecognition(devices.get(0));
//                    audioTest();

                }
            }
        }
        public void onServiceDisconnected(int profile) {
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        transmitter = new VoiceTransmitter();
        audioTest();

//        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        bluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);
    }


    @Override
    protected void onResume() {
        super.onResume();
        checkPermission(Manifest.permission.BLUETOOTH);
        checkPermission(Manifest.permission.RECORD_AUDIO);
    }

    void checkPermission(String permission) {
        int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    permission
            }, REQUEST_CODE);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    // パーミッションが必要な処理
                }
            }
        }
    }

    void audioTest() {
        Log.d(TAG,"audioTest started");
        try {
            mVoiceRecorder = new VoiceRecorder(this);
            mVoiceRecorder.start();
        } catch(Exception e) {
            Log.e(TAG, "", e);
        }
    }

    @Override
    public void onVoiceStart() {

        transmitter.run ();
    }

    @Override
    public void onVoice(byte[] data, int size) {
        Log.d(TAG,String.valueOf(transmitter.send ( data )));
    }

    @Override
    public void onVoiceEnd() {
        transmitter.close ();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
        if (transmitter != null) {
            transmitter.close ();
            transmitter = null;
        }
        if (mBluetoothHeadset != null) {
            List <BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices ();
            if ( devices != null && devices.size () > 0 ) {
                mBluetoothHeadset.stopVoiceRecognition ( devices.get ( 0 ) );
            }
        }
    }

    /** 最初に見つかったIPv4ローカルアドレスを返します。
     *
     * @return
     * @throws SocketException
     */
    private static InetAddress getLocalAddress() throws SocketException {
        Enumeration<NetworkInterface> netifs = NetworkInterface.getNetworkInterfaces();
        while(netifs.hasMoreElements()) {
            NetworkInterface netif = netifs.nextElement();
            for(InterfaceAddress ifAddr : netif.getInterfaceAddresses()) {
                InetAddress a = ifAddr.getAddress();
                if(a != null && !a.isLoopbackAddress() && a instanceof Inet4Address) {
                    return a;
                }
            }
        }
        return null;
    }



}