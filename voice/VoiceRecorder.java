package com.example.myapplication.voice;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 継続的に録音し、音声が入力されたらコールバックに通知
 * オーディオフォーマットはPCM16でチャンネルはモノラル
 * サンプリング周波数は使用できる周波数を動的に決定する
 */
public class VoiceRecorder {


    private static final String TAG = "AudioRecorder";
    //モノラル
    static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    //PCM16
    static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    //周波数の候補
    //16000が最適、44100は通信の都合上優先度低め
    private static final int[] SAMPLE_RATE_CANDIDATES = new int[] { 16000, 44100, 22050, 11025 };
    //喋っているとみなす最小の音量
    private static final int AMPLITUDE_THRESHOLD = 3000;
    //喋るのが終わったとみなす時間
    private static final int SPEECH_TIMEOUT_MILLIS = 2000;
    //喋る時間の最大の長さ
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;

    protected interface Callback {
        void onVoiceStart ();

        /**
         * 音声が入力されている間呼ばれる
         *
         * @param data PCM16の音声データ
         * @param size dataの実サイズ
         */
        void onVoice ( byte[] data, int size );

        void onVoiceEnd ();
    }

    //通知先
    private final Callback mCallback;

    //録音を行う
    private AudioRecord mAudioRecord;

    //録音された音声データの処理を行うスレッド
    private Thread mThread;

    //音声のバッファ
    private short[] mBuffer;

    //同期実行で使用
    private final Object mLock = new Object ();

    //最後に音声が入力された時点でのタイムスタンプ
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;

    //現在の音声入力が始まった時点のタイムスタンプ
    private long mVoiceStartedMillis;

    VoiceRecorder ( @NonNull Callback callback ) {
        mCallback = callback;
    }

    /**
     * 録音を始める
     * これを実行したらあとでstop()を実行しなければならない
     */
    int start () {
        //現在の録音を停止する
        stop ();
        //新しく録音のセッションを生成する
        mAudioRecord = createAudioRecord ();
        if ( mAudioRecord == null ) {
            throw new RuntimeException ( "Cannot instantiate VoiceRecorder" );
        }
        //録音を始める
        mAudioRecord.startRecording ();
        //録音されたデータを処理するスレッドの生成
        mThread = new Thread ( new ProcessVoice () );
        mThread.start ();
        return mBuffer.length;
    }

    void stop () {
        synchronized ( mLock ) {
            //音声入力中だった場合
            if ( mLastVoiceHeardMillis != Long.MAX_VALUE ) {
                mLastVoiceHeardMillis = Long.MAX_VALUE;
                mCallback.onVoiceEnd ();
            }
            //
            if ( mThread != null ) {
                mThread.interrupt ();
                mThread = null;
            }
            //
            if ( mAudioRecord != null ) {
                mAudioRecord.stop ();
                mAudioRecord.release ();
                mAudioRecord = null;
            }
            mBuffer = null;
        }
    }

    /**
     * 新しくAudioRecordを生成する
     * パーミッションがない場合などはnullが返り値
     */
    private AudioRecord createAudioRecord () {
        final int AUDIO_BUFFER_BYTES = 1024 * 8;  // バッファリングする切り上げサイズ
        for ( int sampleRate : SAMPLE_RATE_CANDIDATES ) {
            //このデータ形式で割当可能で妥当なバッファサイズを取得
            final int minBufferSize = AudioRecord.getMinBufferSize ( sampleRate, CHANNEL, ENCODING );
            //割当不可能または無効な値の時
            if ( minBufferSize == AudioRecord.ERROR_BAD_VALUE ) {
                continue;
            }
            final int CAPTURE_CACHE_SIZE = ( ( ( minBufferSize * 4 ) / AUDIO_BUFFER_BYTES ) + 1 ) * AUDIO_BUFFER_BYTES;
            final AudioRecord audioRecord = new AudioRecord ( MediaRecorder.AudioSource.MIC,
                    sampleRate, CHANNEL, ENCODING, CAPTURE_CACHE_SIZE );
            Log.d ( TAG, "initialized:" + String.valueOf ( audioRecord.getState () ) );
            //正常に初期化されていたら
            if ( audioRecord.getState () == AudioRecord.STATE_INITIALIZED ) {
                //バッファ初期化
                mBuffer = new short[ CAPTURE_CACHE_SIZE ];
                Log.d ( TAG, "initialized:" + String.valueOf ( sampleRate ) );
                return audioRecord;
            } else {
                audioRecord.release ();
            }
        }
        return null;
    }

    /**
     * 継続的に録音データを処理し、それをコールバックに通知する
     */
    private class ProcessVoice implements Runnable {

        @Override
        public void run () {
            while ( true ) {
                synchronized ( mLock ) {
                    //実行スレッドが停止されていたら終了
                    if ( Thread.currentThread ().isInterrupted () ) {
                        break;
                    }
                    //バッファから音声データ読み出し
                    final int size = mAudioRecord.read ( mBuffer, 0, mBuffer.length );
                    //リトルエンディアンに変換
                    final byte[] byteArray = short2byte ( mBuffer );
                    final long now = System.currentTimeMillis ();
                    if ( isHearingVoice ( byteArray, size ) ) {
                        //音声入力が中断されていたら(=今が音声入力の始まりだったら)
                        if ( mLastVoiceHeardMillis == Long.MAX_VALUE ) {
                            //音声入力の始まりを今にする
                            mVoiceStartedMillis = now;
                            mCallback.onVoiceStart ();
                        }
                        mCallback.onVoice ( byteArray, size );
                        //最後に音声が入力された時刻を今にする
                        mLastVoiceHeardMillis = now;
                        //最長の喋る時間を超えたら
                        if ( now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS ) {
                            end ();
                        }
                        //今喋っていないが、音声入力中のとき
                    } else if ( mLastVoiceHeardMillis != Long.MAX_VALUE ) {
                        mCallback.onVoice ( byteArray, size );
                        //タイムアウトしているなら
                        if ( now - mLastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS ) {
                            end ();
                        }
                    }
                }
            }
        }

        private void end () {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd ();
        }

        private boolean isHearingVoice ( byte[] buffer, int size ) {
            // バッファはLINEAR16をリトルエンディアンで保持しているので、
            // 連続した２つのバイトを置換して連結する
            for ( int i = 0; i < size - 1; i += 2 ) {
                int s = buffer[ i ];
                //負の場合は正にする
                if ( s < 0 ) s *= -1;
                //後続バイトを上8bitにする
                s <<= 8;
                //下8bitを先頭バイトにする
                s += Math.abs ( buffer[ i + 1 ] );
                //喋っているとみなすしきい値を超えていたら
                if ( s > AMPLITUDE_THRESHOLD ) {
                    return true;
                }
            }
            return false;
        }
    }

    private static byte[] short2byte ( short[] sData ) {
        //shortは2byteなので
        ByteBuffer buffer = ByteBuffer.allocate ( sData.length * 2 );
        buffer.order ( ByteOrder.LITTLE_ENDIAN );
        buffer.asShortBuffer ().put ( sData );
        return buffer.array ();
    }

}