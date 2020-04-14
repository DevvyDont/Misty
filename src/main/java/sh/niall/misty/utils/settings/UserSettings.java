package sh.niall.misty.utils.settings;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import sh.niall.misty.Misty;
import sh.niall.yui.cogs.commands.context.Context;
import sh.niall.yui.exceptions.CommandException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UserSettings {
    final static public String[] timezones = new String[]{
            "Asia/Dubai (UTC +4)",
            "Europe/Paris (UTC +1)",
            "UTC (Default Time)",
            "Europe/London (GMT/BST)",
            "America/New_York (ET/EST)",
            "America/Chicago (Central)",
            "America/Los_Angeles (Pacific)"
    };
    final static public String[] languages = new String[]{
            "English \uD83C\uDDEC\uD83C\uDDE7/\uD83C\uDDFA\uD83C\uDDF8"
    };
    final static DateTimeFormatter shortDateUS = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    final static DateTimeFormatter shortDateUK = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    final static DateTimeFormatter shortDateTimeUS = DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm a");
    final static DateTimeFormatter shortDateTimeUK = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm");
    final static DateTimeFormatter longDateUS = DateTimeFormatter.ofPattern("MMMM dd yyyy");
    final static DateTimeFormatter longDateUK = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    final static DateTimeFormatter longDateTimeUS = DateTimeFormatter.ofPattern("MMMM dd yyyy - hh:mm a");
    final static DateTimeFormatter longDateTimeUK = DateTimeFormatter.ofPattern("dd MMMM yyyy - HH:mm");
    final static List<String> usTimezones = new ArrayList<>(Arrays.asList("America/New_York", "America/Chicago", "America/Los_Angeles"));

    private MongoCollection<Document> db = Misty.database.getCollection("users");
    private Document originalDocument = null;
    private long userId;

    public ZoneId timezone = ZoneId.of("UTC");
    public Languages language = Languages.English;
    public String preferredName = "";

    public UserSettings(Context ctx) throws CommandException {
        this(ctx.getAuthor().getIdLong());
    }

    public UserSettings(long userId) throws CommandException {
        this.userId = userId;
        Document document = db.find(Filters.eq("userId", userId)).first();
        if (document != null) {
            originalDocument = document;
            timezone = ZoneId.of(document.getString("timezone"));
            language = Languages.valueOf(document.getString("language"));
            preferredName = document.getString("preferredName");
        } else {
            if (Misty.yui.getJda().getUserById(userId) == null)
                throw new CommandException("User does not exist!");
            save();
        }
    }

    public String getShortDate(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(timezone).format((usTimezones.contains(timezone.getId()) ? shortDateUS : shortDateUK));
    }

    public String getShortDateTime(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(timezone).format((usTimezones.contains(timezone.getId()) ? shortDateTimeUS : shortDateTimeUK));
    }

    public String getLongDate(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(timezone).format((usTimezones.contains(timezone.getId()) ? longDateUS : longDateUK));
    }

    public String getLongDateTime(long timestamp) {
        return Instant.ofEpochSecond(timestamp).atZone(timezone).format((usTimezones.contains(timezone.getId()) ? longDateTimeUS : longDateTimeUK));
    }

    public UserSettings save() {
        Document document = new Document();
        if (originalDocument == null) {
            document.append("userId", userId);
            document.append("timezone", timezone.getId());
            document.append("language", language.name());
            document.append("preferredName", preferredName);
            db.insertOne(document);
        } else {
            if (!timezone.getId().equals(originalDocument.getString("timezone")))
                document.append("timezone", timezone.getId());

            if (!language.name().equals(originalDocument.getString("language")))
                document.append("language", language.name());

            if (!preferredName.equals(originalDocument.getString("preferredName")))
                document.append("preferredName", preferredName);

            if (!document.isEmpty())
                db.updateOne(Filters.eq("userId", userId), new Document("$set", document));
        }
        return this;
    }

    public static String getName(Context ctx) {
        return getName(ctx, ctx.getAuthor().getIdLong());
    }

    public static String getName(Context ctx, long targetId) {
        return getName(ctx.getGuild(), targetId);
    }

    public static String getName(Guild guild, long targetId) {
        try {
            UserSettings userSettings = new UserSettings(targetId);
            if (!userSettings.preferredName.isEmpty())
                return userSettings.preferredName;

            Member member = guild.getMemberById(targetId);
            if (member != null)
                return member.getEffectiveName();

            return guild.getJDA().getUserById(targetId).getName();
        } catch (CommandException ignored) {
            return "Unknown User ( " + targetId + ")";
        }
    }

    public static String getName(long targetId) {
        try {
            UserSettings userSettings = new UserSettings(targetId);
            if (!userSettings.preferredName.isEmpty())
                return userSettings.preferredName;

            return Misty.yui.getJda().getUserById(targetId).getName();
        } catch (CommandException ignored) {
            return "Unknown User ( " + targetId + ")";
        }
    }

    public static String getAvatarUrl(Context ctx, long userId) {
        return getAvatarUrl(ctx.getJda(), userId);
    }

    public static String getAvatarUrl(JDA jda, long userId) {
        User user = jda.retrieveUserById(userId).complete();
        return (user == null) ? null : user.getEffectiveAvatarUrl();
    }
}
