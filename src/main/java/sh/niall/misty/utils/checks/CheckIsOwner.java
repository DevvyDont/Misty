package sh.niall.misty.utils.checks;

import sh.niall.misty.Misty;
import sh.niall.yui.commands.Context;

public class CheckIsOwner {

    public static boolean runCheck(Context ctx) {
        Long ownerId = Misty.ownerId;
        if (ownerId == null) {
            Misty.ownerId = ctx.getBot().retrieveApplicationInfo().complete().getOwner().getIdLong();
            ownerId = Misty.ownerId;
        }

        return ownerId == ctx.getAuthor().getIdLong();
    }

}
