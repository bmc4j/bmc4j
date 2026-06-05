package example.arraybounds;

/** Maps a percentage score to a grade-band label. */
public final class GradeBand {

    private GradeBand() {
    }

    static final String[] BANDS = {"F", "D", "C", "B", "A"};

    /**
     * BUG: {@code score / 20 == 5} when {@code score == 100}, indexing one past
     * the end of {@code BANDS}. A hand-written test of 1, 50, 99 never reveals it.
     */
    public static int label(int score) {
        return BANDS[score / 20].length();
    }

    /** The fix: clamp the index into range. */
    public static int labelSafe(int score) {
        int index = score / 20;
        if (index >= BANDS.length) {
            index = BANDS.length - 1;
        }
        return BANDS[index].length();
    }
}
