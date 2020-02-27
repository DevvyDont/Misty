package sh.niall.misty.errors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import sh.niall.misty.Misty;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.YuiException;

import java.awt.*;
import java.time.LocalDateTime;

public class ErrorHandler extends sh.niall.yui.cogs.ErrorHandler {
    @Override
    public void onError(Context ctx, Throwable error) {
        postError(ctx.getChannel(), error);
    }

    public void postError(MessageChannel channel, Throwable error) {
        EmbedBuilder embedBuilder = generateBaseEmbed();
        //embedBuilder.setAuthor(ctx.getAuthor().getEffectiveName(), null, ctx.getUser().getEffectiveAvatarUrl());


        if (error instanceof YuiException) {
            embedBuilder.addField("Information:", error.getMessage(), false);
            channel.sendMessage(embedBuilder.build()).queue();
            return;
        }

        embedBuilder.addField("Bot Error", "I don't know how to handle this error! Please inform my developer!", false);
        channel.sendMessage(embedBuilder.build()).queue();
        error.printStackTrace();
    }

    private static EmbedBuilder generateBaseEmbed() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Error!");
        embedBuilder.setDescription("I ran into an error processing your request");
        embedBuilder.setColor(Color.RED);
        embedBuilder.setThumbnail(Misty.config.getDiscordErrorImage());
        embedBuilder.setTimestamp(LocalDateTime.now());
        return embedBuilder;
    }
}
