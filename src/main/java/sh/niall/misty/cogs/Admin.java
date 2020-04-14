package sh.niall.misty.cogs;

import sh.niall.yui.cogs.cog.Cog;
import sh.niall.yui.cogs.commands.annotations.Command;
import sh.niall.yui.cogs.commands.checks.annotations.Check;
import sh.niall.yui.cogs.commands.checks.checks.IsOwner;
import sh.niall.yui.cogs.commands.context.Context;

public class Admin extends Cog {

    @Check(check = IsOwner.class)
    @Command(name = "reboot")
    public void _commandReboot(Context ctx) {
        ctx.send("Ok restarting!");
        System.exit(0);
    }

}
