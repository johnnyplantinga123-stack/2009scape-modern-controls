package org.runite.client;

public final class SoundTrack {

    private final SoundTone[] tones = new SoundTone[10];
    private int end;
    private int begin;

    private SoundTrack(DataBuffer buffer) {
        for (int tone = 0; tone < 10; ++tone) {
            if (buffer.readUnsignedByte() != 0) {
                --buffer.index;
                tones[tone] = new SoundTone();
                tones[tone].read(buffer);
            }
        }

        begin = buffer.readUnsignedShort();
        end = buffer.readUnsignedShort();
    }

    public static SoundTrack create(CacheIndex store, int archive, int file) {
        byte[] data = store.getFile(archive, file);
        return data == null ? null : new SoundTrack(new DataBuffer(data));
    }

    private byte[] generate() {
        int duration = 0;

        for (int tone = 0; tone < 10; ++tone) {
            if (tones[tone] != null && tones[tone].length + tones[tone].start > duration) {
                duration = tones[tone].length + tones[tone].start;
            }
        }

        if (duration == 0) {
            return new byte[0];
        }

        int sampleCount = (22050 * duration) / 1000;
        byte[] bbuf = new byte[sampleCount];

        for (int tone = 0; tone < 10; ++tone) {
            if (tones[tone] == null) {
                continue;
            }

            int toneSampleCount = (tones[tone].length * 22050) / 1000;
            int start = (tones[tone].start * 22050) / 1000;
            int[] samples = tones[tone].generate(toneSampleCount, tones[tone].length);

            for (int sample = 0; sample < toneSampleCount; ++sample) {
                bbuf[sample + start] += (byte) (samples[sample] >> 8);
            }
        }

        return bbuf;
    }

    public PcmSound toPCMSound() {
        return new PcmSound(generate(), 22050 * begin / 1000, 22050 * end / 1000);
    }

    int trim() {
        int start = 9999999;

        for (int tone = 0; tone < 10; ++tone) {
            if (tones[tone] != null && tones[tone].start / 20 < start) {
                start = tones[tone].start / 20;
            }
        }

        if (begin < end && (begin / 20) < start) {
            start = begin / 20;
        }

        if (start == 9999999 || start == 0) {
            return 0;
        }

        for (int tone = 0; tone < 10; ++tone) {
            if (tones[tone] != null) {
                tones[tone].start -= start * 20;
            }
        }

        if (begin < end) {
            begin -= start * 20;
            end -= start * 20;
        }

        return start;
    }

}
