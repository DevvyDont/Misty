package sh.niall.misty.cogs;

import sh.niall.yui.cogs.cog.Cog;
import sh.niall.yui.cogs.commands.annotations.Command;
import sh.niall.yui.cogs.commands.checks.annotations.Check;
import sh.niall.yui.cogs.commands.checks.checks.IsOwner;
import sh.niall.yui.cogs.commands.context.Context;
import sh.niall.yui.cogs.commands.help.annotations.CommandHelp;

public class Admin extends Cog {

    @Check(check = IsOwner.class)
    @CommandHelp(hidden = true)
    @Command(name = "reboot")
    public void _commandReboot(Context ctx) {
        ctx.send("Ok restarting!");
        System.exit(0);
    }

}
