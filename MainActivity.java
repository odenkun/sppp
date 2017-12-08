package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.example.myapplication.voice.VoiceRepository;

import java.util.ArrayList;

enum MicMode {
    BLUETOOTH,
    BUILT_IN
}

public class MainActivity extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback, BTListener.Callback {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE = 349708;

    private static final String[] PERMISSIONS = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.RECORD_AUDIO
    };

    private VoiceRepository voiceRepo;
    private MicMode mMicMode = MicMode.BUILT_IN;
    private BTListener mBTListener;

    @Override
    protected void onCreate ( Bundle savedInstanceState ) {
        super.onCreate ( savedInstanceState );
        setContentView ( R.layout.activity_main );
    }

    @Override
    protected void onResume () {
        super.onResume ();

        String[] notGranted = getNotGrantedPermissions ();
        if ( notGranted.length > 0 ) {
            ActivityCompat.requestPermissions ( this, notGranted, REQUEST_CODE );
        } else {
            readyMic ();
        }
    }

    private void readyMic () {
        switch ( mMicMode ) {
            case BUILT_IN:
                voiceRepo = new VoiceRepository ();
                break;
            case BLUETOOTH:
                mBTListener = new BTListener ( this, this );
                break;
            default:
                Log.e ( TAG, "MicMode is not assigned." );
        }
    }

    private String[] getNotGrantedPermissions () {
        ArrayList <String> notGranted = new ArrayList <> ();
        for ( String permission : PERMISSIONS ) {
            int permissionState = ContextCompat.checkSelfPermission ( this, permission );
            if ( permissionState != PackageManager.PERMISSION_GRANTED ) {
                notGranted.add ( permission );
            }
        }
        return notGranted.toArray ( new String[] {} );
    }

    @Override
    public void onRequestPermissionsResult (
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults ) {

        if ( requestCode != REQUEST_CODE ) return;

        for ( int grantResult : grantResults ) {
            if ( grantResult == PackageManager.PERMISSION_DENIED ) {
                //許可されていないものがあるとき
                Toast.makeText ( this, "権限を許可してください。", Toast.LENGTH_LONG ).show ();
                return;
            }
        }
        //すべて許可されていたなら
        readyMic ();
    }

    @Override
    public void onBTConnected () {
        if (mMicMode == MicMode.BLUETOOTH) {
            voiceRepo = new VoiceRepository ();
        }
    }


    @Override
    protected void onPause () {
        super.onPause ();
        if ( voiceRepo != null ) {
            voiceRepo.stop ();
        }
        if ( mBTListener != null ) {
            mBTListener.stop ();
        }
    }

}