package sh.niall.misty.cogs;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;
import org.bson.types.ObjectId;
import sh.niall.misty.Misty;
import sh.niall.misty.utils.misty.MistyCog;
import sh.niall.misty.utils.reminders.HumanDateConverter;
import sh.niall.misty.utils.reminders.RemindersPaginator;
import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.misty.utils.ui.Helper;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Group;
import sh.niall.yui.commands.interfaces.GroupCommand;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;
import sh.niall.yui.tasks.interfaces.Loop;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Reminders extends MistyCog {

    private MongoCollection<Document> db = Misty.database.getCollection("reminders");

    @Group(name = "remind", aliases = {"reminders"})
    public void _commandGroup(Context ctx) throws CommandException, WaiterException {
        if (ctx.didSubCommandRun())
            return;

        if (db.count(Filters.eq("author", ctx.getAuthor().getIdLong())) >= 20)
            throw new CommandException("You can have up to 20 reminders at a time!");

        // Understand the request
        UserSettings userSettings = new UserSettings(ctx);
        ZonedDateTime remindAt = new HumanDateConverter(userSettings, String.join(" ", ctx.getArgsStripped())).toZonedDateTime();
        ZonedDateTime now = ZonedDateTime.now(userSettings.timezone);
        if (remindAt.toEpochSecond() < now.toEpochSecond())
            throw new CommandException("Please specify a date in the future, I can't change the past!");
        Duration duration = Duration.between(now, remindAt);

        // Create the embed
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Set reminder?");
        embedBuilder.setDescription("I'll ping you in this channel or in DM's when it's time.");
        embedBuilder.addField("Remind Time:", userSettings.getLongDateTime(remindAt.toEpochSecond()), true);
        embedBuilder.addField("Duration until:", durationToString(duration), false);
        embedBuilder.setFooter(String.format("Date/Time is shown in your set timezone (%s)", userSettings.timezone.getId()));

        // Ignore if they change their mind
        if (!sendConfirmation(ctx, embedBuilder.build())) {
            ctx.send("Okay! I won't remind you.");
            return;
        }

        // Insert to the db
        Document document = new Document();
        document.put("author", ctx.getAuthor().getIdLong());
        document.put("timestamp", remindAt);
        document.put("url", ctx.getMessage().getJumpUrl());
        if (ctx.getChannel().getType() != ChannelType.PRIVATE) {
            document.put("guild", ctx.getGuild().getIdLong());
            document.put("channel", ctx.getChannel().getIdLong());
        }
        db.insertOne(document);
        ctx.send("Ok, I'll remind you!");
    }

    @GroupCommand(group = "remind", name = "list")
    public void _commandList(Context ctx) throws CommandException, WaiterException {
        Map<EmbedBuilder, Document> pages = new HashMap<>();

        // Get all the reminders
        for (Document document : db.find(Filters.eq("author", ctx.getAuthor().getIdLong()))) {
            LocalDateTime remindDT = LocalDateTime.ofEpochSecond(document.getLong("timestamp"), 0, ZoneOffset.UTC);
            Duration duration = Duration.between(LocalDateTime.now(), remindDT);

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Your reminders", document.getString("url"));
            embedBuilder.setDescription("Time is in UTC.");
            embedBuilder.setAuthor(UserSettings.getName(ctx), null, ctx.getUser().getEffectiveAvatarUrl());
            embedBuilder.addField("Date:", remindDT.format(DateTimeFormatter.ofPattern("EEE dd MMMM yyyy")), true);
            embedBuilder.addField("Time:", remindDT.format(DateTimeFormatter.ofPattern("hh:mm:ss a")), true);
            embedBuilder.addField("Duration:", durationToString(duration), false);
            pages.put(embedBuilder, document);
        }

        // Check to see if they have any reminders
        if (pages.isEmpty())
            throw new CommandException("You currently have no reminders!");

        // Run the paginator
        RemindersPaginator paginator = new RemindersPaginator(ctx, pages, 160, this);
        paginator.run();
    }

    @Loop(seconds = 10)
    public void checkLoop() {
        for (Document document : db.find(Filters.lte("timestamp", Instant.now().getEpochSecond()))) {
            // Build the message
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Reminder!");
            embedBuilder.setDescription("I'm reminding you about this message:");
            embedBuilder.addField("Message link:", document.getString("url"), false);
            embedBuilder.setColor(Helper.randomColor());
            MessageBuilder messageBuilder = new MessageBuilder();
            messageBuilder.setContent(String.format("<@%s>", document.getLong("author")));
            messageBuilder.setEmbed(embedBuilder.build());

            // Try to send to the Guild Channel first
            if (document.containsKey("guild")) {
                try {
                    // Get the Guild
                    Guild guild = getYui().getJda().getGuildById(document.getLong("guild"));
                    if (guild == null)
                        throw new Exception();

                    // Get the channel
                    TextChannel channel = guild.getTextChannelById(document.getLong("channel"));
                    if (channel == null)
                        throw new Exception();

                    // Get the member list and check our user is there
                    List<Member> memberList = new ArrayList<>(channel.getMembers());
                    memberList.removeIf(member -> member.getIdLong() != document.getLong("author"));
                    if (memberList.isEmpty())
                        throw new Exception();

                    // Send it and delete
                    channel.sendMessage(messageBuilder.build()).complete();
                    deleteDoc(document);
                    return;
                } catch (Exception ignored) {
                }
            }
            try {
                getYui().getJda().getUserById(document.getLong("author")).openPrivateChannel().complete().sendMessage(messageBuilder.build());
            } catch (Exception ignored) {
                deleteDoc(document);
            }
        }
    }

    /**
     * Deletes a document from the database
     *
     * @param document The document to delete
     */
    private void deleteDoc(Document document) {
        db.deleteOne(Filters.eq("_id", document.get("_id", ObjectId.class)));
    }

    /**
     * Turns a duration object into a string.
     *
     * @param duration The duration to convert
     * @return The output string
     */
    private String durationToString(Duration duration) {
        String output = "";
        output += (duration.toDays() != 0) ? String.format("%s %s, ", duration.toDays(), Helper.singularPlural((int) duration.toDays(), "Day", "Days")) : "";
        output += (duration.toHoursPart() != 0) ? String.format("%s %s, ", duration.toHoursPart(), Helper.singularPlural(duration.toHoursPart(), "Hour", "Hours")) : "";
        output += (duration.toMinutesPart() != 0) ? String.format("%s %s, ", duration.toMinutesPart(), Helper.singularPlural(duration.toMinutesPart(), "Minute", "Minutes")) : "";
        output += (duration.toSecondsPart() != 0) ? String.format("%s %s, ", duration.toSecondsPart(), Helper.singularPlural(duration.toSecondsPart(), "Second", "Seconds")) : "";
        return output;
    }

    /**
     * Called by the paginator to prompt the user if they want to delete a reminder and delete it if so.
     *
     * @param ctx      The current context
     * @param document The document to delete
     * @return If the delete was successful
     */
    public boolean attemptDelete(Context ctx, Document document) {
        //
        LocalDateTime remindDT = LocalDateTime.ofEpochSecond(document.getLong("timestamp"), 0, ZoneOffset.UTC);

        // Create the embed
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Are you sure you want to delete this reminder?", document.getString("url"));
        embedBuilder.setDescription("Time is in UTC.");
        embedBuilder.setAuthor(UserSettings.getName(ctx), null, ctx.getUser().getEffectiveAvatarUrl());
        embedBuilder.addField("Date:", remindDT.format(DateTimeFormatter.ofPattern("EEE dd MMMM yyyy")), true);
        embedBuilder.addField("Time:", remindDT.format(DateTimeFormatter.ofPattern("hh:mm:ss a")), true);
        embedBuilder.addField("Duration:", durationToString(Duration.between(LocalDateTime.now(), remindDT)), false);
        embedBuilder.setColor(Color.YELLOW);

        // Prompt if they want to delete. If anything but a yes, just ignore the delete reaction
        try {
            if (sendConfirmation(ctx, embedBuilder.build())) {
                deleteDoc(document);
                return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
