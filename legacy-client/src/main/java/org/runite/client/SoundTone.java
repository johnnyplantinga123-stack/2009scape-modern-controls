package org.runite.client;

import org.rs09.client.util.ArrayUtils;

import java.util.Random;

final class SoundTone {

    private static final int[] sin = new int[32768];
    private static final int[] noise = new int[32768];
    private static final int[] buffer = new int[22050 * 10]; // 10 second buffer @ 22050 KHz
    private static final int[] tmpSemitones = new int[5];
    private static final int[] tmpStarts = new int[5];
    private static final int[] tmpPhases = new int[5];
    private static final int[] tmpDelays = new int[5];
    private static final int[] tmpVolumes = new int[5];

    static {
        Random rand = new Random(0L);
        for (int n = 0; n < 32768; ++n) {
            noise[n] = (rand.nextInt() & 2) - 1;
        }

        for (int n = 0; n < 32768; ++n) {
            sin[n] = (int) (Math.sin((double) n / 5215.1903D) * 16384.0D);
        }
    }

    private final int[] harmonicVolume = new int[5];
    private final int[] harmonicDelay = new int[5];
    private final int[] harmonicSemitone = new int[5];
    int length = 500;
    int start = 0;
    private SoundEnvelope amplitudeModRange;
    private SoundEnvelope release;
    private SoundEnvelope amplitudeBase;
    private SoundEnvelope attack;
    private SoundEnvelope frequencyBase;
    private int reverbDelay = 0;
    private SoundEnvelope filterRange;
    private SoundEnvelope amplitudeModRate;
    private int reverbVolume = 100;
    private SoundFilter filter;
    private SoundEnvelope frequencyModRange;
    private SoundEnvelope frequencyModRate;

    private int generate(int phase, int amplitude, int form) {
        if (form == 1) {
            if ((phase & 0x7fff) < 16384) {
                return amplitude;
            } else {
                return -amplitude;
            }
        } else if (form == 2) {
            return (sin[phase & 0x7fff] * amplitude) >> 14;
        } else if (form == 3) {
            return (((phase & 0x7fff) * amplitude) >> 14) - amplitude;
        } else if (form == 4) {
            return noise[(phase / 2607) & 0x7fff] * amplitude;
        } else {
            return 0;
        }
    }

