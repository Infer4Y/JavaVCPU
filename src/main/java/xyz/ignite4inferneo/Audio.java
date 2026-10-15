/*
 * Copyright (c) 2026 Xavier Dillinger (Infer4Y).
 * All rights reserved.
 */

package xyz.ignite4inferneo;

import javax.sound.sampled.*;
import java.util.Random;

public final class Audio {
    private static final int SAMPLE_RATE = 44_100;
    private static final int VOICE_COUNT = 8;
    private static final Voice[] VOICES = new Voice[VOICE_COUNT];
    private static final Random RANDOM = new Random();

    private static volatile boolean running;
    private static SourceDataLine line;

    private Audio() {
    }

    public static void initialize() {
        if (running) {
            return;
        }

        for (int i = 0; i < VOICE_COUNT; i++) {
            VOICES[i] = new Voice();
        }

        try {
            AudioFormat format = new AudioFormat(
                    SAMPLE_RATE,
                    16,
                    1,
                    true,
                    false
            );

            line = AudioSystem.getSourceDataLine(format);
            line.open(format, SAMPLE_RATE / 10);
            line.start();

            running = true;

            Thread audioThread = new Thread(Audio::runMixer, "JavaVCPU Audio");
            audioThread.setDaemon(true);
            audioThread.start();
        } catch (LineUnavailableException exception) {
            throw new IllegalStateException("Could not initialize audio output", exception);
        }
    }

    /*
     * control bits:
     * bit 7: gate; 1 = enabled, 0 = silent
     * bits 0-2: waveform
     *   0 = square
     *   1 = triangle
     *   2 = saw
     *   3 = noise
     */
    public static void setVoice(int voiceNumber, int midiNote, int control) {
        if (voiceNumber < 0 || voiceNumber >= VOICE_COUNT) {
            return;
        }

        Voice voice = VOICES[voiceNumber];
        voice.enabled = (control & 0x80) != 0;
        voice.waveform = control & 0x07;
        voice.frequency = midiNoteToFrequency(midiNote);
    }

    public static void shutdown() {
        running = false;

        if (line != null) {
            line.drain();
            line.close();
        }
    }

    private static void runMixer() {
        byte[] buffer = new byte[512];

        while (running) {
            for (int i = 0; i < buffer.length; i += 2) {
                double mixedSample = 0.0;

                for (Voice voice : VOICES) {
                    mixedSample += nextSample(voice);
                }

                mixedSample = Math.max(-1.0, Math.min(1.0, mixedSample));

                short pcm = (short) (mixedSample * Short.MAX_VALUE);

                buffer[i] = (byte) (pcm & 0xFF);
                buffer[i + 1] = (byte) ((pcm >> 8) & 0xFF);
            }

            line.write(buffer, 0, buffer.length);
        }
    }

    private static double nextSample(Voice voice) {
        if (!voice.enabled || voice.frequency <= 0) {
            return 0.0;
        }

        double sample = switch (voice.waveform) {
            case 0 -> voice.phase < 0.5 ? 1.0 : -1.0;          // square
            case 1 -> 1.0 - 4.0 * Math.abs(voice.phase - 0.5); // triangle
            case 2 -> (2.0 * voice.phase) - 1.0;               // saw
            case 3 -> RANDOM.nextDouble() * 2.0 - 1.0;         // noise
            default -> 0.0;
        };

        voice.phase += voice.frequency / SAMPLE_RATE;

        if (voice.phase >= 1.0) {
            voice.phase -= 1.0;
        }

        // Lower volume prevents multiple voices clipping immediately.
        return sample * 0.15;
    }

    private static double midiNoteToFrequency(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }

    private static final class Voice {
        private volatile boolean enabled;
        private volatile int waveform;
        private volatile double frequency;
        private double phase;
    }
}