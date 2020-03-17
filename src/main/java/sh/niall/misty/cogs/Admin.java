package sh.niall.misty.cogs;

import sh.niall.misty.utils.checks.CheckIsOwner;
import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Command;

public class Admin extends Cog {

    @Command(name = "reboot")
    public void _commandReboot(Context ctx) {
        if (!CheckIsOwner.runCheck(ctx))
            return;

        ctx.send("Ok restarting!");
        System.exit(0);
    }

}
