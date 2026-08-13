package org.runite.client;

final class SoundFilter {

    private static final float[][] coefficient = new float[2][8];
    public static int[][] coefficient16 = new int[2][8];
    private static float unity;
    public static float unity16;
    private final int[][][] frequencies = new int[2][2][4];
    private final int[][][] ranges = new int[2][2][4];
    private final int[] unities = new int[2];
    int[] pairs = new int[2];

    private static float normalize(float f) {
        return (32.7032F * (float) Math.pow(2D, f) * 3.141593F) / 11025.0F;
    }

    void read(DataBuffer buffer, SoundEnvelope envelope) {
        int count = buffer.readUnsignedByte();
        pairs[0] = count >> 4;
        pairs[1] = count & 15;

        if (count != 0) {
            unities[0] = buffer.readUnsignedShort();
            unities[1] = buffer.readUnsignedShort();

            int migration = buffer.readUnsignedByte();

            for (int direction = 0; direction < 2; ++direction) {
                for (int pair = 0; pair < pairs[direction]; ++pair) {
                    frequencies[direction][0][pair] = buffer.readUnsignedShort();
                    ranges[direction][0][pair] = buffer.readUnsignedShort();
                }
            }

            for (int direction = 0; direction < 2; ++direction) {
                for (int pair = 0; pair < pairs[direction]; ++pair) {
                    if ((migration & 1 << direction * 4 << pair) == 0) {
                        frequencies[direction][1][pair] = frequencies[direction][0][pair];
                        ranges[direction][1][pair] = ranges[direction][0][pair];
                    } else {
                        frequencies[direction][1][pair] = buffer.readUnsignedShort();
                        ranges[direction][1][pair] = buffer.readUnsignedShort();
                    }
                }
            }

            if (migration != 0 || unities[1] != unities[0]) {
                envelope.readShape(buffer);
            }
        } else {
            unities[0] = unities[1] = 0;
        }
    }

    int evaluate(int direction, float delta) {
        if (direction == 0) {
            float u = (float) unities[0] + (float) (unities[1] - unities[0]) * delta;
            u *= 0.0030517578F;
            unity = (float) Math.pow(0.1D, u / 20.0F);
            unity16 = (int) (unity * 65536.0F);
        }

        if (pairs[direction] == 0) {
            return 0;
        }

        float u = gain(direction, 0, delta);
        coefficient[direction][0] = -2.0F * u * (float) Math.cos(phase(direction, 0, delta));
        coefficient[direction][1] = u * u;

        for (int pair = 1; pair < pairs[direction]; ++pair) {
            float g = gain(direction, pair, delta);
            float a = -2.0F * g * (float) Math.cos(phase(direction, pair, delta));
            float b = g * g;
            coefficient[direction][pair * 2 + 1] = coefficient[direction][pair * 2 - 1] * b;
            coefficient[direction][pair * 2] = coefficient[direction][pair * 2 - 1] * a + coefficient[direction][pair * 2 - 2] * b;

            for (int otherPair = pair * 2 - 1; otherPair >= 2; --otherPair) {
                coefficient[direction][otherPair] += coefficient[direction][otherPair - 1] * a + coefficient[direction][otherPair - 2] * b;
            }

            coefficient[direction][1] += coefficient[direction][0] * a + b;
            coefficient[direction][0] += a;
        }

        if (direction == 0) {
            for (int pair = 0; pair < pairs[0] * 2; ++pair) {
                coefficient[0][pair] *= unity;
            }
        }

        for (int pair = 0; pair < pairs[direction] * 2; ++pair) {
            coefficient16[direction][pair] = (int) (coefficient[direction][pair] * 65536.0F);
        }

        return pairs[direction] * 2;
    }

    private float gain(int direction, int pair, float delta) {
        float g = (float) ranges[direction][0][pair] + delta * (float) (ranges[direction][1][pair] - ranges[direction][0][pair]);
        g *= 0.0015258789F;
        return 1.0F - (float) Math.pow(10.0D, -g / 20.0F);
    }

    private float phase(int direction, int pair, float delta) {
        float f1 = (float) frequencies[direction][0][pair] + delta * (float) (frequencies[direction][1][pair] - frequencies[direction][0][pair]);
        f1 *= 0.0001220703F;
        return normalize(f1);
    }

}
