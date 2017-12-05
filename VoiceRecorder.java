package com.example.myapplication;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import okio.ByteString;


/**
 * 継続的に録音し、音声が入力されたらコールバックに通知
 * オーディオフォーマットはPCM16でチャンネルはモノラル
 * サンプリング周波数は使用できる最高の周波数を動的に決定する
 */
public class VoiceRecorder {

    //周波数の候補
    private static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000,44100,22050, 11025};

    private static final String TAG = "AudioRecorder";

    //モノラル
    static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    //PCM16
    static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    //喋っているとみなす最小の音量
    private static final int AMPLITUDE_THRESHOLD = 3000;
    //喋るのが終わったとみなす時間
    private static final int SPEECH_TIMEOUT_MILLIS = 2000;
    //喋る時間の最大の長さ
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;

    public interface Callback {

        void onVoiceStart();

        /**
         * 音声が入力されている間呼ばれる
         *
         * @param data PCM16の音声データ
         * @param size dataの実サイズ
         */
        void onVoice(byte[] data, int size);

        void onVoiceEnd();
    }

    //通知先
    private final Callback mCallback;

    private AudioRecord mAudioRecord;

    //録音された音声データの処理を行うスレッド
    private Thread mThread;

    //音声のバッファ
    private byte[] mBuffer;
    private int bufferLen = 0;

    //同期実行で使用
    private final Object mLock = new Object();

    //最後に音声が入力された時点でのタイムスタンプ
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;

    //現在の音声入力が始まった時点のタイムスタンプ
    private long mVoiceStartedMillis;

    public VoiceRecorder(@NonNull Callback callback) {
        mCallback = callback;
    }

    /**
     * 録音を始める
     * これを実行したらあとでstop()を実行しなければならない
     */
    public void start() {
        //現在の録音を停止する
        stop();
        //新しく録音のセッションを生成する
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            throw new RuntimeException("Cannot instantiate VoiceRecorder");
        }
        //録音を始める
        mAudioRecord.startRecording();
        //録音されたデータを処理するスレッドの生成
        mThread = new Thread(new ProcessVoice());
        mThread.start();
    }

    public void stop() {
        synchronized (mLock) {
            if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                mLastVoiceHeardMillis = Long.MAX_VALUE;
                mCallback.onVoiceEnd();
            }
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
            if (mAudioRecord != null) {
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }
            mBuffer = null;
        }
    }

    /**
     * 現在使用されているサンプリング周波数を取得
     */
    public int getSampleRate() {
        if (mAudioRecord != null) {
            return mAudioRecord.getSampleRate();
        }
        return 0;
    }
    public int getBufferSize() {
        if (mBuffer != null) {
            return mBuffer.length;
        }
        return 0;
    }



    /**
     * 新しくAudioRecordを生成する
     * パーミッションがない場合などはnullが返り値
     */
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            //このデータ形式で割当可能で妥当なバッファサイズを取得
            final int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            //割当不可能または無効な値の時
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, CHANNEL, ENCODING, minBufferSize);
            Log.d(TAG,"initialized:" + String.valueOf(audioRecord.getState ()));
            //正常に初期化されていたら
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                //バッファ初期化
                mBuffer = new byte[minBufferSize];
                bufferLen = minBufferSize;
                Log.d(TAG,"initialized:" + String.valueOf(sampleRate));
                return audioRecord;
            } else {
                audioRecord.release();
            }
        }
        return null;
    }

    /**
     * 継続的に録音データを処理し、それをコールバックに通知する
     */
    private class ProcessVoice implements Runnable {

        @Override
        public void run() {
            while (true) {
                synchronized (mLock) {
                    //実行スレッドが停止されていたら終了
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    short[] shortBuffer = new short[bufferLen / 2];
                    //バッファから音声データ読み出し
                    final int size = mAudioRecord.read(shortBuffer, 0, shortBuffer.length);
                    mBuffer = short2byte ( shortBuffer );
                    final long now = System.currentTimeMillis();
                    //音声が入力されていたら
                    if (isHearingVoice(mBuffer, size)) {
                        //音声入力が中断されていたら(=今が音声入力の始まりだったら)
                        if (mLastVoiceHeardMillis == Long.MAX_VALUE) {
                            //音声入力の始まりを今にする
                            mVoiceStartedMillis = now;
                            //音声入力が始まったことを通知
                            mCallback.onVoiceStart();
                        }
                        //コールバックに通知
                        mCallback.onVoice(mBuffer, size);
                        //最後に音声が入力された時刻を今にする
                        mLastVoiceHeardMillis = now;
                        //最長の喋る時間を超えたら
                        if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                            end();
                        }
                        //今現在は音声入力されていないが、タイムアウトではないとき
                    } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                        mCallback.onVoice(mBuffer, size);
                        //タイムアウトしたら
                        if (now - mLastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS) {
                            end();
                        }
                    }
                }
            }
        }

        private void end() {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            //音声入力の終了を通知
            mCallback.onVoiceEnd();
        }

        private boolean isHearingVoice(byte[] buffer, int size) {
            // バッファはLINEAR16をリトルエンディアンで保持しているので、
            // 連続した２つのバイトを置換して連結する
            for (int i = 0; i < size - 1; i += 2) {
                int s = buffer[i + 1];
                //負の場合は正にする
                if (s < 0) s *= -1;
                //後続バイトを上8bitにする
                s <<= 8;
                //下8bitを先頭バイトにする
                s += Math.abs(buffer[i]);
                //喋っているとみなすしきい値を超えていたら
                if (s > AMPLITUDE_THRESHOLD) {
                    return true;
                }
            }
            return false;
        }

    }
    private byte[] short2byte(short[] sData) {
        // Little Endianのバッファを用意する
        ByteBuffer nativeOriginBuffer = ByteBuffer.allocateDirect(sData.length * 2).order( ByteOrder.LITTLE_ENDIAN);
        ShortBuffer nativeBuffer = nativeOriginBuffer.asShortBuffer();

        // short[]をNative Bufferに書き込む
        nativeBuffer.position(0);
        nativeBuffer.put(sData, 0, sData.length).position(0);   // Little Endianに書き出す
        nativeOriginBuffer.position(0);

        // nio.BufferからのデータをAudioContentとしてストリームに投げる
        return nativeOriginBuffer.array ();
    }
}