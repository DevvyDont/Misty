package sh.niall.misty.utils.ui;

public class Helper {

    /**
     * Returns the correct word to use based on the count.
     *
     * @param count  The determining factor
     * @param single The singular word to use
     * @param plural The plural word to use
     * @return The correct word to use based on the count. Returns single if count == 1
     */
    public static String singularPlural(int count, String single, String plural) {
        if (count == 1)
            return single;
        return plural;
    }

}
