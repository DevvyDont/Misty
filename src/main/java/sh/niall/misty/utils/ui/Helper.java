package sh.niall.misty.utils.ui;

public class Helper {

    public static String singularPlural(int count, String single, String plural) {
        if (count == 1)
            return single;
        return plural;
    }

}
