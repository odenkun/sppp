package com.example.myapplication.voice;

public class VoiceRepository implements VoiceRecorder.Callback {

    private VoiceRecorder mVoiceRecorder;
    private VoiceTransmitter mTransmitter;

    public VoiceRepository() {
        mVoiceRecorder = new VoiceRecorder(this);
        int sampleRate = mVoiceRecorder.start();
        mTransmitter = new VoiceTransmitter(sampleRate);
    }

    public void stop() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
        if (mTransmitter != null) {
            mTransmitter.close();
        }
    }

    @Override
    public void onVoiceStart() {
        mTransmitter.startRecognize ();
    }

    @Override
    public void onVoice(byte[] data, int size) {
        mTransmitter.sendVoice (data);
    }

    @Override
    public void onVoiceEnd() {
        mTransmitter.stopRecognize();
    }
}