package example.jakarta;

/** Business logic over a User. */
public final class Ages {

    private Ages() {
    }

    static final String[] GROUPS = {"child", "teen", "adult", "senior"}; // 4 buckets

    /**
     * BUG: {@code age / 30 == 4} when {@code age == 120} — and {@code @Max(120)}
     * says 120 is a valid age. So a perfectly valid User crashes this.
     */
    public static int group(User u) {
        return GROUPS[u.age / 30].length();
    }

    /** The fix: clamp into range. Provably safe for every valid User. */
    public static int groupSafe(User u) {
        int i = u.age / 30;
        if (i >= GROUPS.length) {
            i = GROUPS.length - 1;
        }
        return GROUPS[i].length();
    }
}
