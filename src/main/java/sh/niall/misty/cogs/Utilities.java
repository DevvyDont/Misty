package sh.niall.misty.cogs;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import sh.niall.misty.utils.misty.MistyCog;
import sh.niall.misty.utils.settings.Languages;
import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.misty.utils.ui.Menu;
import sh.niall.yui.cogs.commands.annotations.Command;
import sh.niall.yui.cogs.commands.checks.annotations.Check;
import sh.niall.yui.cogs.commands.checks.checks.IsGuildMessage;
import sh.niall.yui.cogs.commands.context.Context;
import sh.niall.yui.cogs.commands.help.annotations.CommandHelp;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.YuiException;

import java.awt.*;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

public class Utilities extends MistyCog {

    static final String inviteLink = "<https://discordapp.com/oauth2/authorize?client_id=%s&scope=bot&permissions=8>";

    /**
     * Sends the specified users avatar!
     */
    @CommandHelp(desc = "Get a users avatar", arguments = {"Optional: User Mention"})
    @Command(name = "avatar", aliases = {"avi"})
    public void _commandAvatar(Context ctx) throws CommandException {
        // Create the target user
        User targetUser = null;

        // If we're given an ID, check to see if it's valid
        if (!ctx.getArguments().isEmpty()) {
            String target = ctx.getArguments().get(0).replace("<@!", "").replace(">", "");
            targetUser = ctx.getJda().getUserById(target);
            if (targetUser == null)
                throw new CommandException("I can't find a user with the ID " + ctx.getArguments().get(0));
        }

        // Make the target the invoker
        if (targetUser == null)
            targetUser = ctx.getAuthorUser();

        // Send the output
        ctx.send(String.format("Here is %s avatar:\n%s", targetUser.getName(), targetUser.getEffectiveAvatarUrl()));
    }

    /**
     * Sends the screen share link if the user is in a voice channel
     */
    @Check(check = IsGuildMessage.class)
    @CommandHelp(name = "Screen Share", desc = "Get the screen share link for your voice channel")
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

    @CommandHelp(desc = "Get the link to invite me to your server")
    @Command(name = "invite")
    public void _commandInvite(Context ctx) {
        ctx.send(String.format(inviteLink, getYui().getJda().getSelfUser().getId()));
    }

    @CommandHelp(desc = "Change your Misty settings")
    @Command(name = "settings")
    public void _commandSettings(Context ctx) throws YuiException {
        UserSettings userSettings = new UserSettings(ctx.getAuthor().getIdLong());

        int menuOption = Menu.showMenu(
                ctx,
                "What would you like to edit?",
                new String[]{
                        "Change Timezone",
                        "Change Language",
                        "Set Preferred Name"
                }
        );

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("User Settings Change");
        embedBuilder.setDescription("Are you sure you want to change the following setting");
        embedBuilder.setAuthor(UserSettings.getName(ctx), null, ctx.getAuthorUser().getEffectiveAvatarUrl());
        embedBuilder.setColor(Color.YELLOW);

        if (menuOption == 1) {
            int timezoneSelection = Menu.showMenu(ctx, "Which is your preferred timezone?", UserSettings.timezones) - 1;
            String selection = UserSettings.timezones[timezoneSelection].split(" ")[0];
            embedBuilder.addField("Old Timezone:", userSettings.timezone.getId(), false);
            embedBuilder.addField("New Timezone:", selection, false);
            userSettings.timezone = ZoneId.of(selection);
        } else if (menuOption == 2) {
            int languageSelection = Menu.showMenu(ctx, "What language would you like me to talk to you in?", UserSettings.languages) - 1;
            String selection = UserSettings.languages[languageSelection].split(" ")[0];
            embedBuilder.addField("Old Language:", UserSettings.languages[userSettings.language.getId()], false);
            embedBuilder.addField("New Language:", UserSettings.languages[languageSelection], false);
            userSettings.language = Languages.valueOf(selection);
        } else {
            ctx.send("What would you like me to call you?\n(Type `clear` to remove your custom name)");
            AtomicInteger attempts = new AtomicInteger();
            String newName;
            while (true) {
                if (attempts.incrementAndGet() >= 4)
                    throw new CommandException("Exiting name edit due because of too many failed attempts.");

                newName = getNextMessage(ctx);
                if (newName.toLowerCase().equals("clear")) {
                    newName = "";
                    break;
                }
                try {
                    int length = newName.length();
                    if (length < 2 || 40 < length)
                        throw new CommandException("Names must be between 2 and 40 characters");

                    if (!newName.matches("^[a-zA-Z0-9 ]*$"))
                        throw new CommandException("Names must only contain alphanumeric characters (a-Z & 0-9)!");

                    if (!newName.equals(newName.replaceAll(" +", " ")))
                        throw new CommandException("Names can't have multiple spaces!");
                } catch (CommandException error) {
                    ctx.send("That won't work, please try again.\n`" + error.getMessage() + "`");
                    continue;
                }
                break;
            }

            embedBuilder.addField("Old Preferred Name:", UserSettings.getName(ctx), false);
            embedBuilder.addField("New Preferred Name:", newName, false);
            userSettings.preferredName = newName;
        }

        if (sendConfirmation(ctx, embedBuilder.build())) {
            userSettings.save();
            ctx.send("Settings edited!");
        } else {
            ctx.send("Ok I won't edit any settings.");
        }
    }

}
