package sh.niall.misty.cogs;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.EmbedBuilder;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import sh.niall.misty.Misty;
import sh.niall.misty.utils.playlists.PlaylistUtils;
import sh.niall.misty.utils.misty.MistyCog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Group;
import sh.niall.yui.commands.interfaces.GroupCommand;
import sh.niall.yui.exceptions.CogException;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;

import java.util.List;

public class Social extends MistyCog {

    MongoCollection<Document> db = Misty.database.getCollection("social");

    @Group(name = "bio")
    public void _commandInfo(Context ctx) throws CogException {
        // Make sure a sub command didn't run!
        if (ctx.didSubCommandRun())
            return;

        // Work out the target
        long target = ctx.getAuthor().getIdLong();
        if (!ctx.getArgsStripped().isEmpty()) {
            String possibleTarget = ctx.getArgsStripped().get(0).replace("<@!", "").replace("<@", "").replace(">", "");
            int length = possibleTarget.length();
            if (15 <= length && length <= 21 && possibleTarget.matches("\\d+"))
                target = Long.parseLong(possibleTarget);
        }

        // Get their bio
        Document document = db.find(Filters.eq("_id", target)).first();
        if (document == null) {
            if (target == ctx.getAuthor().getIdLong())
                throw new CogException("You don't have a bio set!");
            else
                throw new CogException(String.format("%s doesn't have a bio set!", PlaylistUtils.getTargetName(ctx, target)));
        }

        // Post the bio
        ctx.send(document.getString("bio"));
    }

    @GroupCommand(group = "bio", name = "set")
    public void _commandSet(Context ctx) throws CommandException, WaiterException {
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide a bio to set, or run the clear sub command to remove you current bio.");

        // Get their new bio
        String newBio = ctx.getContent();
        newBio = StringUtils.replaceOnce(newBio, ctx.getPrefix(), "");
        newBio = StringUtils.replaceOnceIgnoreCase(newBio, ctx.getCommandWord(), "");
        newBio = StringUtils.replaceOnceIgnoreCase(newBio, ctx.getSubCommandWord(), "");
        newBio = StringUtils.replace(newBio, "\n", " \n ").trim();

        // Validate their new bio
        int length = newBio.length();
        if (length < 3 || 500 < length)
            throw new CommandException("Bios must be between 3 and 500 characters!");
        if (StringUtils.countMatches(newBio, "\n") > 10)
            throw new CommandException("Bios must have no more than 10 new lines!");

        // Add < > to any url
        StringBuilder stringBuilder = new StringBuilder();
        for (String word : newBio.split(" ")) {
            // Urls need to be at least 10 letters long
            if (word.length() < 10) {
                stringBuilder.append(word).append(" ");
                continue;
            }

            // See if the word has a URL
            List<Url> detected = new UrlDetector(word, UrlDetectorOptions.Default).detect();
            if (detected.isEmpty()) {
                stringBuilder.append(word).append(" ");
                continue;
            }

            // See if we just have a bare url
            String url = detected.get(0).getFullUrl();
            if (word.length() == url.length()) {
                stringBuilder.append("<").append(url).append(">").append(" ");
                continue;
            }

            // Find the index
            int startIndex = word.indexOf(url);
            int endIndex = startIndex + url.length();

            // Look for <
            if (startIndex == 0) {
                word = "<" + word;
            } else {
                if (word.charAt(startIndex - 1) != '<') {
                    word = new StringBuilder(word).insert(startIndex - 1, "<").toString();
                }
            }

            // Handle >
            if (endIndex == word.length() - 1) {
                word = word + ">";
            } else {
                if (word.charAt(endIndex + 1) != '>') {
                    word = new StringBuilder(word).insert(endIndex + 1, ">").toString();
                }
            }
            stringBuilder.append(word).append(" ");
        }

        // Create the new document
        Document updatedDocument = new Document().append("_id", ctx.getAuthor().getIdLong()).append("bio", stringBuilder.toString().replace(" \n ", "\n"));

        // Insert if new
        Document document = db.find(Filters.eq("_id", ctx.getAuthor().getIdLong())).first();
        if (document == null) {
            db.insertOne(updatedDocument);
            ctx.send("Bio created!");
        } else {
            // Confirm with the user
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Bio Update!");
            embedBuilder.setDescription("Are you sure you want to update your bio?");
            embedBuilder.addField("Old Bio:", document.getString("bio"), false);
            embedBuilder.addField("New Bio:", newBio, false);
            if (!sendConfirmation(ctx, embedBuilder.build())) {
                ctx.send("I won't update your bio!");
                return;
            }

            // Update the bio
            db.updateOne(Filters.eq("_id", ctx.getAuthor().getIdLong()), new Document("$set", updatedDocument));
            ctx.send("Bio Updated!");
        }
    }

    @GroupCommand(group = "bio", name = "clear")
    public void _commandClear(Context ctx) throws CommandException, WaiterException {
        // Check they have a bio
        Document document = db.find(Filters.eq("_id", ctx.getAuthor().getIdLong())).first();
        if (document == null)
            throw new CommandException("You have no bio to clear!");

        // Confirm with the user
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Bio Clear!");
        embedBuilder.setDescription("Are you sure you want to delete your bio?");
        embedBuilder.addField("Current Bio:", document.getString("bio"), false);
        if (!sendConfirmation(ctx, embedBuilder.build())) {
            ctx.send("I won't delete your bio!");
            return;
        }

        // Delete it!
        db.deleteOne(Filters.eq("_id", ctx.getAuthor().getIdLong()));
        ctx.send("Bio deleted!");
    }
}
