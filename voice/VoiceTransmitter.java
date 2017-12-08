package com.example.myapplication.voice;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

enum Header {
    RECOGNIZE("recognize");
    private final String name;
    Header(final String name) {
        this.name = name;
    }
    public String getName() {
        return this.name;
    }
}
enum WebSocketState {
    CLOSED,
    OPENED,
    RECOGNIZING
}

class VoiceTransmitter extends WebSocketListener {
    private static final String TAG = "VoiceTransmitter";
    private static final String SUB_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private WebSocket ws;
    private int sampleRate;
    private static final String WS_URL = "http://192.168.0.5";

    private WebSocketState mState = WebSocketState.CLOSED;

    VoiceTransmitter ( int sampleRate ) {
        this.sampleRate = sampleRate;

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder()
                .addHeader ( SUB_PROTOCOL_HEADER, Header.RECOGNIZE.getName () )
                .url(WS_URL)
                .build();

        ws = client.newWebSocket( request, this);

    }

    /**
     * WebSocketコネクションが確立されたとき呼ばれる
     */
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        emitLog("opened");
        ws = webSocket;
        ws.send ( "sampleRate:" + sampleRate );
        mState = WebSocketState.OPENED;
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        emitLog("Receiving : " + text);

    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        emitLog("Receiving bytes : " + bytes.hex());
    }

    /**
     *音声認識を始める
     */
    boolean startRecognize() throws NotConnectedException, SendFailedException{
        if (!isOpen ()) {
            throw new NotConnectedException("WebSocket state is not OPENED");
        }
        boolean result = this.send ( "startRecognize" );
        if (result) {
            mState = WebSocketState.RECOGNIZING;
            emitLog ( "recognizing started" );
        }else{
            emitLog ( "recognizing cannot start" );
        }
        return result;
    }
    /**
     *音声認識を終える
     */
    boolean stopRecognize() throws NotConnectedException{
        if (! isRecognizing ()){
            throw new NotConnectedException("WebSocket state is not RECOGNIZING");
        }
        this.send ( "stopRecognize" );
        Log.d(TAG,"recognizing has successfully stopped");
        mState = WebSocketState.CLOSED;
        return true;
    }

    boolean close () throws NotConnectedException{
        if (! isOpen ()){
            throw new NotConnectedException("WebSocket state is not OPENED");
        }
        //wsはonClosingでnullになるので問題ない
        return ws.close ( 1000,null );
    }

    //切断時必ず実行される
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        mState = WebSocketState.CLOSED;
        ws = null;
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        emitLog("Closing : " + code + " / " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        emitLog("Error : " + t.getMessage());
    }

    private void emitLog(final String s) {
        Log.d(TAG, s);
    }

    boolean sendVoice (final byte[] data ) throws NotConnectedException {
        if (! isRecognizing ()){
            throw new NotConnectedException("WebSocket state is not RECOGNIZING");
        }
        boolean result = mState == WebSocketState.RECOGNIZING && ws.send(ByteString.of(data));
        if (!result) {
            Log.e(TAG, "cannot enqueue audio data");
        }
        return result;
    }
    boolean send (final String message ) throws NotConnectedException {
        if (! isOpen ()){
            throw new NotConnectedException("WebSocket state is not OPENED");
        }
        boolean result = isOpen () && ws.send(message);
        if (!result) {
            Log.e(TAG, "cannot enqueue a message");
        }
        return result;
    }

    private boolean isOpen () {
        Log.d(TAG,mState.name ());
        return mState != WebSocketState.CLOSED;
    }
    private boolean isRecognizing () {
        Log.d(TAG,mState.name ());
        return mState == WebSocketState.RECOGNIZING;
    }

    class NotConnectedException extends Exception {
        public NotConnectedException(String message) {
            super(message);
        }
    }

    class SendFailedException extends Exception {
        public SendFailedException(String message) {
            super(message);
        }
    }

}