    int[] generate(int sampleCount, int length) {
        ArrayUtils.zero(buffer, 0, sampleCount);

        if (length < 10) {
            return buffer;
        }

        double samplesPerStep = (double) sampleCount / ((double) length + 0.0D);

        frequencyBase.reset();
        amplitudeBase.reset();

        int frequencyStart = 0;
        int frequencyDuration = 0;
        int frequencyPhase = 0;
        if (frequencyModRate != null) {
            frequencyModRate.reset();
            frequencyModRange.reset();
            frequencyStart = (int) ((double) (frequencyModRate.end - frequencyModRate.start) * 32.768D / samplesPerStep);
            frequencyDuration = (int) ((double) frequencyModRate.start * 32.768D / samplesPerStep);
        }

        int amplitudeStart = 0;
        int amplitudeDuration = 0;
        int amplitudePhase = 0;
        if (amplitudeModRate != null) {
            amplitudeModRate.reset();
            amplitudeModRange.reset();
            amplitudeStart = (int) ((double) (amplitudeModRate.end - amplitudeModRate.start) * 32.768D / samplesPerStep);
            amplitudeDuration = (int) ((double) amplitudeModRate.start * 32.768D / samplesPerStep);
        }

        for (int harmonic = 0; harmonic < 5; ++harmonic) {
            if (harmonicVolume[harmonic] != 0) {
                tmpPhases[harmonic] = 0;
                tmpDelays[harmonic] = (int) ((double) harmonicDelay[harmonic] * samplesPerStep);
                tmpVolumes[harmonic] = (harmonicVolume[harmonic] << 14) / 100;
                tmpSemitones[harmonic] = (int) ((double) (frequencyBase.end - frequencyBase.start) * 32.768D * Math.pow(1.0057929410678534D, harmonicSemitone[harmonic]) / samplesPerStep);
                tmpStarts[harmonic] = (int) ((double) frequencyBase.start * 32.768D / samplesPerStep);
            }
        }

        for (int sample = 0; sample < sampleCount; ++sample) {
            int frequency = frequencyBase.evaluate(sampleCount);
            int amplitude = amplitudeBase.evaluate(sampleCount);

            if (frequencyModRate != null) {
                int rate = frequencyModRate.evaluate(sampleCount);
                int range = frequencyModRange.evaluate(sampleCount);
                frequency += generate(frequencyPhase, range, frequencyModRate.form) >> 1;
                frequencyPhase += (rate * frequencyStart >> 16) + frequencyDuration;
            }

            if (amplitudeModRate != null) {
                int rate = amplitudeModRate.evaluate(sampleCount);
                int range = amplitudeModRange.evaluate(sampleCount);
                amplitude = amplitude * ((generate(amplitudePhase, range, amplitudeModRate.form) >> 1) + 32768) >> 15;
                amplitudePhase += (rate * amplitudeStart >> 16) + amplitudeDuration;
            }

            for (int harmonic = 0; harmonic < 5; ++harmonic) {
                if (harmonicVolume[harmonic] != 0) {
                    int position = sample + tmpDelays[harmonic];
                    if (position < sampleCount) {
                        buffer[position] += generate(tmpPhases[harmonic], amplitude * tmpVolumes[harmonic] >> 15, frequencyBase.form);
                        tmpPhases[harmonic] += (frequency * tmpSemitones[harmonic] >> 16) + tmpStarts[harmonic];
                    }
                }
            }
        }

        if (release != null) {
            release.reset();
            attack.reset();

            int counter = 0;
            boolean muted = true;

            for (int sample = 0; sample < sampleCount; ++sample) {
                int releaseValue = release.evaluate(sampleCount);
                int attackValue = attack.evaluate(sampleCount);
                int threshold;

                if (muted) {
                    threshold = release.start + (((release.end - release.start) * releaseValue) >> 8);
                } else {
                    threshold = release.start + (((release.end - release.start) * attackValue) >> 8);
                }

                if ((counter += 256) >= threshold) {
                    counter = 0;
                    muted = !muted;
                }

                if (muted) {
                    buffer[sample] = 0;
                }
            }
        }

        if (reverbDelay > 0 && reverbVolume > 0) {
            int start = (int) ((double) reverbDelay * samplesPerStep);

            for (int sample = start; sample < sampleCount; ++sample) {
                buffer[sample] += buffer[sample - start] * reverbVolume / 100;
            }
        }

        if (filter.pairs[0] > 0 || filter.pairs[1] > 0) {
            filterRange.reset();

            int range = filterRange.evaluate(sampleCount + 1);
            int forward = filter.evaluate(0, (float) range / 65536.0F);
            int backward = filter.evaluate(1, (float) range / 65536.0F);

            if (sampleCount >= forward + backward) {
                int index = 0;
                int interval = backward;

                if (interval > (sampleCount - forward)) {
                    interval = sampleCount - forward;
                }

                for (; index < interval; index++) {
                    int sample = (int) ((long) buffer[index + forward] * (long) SoundFilter.unity16 >> 16);

                    for (int offset = 0; offset < forward; ++offset) {
                        sample += (int) ((long) buffer[index + forward - 1 - offset] * (long) SoundFilter.coefficient16[0][offset] >> 16);
                    }

                    for (int offset = 0; offset < index; ++offset) {
                        sample -= (int) ((long) buffer[index - 1 - offset] * (long) SoundFilter.coefficient16[1][offset] >> 16);
                    }

                    buffer[index] = sample;
                    range = filterRange.evaluate(sampleCount + 1);
                }

                interval = 128;

                while (true) {
                    if (interval > sampleCount - forward) {
                        interval = sampleCount - forward;
                    }

                    for (; index < interval; index++) {
                        int sample = (int) ((long) buffer[index + forward] * (long) SoundFilter.unity16 >> 16);

                        for (int offset = 0; offset < forward; ++offset) {
                            sample += (int) ((long) buffer[index + forward - 1 - offset] * (long) SoundFilter.coefficient16[0][offset] >> 16);
                        }

                        for (int offset = 0; offset < backward; ++offset) {
                            sample -= (int) ((long) buffer[index - 1 - offset] * (long) SoundFilter.coefficient16[1][offset] >> 16);
                        }

                        buffer[index] = sample;
                        range = filterRange.evaluate(sampleCount + 1);
                    }

                    if (index >= sampleCount - forward) {
                        for (; index < sampleCount; index++) {
                            int sample = 0;

                            for (int offset = (index + forward) - sampleCount; offset < forward; offset++) {
                                sample += (int) (((long) buffer[(index + forward) - 1 - offset] * (long) SoundFilter.coefficient16[0][offset]) >> 16);
                            }

                            for (int offset = 0; offset < backward; offset++) {
                                sample -= (int) (((long) buffer[index - 1 - offset] * (long) SoundFilter.coefficient16[1][offset]) >> 16);
                            }

                            buffer[index] = sample;
                            filterRange.evaluate(sampleCount + 1);
                        }
                        break;
                    }

                    forward = filter.evaluate(0, (float) range / 65536.0F);
                    backward = filter.evaluate(1, (float) range / 65536.0F);
                    interval += 128;
                }
            }
        }

        for (int sample = 0; sample < sampleCount; ++sample) {
            if (buffer[sample] < -32768) {
                buffer[sample] = -32768;
            }

            if (buffer[sample] > 32767) {
                buffer[sample] = 32767;
            }
        }

        return buffer;
    }

    void read(DataBuffer buffer) {
        frequencyBase = new SoundEnvelope();
        frequencyBase.read(buffer);
        amplitudeBase = new SoundEnvelope();
        amplitudeBase.read(buffer);

        if (buffer.readUnsignedByte() != 0) {
            --buffer.index;
            frequencyModRate = new SoundEnvelope();
            frequencyModRate.read(buffer);
            frequencyModRange = new SoundEnvelope();
            frequencyModRange.read(buffer);
        }

        if (buffer.readUnsignedByte() != 0) {
            --buffer.index;
            amplitudeModRate = new SoundEnvelope();
            amplitudeModRate.read(buffer);
            amplitudeModRange = new SoundEnvelope();
            amplitudeModRange.read(buffer);
        }

        if (buffer.readUnsignedByte() != 0) {
            --buffer.index;
            release = new SoundEnvelope();
            release.read(buffer);
            attack = new SoundEnvelope();
            attack.read(buffer);
        }

        for (int n = 0; n < 10; ++n) {
            int volume = buffer.getSmart();
            if (volume == 0) {
                break;
            }

            harmonicVolume[n] = volume;
            harmonicSemitone[n] = buffer.getByteOrShort();
            harmonicDelay[n] = buffer.getSmart();
        }

        reverbDelay = buffer.getSmart();
        reverbVolume = buffer.getSmart();

        length = buffer.readUnsignedShort();
        start = buffer.readUnsignedShort();

        filter = new SoundFilter();
        filterRange = new SoundEnvelope();
        filter.read(buffer, filterRange);
    }
}
