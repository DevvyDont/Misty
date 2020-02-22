package sh.niall.misty.utils.cogs;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import sh.niall.yui.Yui;
import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;
import sh.niall.yui.waiter.EventWaiter;
import sh.niall.yui.waiter.interfaces.EventCheck;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class MistyCog extends Cog {

    public boolean sendConfirmation(Context ctx, String question) throws WaiterException, CommandException {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Confirmation!");
        embedBuilder.setDescription(question);
        embedBuilder.setColor(Color.ORANGE);
        embedBuilder.setAuthor(ctx.getAuthor().getEffectiveName(), null, ctx.getUser().getEffectiveAvatarUrl());
        return sendConfirmation(ctx, embedBuilder.build());
    }

    public boolean sendConfirmation(Context ctx, MessageEmbed embed) throws WaiterException, CommandException {
        Message message = ctx.send(embed);
        message.addReaction("✅").complete();
        message.addReaction("❌").complete();

        GuildMessageReactionAddEvent reactionAddEvent = (GuildMessageReactionAddEvent) waitForEvent(
                GuildMessageReactionAddEvent.class, check -> {
                    GuildMessageReactionAddEvent e = (GuildMessageReactionAddEvent) check;
                    return (e.getMember().getIdLong() == ctx.getAuthor().getIdLong()) &&
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

    public boolean sendConfirmation(Context ctx, MessageEmbed embed, long target) throws WaiterException, CommandException {
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
}
