package com.example.myapplication;

import android.util.Log;
import android.webkit.WebSettings;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.internal.ws.WebSocketProtocol;
import okio.ByteString;

class VoiceTransmitter extends WebSocketListener {
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private static final String TAG = "VoiceTransmitter";
    private WebSocket ws;


    void run () {
        Interceptor interceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request req = chain.request()
                        .newBuilder()
                        .addHeader("Sec-WebSocket-Protocol", "recognize")
                        .build();
                return chain.proceed(req);
            }
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder()
                .url("ws://192.168.1.3")
                .build();

        ws = client.newWebSocket( request, this);
        client.dispatcher().executorService().shutdown();

    }




    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        output("opened");
        ws = webSocket;
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        output("Receiving : " + text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        output("Receiving bytes : " + bytes.hex());
    }

    //こちらが閉じた時も閉じられたときも実行される
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        ws = null;
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        output("Closing : " + code + " / " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        output("Error : " + t.getMessage());
    }

    void output(String s) {
        Log.d(TAG, s);
    }

    boolean send ( byte[] data ) {
        return isOpen () && ws.send(ByteString.of(data));
    }
    boolean send ( String message ) {
        return isOpen () && ws.send(message);
    }
    boolean stopRecognize() {
        return this.send ( "stopRecognize" );
    }

    private boolean isOpen () {
        return ws!=null;
    }

    boolean close () {
        return isOpen () && ws.close ( 1000, null ) ;
    }
}