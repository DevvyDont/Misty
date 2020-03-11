package sh.niall.misty.cogs;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import sh.niall.misty.Misty;
import sh.niall.misty.utils.misty.MistyCog;
import sh.niall.misty.utils.playlists.PlaylistUtils;
import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.misty.utils.tags.Tag;
import sh.niall.misty.utils.ui.Menu;
import sh.niall.misty.utils.ui.paginator.Paginator;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Group;
import sh.niall.yui.commands.interfaces.GroupCommand;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Tags extends MistyCog {

    MongoCollection<Document> db = Misty.database.getCollection("tags");
    final static int maxTagsPerMember = 40;


    @Group(name = "tag", aliases = {"t", "tags"})
    public void _commandTag(Context ctx) throws CommandException {
        // Ignore if sub command was run
        if (ctx.didSubCommandRun())
            return;

        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide a tag to lookup");

        // Find the tag
        String tagName = String.join(" ", ctx.getArgsStripped());
        Tag tag = new Tag(db, ctx.getGuild().getIdLong(), Tag.generateSearchName(tagName));

        // Increase the use counter
        tag.uses++;
        tag.save();

        // Display the tag content
        ctx.send(tag.body);
    }

    @GroupCommand(group = "tag", name = "create", aliases = {"c", "add"})
    public void _commandCreate(Context ctx) throws CommandException, WaiterException {
        // Check we were given an argument
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide the name of the tag you want to create!");

        // Get all of the members tags, ensure they have less than 40
        if (this.db.count(Filters.and(Filters.eq("author", ctx.getAuthor().getIdLong()), Filters.eq("guild", ctx.getGuild().getIdLong()))) >= maxTagsPerMember)
            throw new CommandException(String.format("You can only have a total of %s tags per guild!", maxTagsPerMember));

        // First get the name
        String friendlyName = String.join(" ", ctx.getArgsStripped());
        String searchName = Tag.generateSearchName(friendlyName);

        // Validate the search name
        Tag.validateName(ctx, searchName);

        // Search to see if the tag exists
        if (db.find(Filters.and(Filters.eq("guild", ctx.getGuild().getIdLong()), Filters.eq("searchName", searchName))).first() != null)
            throw new CommandException(String.format("Tag %s already exists!", friendlyName));

        // Get the users next message
        ctx.send("What would you like the content of the tag to be?");
        String body = getNextMessage(ctx);

        // Validate the body
        Tag.validateBody(body);

        // Create the tag
        new Tag(db, ctx.getGuild().getIdLong(), ctx.getAuthor().getIdLong(), friendlyName, body).save();

        // Inform the invoker
        ctx.send(String.format("Tag `%s` created!", friendlyName));
    }

    @GroupCommand(group = "tag", name = "delete", aliases = {"del", "d", "remove"})
    public void _commandDelete(Context ctx) throws CommandException, WaiterException {
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide a tag to delete!");

        // First get the tag
        String friendlyName = String.join(" ", ctx.getArgsStripped());
        Tag tag = new Tag(db, ctx.getGuild().getIdLong(), Tag.generateSearchName(friendlyName));

        // Make sure the invoker is the owner
        if (ctx.getAuthor().getIdLong() != tag.author)
            throw new CommandException("You can't delete this tag as you don't own it!");

        // Create the embed
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setAuthor("Tag Delete");
        embedBuilder.setDescription(String.format("Are you sure you want to delete the %s tag?", friendlyName));
        embedBuilder.setColor(Color.RED);
        embedBuilder.setAuthor(UserSettings.getName(ctx), null, ctx.getUser().getEffectiveAvatarUrl());
        embedBuilder.addField("Content: ", tag.body, false);
        embedBuilder.addField("Created: ", Instant.ofEpochSecond(tag.timestamp).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd MMM yyyy")), true);
        embedBuilder.addField("Uses: ", String.valueOf(tag.uses), true);

        // Delete if confirmed
        if (sendConfirmation(ctx, embedBuilder.build()))
            ctx.send(String.format("Tag `%s` deleted!", tag.delete().friendlyName));
        else
            ctx.send(String.format("Okay! I won't delete tag %s", tag.friendlyName));
    }

    @GroupCommand(group = "tag", name = "edit", aliases = {"e"})
    public void _commandEdit(Context ctx) throws CommandException, WaiterException {
        // Check we were given an argument
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide the name of the tag you want to edit!");

        // Get the tag
        String friendlyName = String.join(" ", ctx.getArgsStripped());
        Tag tag = new Tag(db, ctx.getGuild().getIdLong(), Tag.generateSearchName(friendlyName));

        // Make sure the invoker is the owner
        if (ctx.getAuthor().getIdLong() != tag.author)
            throw new CommandException("You can't edit this tag as you don't own it!");

        int menuOption = Menu.showMenu(
                ctx,
                String.format("What would you like to edit about tag %s?", tag.friendlyName),
                new String[]{
                        "Edit Tag Name",
                        "Edit Tag Content",
                        "Change Ownership"
                }
        );

        // Handle the prompt first since all the logic is put in a loop
        switch (menuOption) {
            case 1:
                ctx.send("What would you like to rename the tag to?");
                break;
            case 2:
                ctx.send("What would you like the tag content to be?");
                break;
            case 3:
                ctx.send("Who would you like the new owner to be?");
                break;
        }

        // Setup the update information
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Edit Conformation")
                .setDescription(String.format("Are you sure you want to make these changes to the tag %s?", tag.friendlyName))
                .setColor(Color.ORANGE)
                .setAuthor(UserSettings.getName(ctx), null, ctx.getUser().getEffectiveAvatarUrl());


        // Using Atomic to keep track of how many iterations
        AtomicInteger attempts = new AtomicInteger(0);
        inputLoop:
        while (true) {
            if (attempts.incrementAndGet() == 4)
                throw new CommandException("Exiting edit, you've failed too many attempts.");

            // All need a new message, so listen for it
            String newMessage = getNextMessage(ctx);
            if (newMessage == null)
                throw new CommandException("Exiting edit, you ran out of time!");

            try {
                switch (menuOption) {
                    case 1:
                        String newSearchName = Tag.generateSearchName(newMessage);
                        if (newSearchName.equals(tag.searchName))
                            throw new CommandException("Old and new tag name is the same!");
                        Tag.validateName(ctx, newSearchName);
                        if (db.find(Filters.and(Filters.eq("guild", ctx.getGuild().getIdLong()), Filters.eq("searchName", newSearchName))).first() != null)
                            throw new CommandException(String.format("Tag %s already exists!", friendlyName));
                        embedBuilder.addField("Old Name:", tag.friendlyName, true);
                        embedBuilder.addField("New Name:", newMessage, true);
                        tag.friendlyName = newMessage;
                        tag.searchName = newSearchName;
                        break inputLoop;
                    case 2:
                        if (newMessage.equals(tag.body))
                            throw new CommandException("Old and new tag content is the same!");
                        Tag.validateBody(newMessage);
                        embedBuilder.addField("Old Content:", tag.body, false);
                        embedBuilder.addField("New Content:", newMessage, false);
                        tag.body = newMessage;
                        break inputLoop;
                    case 3:
                        String newOwner = newMessage.split(" ")[0].replace("<@", "").replace("!", "").replace(">", "");
                        if (!newOwner.matches("\\d+"))
                            throw new CommandException("Invalid user! Please provide a valid user to change ownership to.");

                        long newOwnerLong = Long.parseLong(newOwner);
                        if (ctx.getGuild().getMemberById(newOwnerLong) == null)
                            throw new CommandException("I don't know who that is! Please make sure the new owner is in this server.");

                        if (ctx.getBot().getUserById(newOwnerLong).isBot())
                            throw new CommandException("You can't transfer a tag to a bot!");

                        if (this.db.count(Filters.and(Filters.eq("author", ctx.getAuthor().getIdLong()), Filters.eq("guild", ctx.getGuild().getIdLong()))) >= maxTagsPerMember)
                            throw new CommandException("They already have the maximum amount of tags in this guild!");

                        embedBuilder.addField("New Owner:", UserSettings.getName(ctx, newOwnerLong), false);
                        embedBuilder.addField("WARNING:", "You will lose ownership of this tag!", false);
                        embedBuilder.setColor(Color.RED);
                        tag.author = newOwnerLong;
                        break inputLoop;
                }
            } catch (CommandException error) {
                ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
            }
        }

        if (menuOption == 3) { // Validate new ownership with the new owner
            EmbedBuilder targetEmbed = new EmbedBuilder()
                    .setTitle("Tag Transfer")
                    .setDescription(String.format("Would you like to take ownership of the `%s` tag?", tag.friendlyName))
                    .setColor(Color.ORANGE)
                    .setAuthor(UserSettings.getName(ctx, tag.author), null, ctx.getBot().getUserById(tag.author).getEffectiveAvatarUrl());

            boolean targetDecision;
            try {
                targetDecision = sendConfirmation(ctx, targetEmbed.build(), tag.author);
            } catch (CommandException error) {
                ctx.send("Transfer canceled! New owner took too long to respond.");
                return;
            }
            if (!targetDecision)
                throw new CommandException("Transfer canceled! The new owner declined!");
        }

        // Confirm and edit
        if (sendConfirmation(ctx, embedBuilder.build())) {
            tag.save();
            ctx.send("Tag edited!");
        } else {
            ctx.send("Tag edit canceled!");
        }
    }

    @GroupCommand(group = "tag", name = "info", aliases = {"i"})
    public void _commandInfo(Context ctx) throws CommandException {
        // Check we were given an argument
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide the name of the tag you want to lookup!");

        // Find the tag
        String tagName = String.join(" ", ctx.getArgsStripped());
        Tag tag = new Tag(db, ctx.getGuild().getIdLong(), Tag.generateSearchName(tagName));

        // Create the output
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(StringUtils.capitalize(tag.searchName.toLowerCase()));
        embedBuilder.setColor(Color.PINK);
        embedBuilder.setDescription(String.format(
                "Tag by: %s\nUses: %s\nLength: %s\nCreated: %s\n\n%s",
                UserSettings.getName(ctx, tag.author),
                tag.uses,
                tag.body.length(),
                Instant.ofEpochSecond(tag.timestamp).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                (ctx.getGuild().getMemberById(tag.author) == null) ? "Author is currently not in this discord, tag is claimable!" : ""
        ));
        User user = ctx.getBot().getUserById(tag.author);
        if (user != null)
            embedBuilder.setAuthor(UserSettings.getName(ctx, tag.author), null, user.getEffectiveAvatarUrl());

        // Display the tag content
        ctx.send(embedBuilder.build());
    }

    // TODO: CLEAN THIS DISPLAY UP
    @GroupCommand(group = "tag", name = "list", aliases = {"l"})
    public void _commandList(Context ctx) throws CommandException, WaiterException {
        // If they provided an argument, see if it's a possible target
        long targetId = ctx.getAuthor().getIdLong();
        if (!ctx.getArgsStripped().isEmpty()) {
            String possibleTarget = ctx.getArgsStripped().get(0).replace("<@!", "").replace("<@", "").replace(">", "");
            int length = possibleTarget.length();
            if (15 <= length && length <= 21 && possibleTarget.matches("\\d+")) {
                targetId = Long.parseLong(possibleTarget);
            }
        }

        // Get that users tags in this guild
        List<Tag> tags = new ArrayList<>();
        for (Document document : db.find(Filters.or(Filters.eq("author", targetId), Filters.eq("guild", ctx.getGuild().getIdLong())))) {
            tags.add(new Tag(db, document));
        }
        if (tags.isEmpty())
            throw new CommandException(String.format("%s has no tags!", UserSettings.getName(ctx, targetId)));

        // Getting information for the pages
        List<EmbedBuilder> embedBuilderList = new ArrayList<>();
        EmbedBuilder embedBuilderMaster = new EmbedBuilder();
        User targetUser = ctx.getBot().getUserById(targetId);
        String targetName = UserSettings.getName(ctx, targetId);
        embedBuilderMaster.setTitle(String.format("%s's tags!", targetName));
        embedBuilderMaster.setAuthor(targetName, null, (targetUser != null) ? targetUser.getEffectiveAvatarUrl() : null);
        String baseDesc = String.format("Showing tags they own.\nThey currently have %s tags! %s", tags.size(),
                (ctx.getGuild().getMemberById(targetId) == null) ? "\nAs they're not in this server currently, you can claim their tags." : "");

        // Loop through tags and add them to the desc
        for (List<Tag> tagList : ListUtils.partition(tags, 10)) {
            EmbedBuilder embedBuilder = new EmbedBuilder(embedBuilderMaster);
            StringBuilder stringBuilder = new StringBuilder(baseDesc);
            for (Tag tag : tagList) {
                stringBuilder.append(String.format("- %s\n", tag.searchName));
            }
            embedBuilder.setDescription(stringBuilder.toString());
            embedBuilderList.add(embedBuilder);
        }

        // Run the paginator
        Paginator paginator = new Paginator(ctx, embedBuilderList, 160, false);
        paginator.run();
    }

    @GroupCommand(group = "tag", name = "claim")
    public void _commandClaim(Context ctx) throws CommandException {
        // Check we were given an argument
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide the name of the tag you want to lookup!");

        // Find the tag
        String tagName = String.join(" ", ctx.getArgsStripped());
        Tag tag = new Tag(db, ctx.getGuild().getIdLong(), Tag.generateSearchName(tagName));

        // Check if the author is still in the guild
        if (ctx.getGuild().getMemberById(tag.author) != null)
            throw new CommandException("You can't claim this tag as the owner is still in this server!");

        // Check if the invoker has enough room
        if (this.db.count(Filters.and(Filters.eq("author", ctx.getAuthor().getIdLong()), Filters.eq("guild", ctx.getGuild().getIdLong()))) >= maxTagsPerMember)
            throw new CommandException(String.format("You can only have a total of %s tags per guild!", maxTagsPerMember));

        // Give them the tag
        tag.author = ctx.getAuthor().getIdLong();
        tag.save();

        // Let them know
        ctx.send(String.format("You're now the new owner of the %s tag!", tag.searchName));
    }
}
