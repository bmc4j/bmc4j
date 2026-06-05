package kotlin.coroutines.jvm.internal;

/**
 * Clean model of the primitive boxing helpers the coroutine codegen uses to store
 * suspend results as {@code Object}. {@code boxInt} uses {@code new Integer} so the
 * boxed value carries a concrete {@code Integer} dynamic type for the downstream
 * {@code checkcast Number} the state machine performs.
 */
@SuppressWarnings("deprecation")
public final class Boxing {

    private Boxing() {
    }

    public static Boolean boxBoolean(boolean v) {
        return Boolean.valueOf(v);
    }

    public static Byte boxByte(byte v) {
        return Byte.valueOf(v);
    }

    public static Character boxChar(char v) {
        return Character.valueOf(v);
    }

    public static Short boxShort(short v) {
        return Short.valueOf(v);
    }

    public static Integer boxInt(int v) {
        return Integer.valueOf(v);
    }

    public static Long boxLong(long v) {
        return Long.valueOf(v);
    }

    public static Float boxFloat(float v) {
        return Float.valueOf(v);
    }

    public static Double boxDouble(double v) {
        return Double.valueOf(v);
    }
}
