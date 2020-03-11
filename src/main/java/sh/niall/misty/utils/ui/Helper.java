package sh.niall.misty.utils.ui;

import java.awt.*;
import java.util.Random;

public class Helper {

    private static Color[] colors = new Color[]{
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.MAGENTA,
            Color.ORANGE,
            Color.PINK,
    };
    private static Random random = new Random();

    /**
     * Returns the correct word to use based on the count.
     *
     * @param count  The determining factor
     * @param single The singular word to use
     * @param plural The plural word to use
     * @return The correct word to use based on the count. Returns single if count == 1
     */
    public static String singularPlural(long count, String single, String plural) {
        if (count == 1)
            return single;
        return plural;
    }

    public static Color randomColor() {
        return colors[random.nextInt(colors.length)];
    }

}
