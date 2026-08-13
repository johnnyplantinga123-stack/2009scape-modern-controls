package org.runite.client;

final class SoundEnvelope {

    int form;
    int start;
    int end;
    private int length = 2;
    private int[] shapePeak = new int[2];
    private int[] shapeDelta = new int[2];
    private int position;
    private int amplitude;
    private int delta;
    private int threshold;
    private int ticks;

    public SoundEnvelope() {
        //shapeDelta[0] = 0;
        shapeDelta[1] = 65535;
        //shapePeak[0] = 0;
        shapePeak[1] = 65535;
    }

    int evaluate(int delta) {
        if (ticks >= threshold) {
            amplitude = shapePeak[position++] << 15;
            if (position >= length) {
                position = length - 1;
            }

            threshold = (int) ((double) shapeDelta[position] / 65536.0D * (double) delta);
            if (threshold > ticks) {
                this.delta = ((shapePeak[position] << 15) - amplitude) / (threshold - ticks);
            }
        }

        amplitude += this.delta;
        ++ticks;
        return amplitude - this.delta >> 15;
    }

    void reset() {
        threshold = 0;
        position = 0;
        delta = 0;
        amplitude = 0;
        ticks = 0;
    }

    void readShape(DataBuffer buffer) {
        length = buffer.readUnsignedByte();
        shapeDelta = new int[length];
        shapePeak = new int[length];

        for (int n = 0; n < length; ++n) {
            shapeDelta[n] = buffer.readUnsignedShort();
            shapePeak[n] = buffer.readUnsignedShort();
        }
    }

    void read(DataBuffer buffer) {
        form = buffer.readUnsignedByte();
        start = buffer.readInt();
        end = buffer.readInt();
        readShape(buffer);
    }
}
