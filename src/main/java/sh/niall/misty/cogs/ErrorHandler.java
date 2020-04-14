package sh.niall.misty.cogs;

import sh.niall.yui.cogs.cog.Cog;
import sh.niall.yui.cogs.commands.context.Context;

public class ErrorHandler extends Cog {

    @Override
    public void onError(Context ctx, Exception error, boolean thisCog) {
        ctx.sendq("An error occured!", null);
        error.printStackTrace();
    }
}
