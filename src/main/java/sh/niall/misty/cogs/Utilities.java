package sh.niall.misty.cogs;

import net.dv8tion.jda.api.entities.User;
import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Command;
import sh.niall.yui.exceptions.CommandException;

public class Utilities extends Cog {

    /**
     * Sends the specified users avatar!
     */
    @Command(name = "avatar", aliases = {"avi"})
    public void _commandAvatar(Context ctx) throws CommandException {
        // Create the target user
        User targetUser = null;

        // If we're given an ID, check to see if it's valid
        if (ctx.getArgs().size() > 1) {
            String target = ctx.getArgs().get(1).replace("<@!", "").replace(">", "");
            targetUser = ctx.getBot().getUserById(target);
            if (targetUser == null)
                throw new CommandException("I can't find a user with the ID " + ctx.getArgs().get(1));
        }

        // Make the target the invoker
        if (targetUser == null)
            targetUser = ctx.getUser();

        // Send the output
        ctx.send(String.format("Here is %s avatar:\n%s", targetUser.getName(), targetUser.getEffectiveAvatarUrl()));
    }

    /**
     * Sends the screen share link if the user is in a voice channel
     */
    @Command(name = "screenshare", aliases = {"ss"})
    public void _commandScreenshare(Context ctx) throws CommandException {
        try {
            ctx.send(String.format(
                    "Here is the screenshare link for %s:\n<https://discordapp.com/channels/%s/%s>",
                    ctx.getAuthor().getVoiceState().getChannel().getName(),
                    ctx.getGuild().getId(),
                    ctx.getAuthor().getVoiceState().getChannel().getId()
            ));
        } catch (NullPointerException error) {
            throw new CommandException("You're not currently in a voice channel, so I can't send a link.");
        }
    }

}
