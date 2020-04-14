package sh.niall.misty.utils.misty;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.yui.cogs.cog.Cog;
import sh.niall.yui.cogs.commands.context.Context;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.YuiException;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class MistyCog extends Cog {

    /**
     * Asks the user to confirm an action
     *
     * @param ctx      The command context
     * @param question The question to ask
     * @return True if they confirm, false if they don't
     * @throws CommandException Thrown if the user didn't respond
     */
    public boolean sendConfirmation(Context ctx, String question) throws YuiException {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Confirmation!");
        embedBuilder.setDescription(question);
        embedBuilder.setColor(Color.YELLOW);
        embedBuilder.setAuthor(UserSettings.getName(ctx), null, ctx.getAuthorUser().getEffectiveAvatarUrl());
        return sendConfirmation(ctx, embedBuilder.build());
    }

    /**
     * Asks the user to confirm an action
     *
     * @param ctx   The command context
     * @param embed The embed to send
     * @return True if they confirm, false if they don't
     * @throws CommandException Thrown if the user didn't respond
     */
    public boolean sendConfirmation(Context ctx, MessageEmbed embed) throws YuiException {
        return sendConfirmation(ctx, embed, ctx.getAuthor().getIdLong());
    }

    /**
     * Asks the user to confirm an action
     *
     * @param ctx    The command context
     * @param embed  The embed to send
     * @param target The user to ask
     * @return True if they confirm, false if they don't
     * @throws CommandException Thrown if the user didn't respond
     */
    public boolean sendConfirmation(Context ctx, MessageEmbed embed, long target) throws YuiException {
        Message message = ctx.send(embed);
        message.addReaction("✅").complete();
        message.addReaction("❌").complete();

        GuildMessageReactionAddEvent reactionAddEvent = (GuildMessageReactionAddEvent) waitForEvent(
                GuildMessageReactionAddEvent.class, check -> {
                    GuildMessageReactionAddEvent e = (GuildMessageReactionAddEvent) check;
                    return (e.getMember().getIdLong() == target) &&
                            (e.getMessageIdLong() == message.getIdLong()) &&
                            (e.getReactionEmote().getEmoji().equals("✅") || e.getReactionEmote().getEmoji().equals("❌"));
                }, 15, TimeUnit.SECONDS
        );

        message.delete().queue();

        if (reactionAddEvent == null)
            throw new CommandException("Timed out waiting for conformation");

        // Handle no logic
        return !reactionAddEvent.getReactionEmote().getEmoji().equals("❌");
    }

    /**
     * Gets the users next message
     *
     * @param ctx The command context
     * @return Their next message, null if they don't respond in time
     */
    public String getNextMessage(Context ctx) throws YuiException {
        GuildMessageReceivedEvent event = ((GuildMessageReceivedEvent) waitForEvent(
                GuildMessageReceivedEvent.class,
                check -> {
                    GuildMessageReceivedEvent e = (GuildMessageReceivedEvent) check;
                    return (e.getChannel().getIdLong() == ctx.getChannel().getIdLong()) && (e.getAuthor().getIdLong() == ctx.getAuthor().getIdLong());
                }, 20, TimeUnit.SECONDS));
        if (event == null)
            return null;
        return event.getMessage().getContentRaw();
    }
}
