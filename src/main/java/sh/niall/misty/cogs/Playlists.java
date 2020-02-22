package sh.niall.misty.cogs;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import org.bson.Document;
import org.bson.types.ObjectId;
import sh.niall.misty.Misty;
import sh.niall.misty.audio.AudioGuildManager;
import sh.niall.misty.playlists.PlaylistUtils;
import sh.niall.misty.playlists.SongCache;
import sh.niall.misty.utils.cogs.MistyCog;
import sh.niall.misty.utils.ui.Menu;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Group;
import sh.niall.yui.commands.interfaces.GroupCommand;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Playlists extends MistyCog {

    private MongoCollection<Document> db = Misty.database.getCollection("playlists");
    private AudioGuildManager audioGuildManager;
    private SongCache songCache;

    // Default values
    private int maxPlaylists = 10;
    private int playlistSongLimit = 150;
    private int maxEditors = 3;

    public Playlists(AudioGuildManager audioGuildManager, SongCache songCache) {
        this.audioGuildManager = audioGuildManager;
        this.songCache = songCache;
    }

    @Group(name = "playlist", aliases = {"pl"})
    public void _commandPlaylist(Context ctx) throws CommandException {
        if (!ctx.didSubCommandRun())
            throw new CommandException("No sub command ran, please see help for a list of playlist commands");
    }

    @GroupCommand(group = "playlist", name = "create", aliases = {"c"})
    public void _commandCreate(Context ctx) throws CommandException {
        // Get all of the users playlists, ensure they have less than 10
        if (db.count(Filters.eq("author", ctx.getAuthor().getIdLong())) >= maxPlaylists)
            throw new CommandException("You can only have a total of " + maxPlaylists + " playlists!");

        // Merge the name
        String name = String.join(" ", ctx.getArgsStripped());
        String lowerName = name.toLowerCase();

        // Validate the name
        PlaylistUtils.validateName(name);

        // Check the playlist doesn't already exist
        if (db.find(Filters.eq("searchName", lowerName)).first() != null)
            throw new CommandException("Playlist " + name + " already exits");

        // Create the playlist
        Document document = new Document();
        document.append("author", ctx.getAuthor().getIdLong());
        document.append("friendlyName", name);
        document.append("searchName", lowerName);
        document.append("image", "");
        document.append("desc", "");
        document.append("editors", new ArrayList<Long>());
        document.append("plays", 0);
        document.append("songList", new ArrayList<String>());
        db.insertOne(document);

        // Inform the user
        ctx.send("Playlist " + name + " created!");
    }

    @GroupCommand(group = "playlist", name = "delete", aliases = {"d"})
    public void _commandDelete(Context ctx) throws CommandException, WaiterException {
        // Get the playlist name
        String name = String.join(" ", ctx.getArgsStripped());
        String lowerName = name.toLowerCase();

        // Validate the playlist name
        PlaylistUtils.validateName(name);

        // Search for it
        Document document = db.find(Filters.and(
                Filters.eq("author", ctx.getAuthor().getIdLong()),
                Filters.eq("searchName", lowerName)
        )).first();
        if (document == null)
            throw new CommandException("Can't delete " + name + " as it doesn't exist!");

        // Convert editors to real names
        List<String> editors = new ArrayList<>();
        for (Long editor : (List<Long>) document.get("editors")) {
            editors.add(PlaylistUtils.getName(ctx, editor));
        }

        // Validate they want to delete the playlist
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Playlist delete confirm!");
        embedBuilder.setDescription("Are you sure you want to delete the playlist " + name);
        embedBuilder.setAuthor(ctx.getAuthor().getEffectiveName(), null, ctx.getUser().getEffectiveAvatarUrl());
        embedBuilder.addField("Songs:", String.valueOf(((List<String>) document.get("songList")).size()), true);
        if (!editors.isEmpty())
            embedBuilder.addField("Editors:", String.join("\n", editors), true);
        embedBuilder.addField("Plays:", String.valueOf(document.getInteger("plays")), true);
        boolean result = sendConfirmation(ctx, embedBuilder.build());

        // Handle the result
        if (result) {
            // If they said yes, delete it!
            db.deleteOne(Filters.eq("_id", document.get("_id", ObjectId.class)));
            ctx.send("Playlist `" + name + "` deleted!");
        } else {
            ctx.send("Playlist delete canceled");
        }
    }

    @GroupCommand(group = "playlist", name = "edit", aliases = {"e"})
    public void _commandEdit(Context ctx) throws CommandException, WaiterException {
        // Get the playlist name
        String name = String.join(" ", ctx.getArgsStripped());
        String lowerName = name.toLowerCase();

        // Validate the playlist name
        PlaylistUtils.validateName(name);

        // Get the playlist from the database
        Document document = db.find(Filters.and(Filters.eq("author", ctx.getAuthor().getIdLong()), Filters.eq("searchName", lowerName))).first();
        if (document == null)
            throw new CommandException("Playlist " + name + " does not exist!");

        // Setup the update information
        Document newDocument = new Document();
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Edit Conformation")
                .setDescription("Are you sure you want to make these changes to the playlist " + document.getString("friendlyName") + " ?")
                .setColor(Color.ORANGE)
                .setAuthor(ctx.getAuthor().getEffectiveName(), null, ctx.getUser().getEffectiveAvatarUrl());

        // Present the menu and ask what they want.
        int menuOption = Menu.showMenu(
                ctx,
                "What would you like to edit?",
                new String[]{
                        "Playlist Name",
                        "Playlist Description",
                        "Playlist Image",
                        "Add Editor",
                        "Remove Editor",
                        "Change Ownership",
                }
        );

        // Since all the other logic is done in a loop, handle the prompts first
        if (menuOption == 1)
            ctx.send("What would you like to rename the playlist to?");
        else if (menuOption == 2)
            ctx.send("What would you like the playlist description to be?\n(Reply with `clear` to remove the current description)");
        else if (menuOption == 3)
            ctx.send("What would you like the playlist image to be?\n(Reply with `clear` to remove the current image)");
        else if (menuOption == 4) {
            // Validate we can do this
            if (((List<Long>) document.get("editors")).size() >= maxEditors)
                throw new CommandException("You can't have more than 3 editors! Please remove an editor from the playlist first.");
            ctx.send("Who would you like to add to the playlist editor list?");
        } else if (menuOption == 5) {
            // Validate we have editors
            if (((List<Long>) document.get("editors")).size() == 0)
                throw new CommandException("You don't have any playlist editors, so you can't remove any!");
            ctx.send("What would you like the playlist description to be?\n(Reply with `clear` to remove the current description)");
        } else if (menuOption == 6)
            ctx.send("Who would you like the new owner to be?");
        else
            throw new CommandException("Unknown menu error occurred!");

        // Using Atomic to keep track of how many iterations
        AtomicInteger attempts = new AtomicInteger(0);
        while (true) {
            if (attempts.incrementAndGet() == 4)
                throw new CommandException("Exiting edit, you've failed too many attempts.");

            // All need a new message, so listen for it
            String newMessage = getNextMessage(ctx);
            if (newMessage == null)
                throw new CommandException("Exiting edit, you ran out of time!");

            if (menuOption == 1) {
                // First up, renaming
                // Validate the name and loop again if it isn't valid
                try {
                    if (document.getString("friendlyName").equals(newMessage))
                        throw new CommandException("Old and new name matches!");

                    PlaylistUtils.validateName(newMessage);
                } catch (CommandException error) {
                    ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
                    continue;
                }

                // Input was valid, add to the conformation and update the document
                embedBuilder.addField("Old Name:", document.getString("friendlyName"), true);
                embedBuilder.addField("New Name:", newMessage, true);
                newDocument.append("friendlyName", newMessage);
                newDocument.append("searchName", newMessage.toLowerCase());
                break;

            } else if (menuOption == 2) {
                //  Next up descriptions
                // The user can provide clear here, so validate if they don't
                if (newMessage.toLowerCase().equals("clear"))
                    newMessage = "";
                else {
                    try {
                        if (document.getString("desc").equals(newMessage))
                            throw new CommandException("Old and new description match!");

                        if (!newMessage.matches("^[a-zA-Z0-9 ]*$"))
                            throw new CommandException("Playlist name must only contain alphanumeric characters (a-Z & 0-9)!");

                        if (!name.equals(name.replaceAll(" +", " ")))
                            throw new CommandException("Playlist descriptions can't have multiple spaces per word!");

                        if (name.startsWith(" "))
                            throw new CommandException("Playlist description can't start with a space!");

                        if (name.length() > 150)
                            throw new CommandException("Playlist descriptions can't be longer than 150 characters");

                    } catch (CommandException error) {
                        ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
                        continue;
                    }
                }

                // Input was valid, add to the conformation and update the document
                embedBuilder.addField("Old Description:", document.getString("desc"), false);
                embedBuilder.addField("New Description:", newMessage, false);
                newDocument.append("desc", newMessage);
                break;
            } else if (menuOption == 3) {
                //  Next up Image
                // The user can provide clear here, so validate if they don't
                if (newMessage.toLowerCase().equals("clear"))
                    newMessage = "";
                else {
                    try {
                        if (document.getString("image").equals(newMessage))
                            throw new CommandException("Old and new images match!");

                        try {
                            new URL(newMessage);
                        } catch (MalformedURLException error) {
                            throw new CommandException("URL isn't valid!");
                        }
                        String[] splits = newMessage.split("\\.");
                        String suffix = splits[splits.length - 1];
                        if (!(suffix.equals("png") || suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("gif")))
                            throw new CommandException("Playlist images must be a png, jpeg, jpg or gif");

                    } catch (CommandException error) {
                        ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
                        continue;
                    }
                }

                // Input was valid, add to the conformation and update the document
                embedBuilder.addField("New Image:", "Please verify the image is displaying correctly.", true);
                embedBuilder.setThumbnail(newMessage);
                newDocument.append("image", newMessage);
                break;
            } else if (menuOption == 4) {
                // Get the new editor
                String newEditor = newMessage.split(" ")[0].replace("<@", "").replace("!", "").replace(">", "");
                List<Long> editorList = (List<Long>) document.get("editors");
                try {
                    if (!newEditor.matches("\\d+"))
                        throw new CommandException("Invalid user! Please provide a valid user to add as an editor.");

                    long newEditorLong = Long.parseLong(newEditor);
                    String newEditorName = PlaylistUtils.getName(ctx, newEditorLong);
                    if (newEditorName.equals("Unknown User (" + newEditorLong + ")"))
                        throw new CommandException("I don't know who that is! Please make sure the editor you're trying to add is in this server.");

                    if (editorList.contains(newEditorLong))
                        throw new CommandException(newEditorName + " is already an editor!");

                    // Input was valid, add to the conformation and update the document
                    editorList.add(newEditorLong);
                    embedBuilder.addField("New editor:", newEditorName, false);
                    embedBuilder.addField("WARNING:", "Editors can Add and Remove songs from a playlist. Please make sure you trust who you're adding.", false);
                    embedBuilder.setColor(Color.RED);
                    newDocument.append("editors", editorList);
                    break;
                } catch (CommandException error) {
                    ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
                }
            } else if (menuOption == 5) {
                // Get the editor to remove
                String editorToRemove = newMessage.split(" ")[0].replace("<@", "").replace("!", "").replace(">", "");
                List<Long> editorList = (List<Long>) document.get("editors");
                try {
                    if (!editorToRemove.matches("\\d+"))
                        throw new CommandException("Invalid user! Please provide a valid user to add as an editor.");

                    long toRemoveLong = Long.parseLong(editorToRemove);
                    String toRemoveName = PlaylistUtils.getName(ctx, toRemoveLong);

                    if (!editorList.contains(toRemoveLong))
                        throw new CommandException(toRemoveName + " is not an editor for this playlist.");

                    // Input was valid, add to the conformation and update the document
                    editorList.remove(toRemoveLong);
                    embedBuilder.addField("Removing editor:", toRemoveName, false);
                    embedBuilder.setColor(Color.RED);
                    newDocument.append("editors", editorList);
                    break;
                } catch (CommandException error) {
                    ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
                }
            } else if (menuOption == 6) {
                // Change ownership time.. Oh boy
                String newOwner = newMessage.split(" ")[0].replace("<@", "").replace("!", "").replace(">", "");
                try {
                    if (!newOwner.matches("\\d+"))
                        throw new CommandException("Invalid user! Please provide a valid user to change ownership to.");

                    long newOwnerLong = Long.parseLong(newOwner);
                    String newOwnerName = PlaylistUtils.getName(ctx, newOwnerLong);

                    if (newOwnerName.equals("Unknown User (" + newOwnerLong + ")"))
                        throw new CommandException("I don't know who that is! Please make sure the new owner is in this server.");

                    if (db.count(Filters.eq("author", newOwnerLong)) >= maxPlaylists)
                        throw new CommandException(newOwnerName + " already has the maximum amount of playlists!");

                    if (db.find(Filters.and(Filters.eq("author", newOwnerLong), Filters.eq("searchName", document.getString("searchName")))).first() != null)
                        throw new CommandException(newOwnerName + " already has a playlist called " + document.getString("friendlyName"));

                    // Input was valid, add to the conformation and update the document
                    embedBuilder.addField("New Owner:", newOwnerName, false);
                    embedBuilder.addField("WARNING:", "You will lose access to this playlist and the playlist editors will be reset!", false);
                    embedBuilder.setColor(Color.RED);
                    newDocument.append("editors", new ArrayList<Long>());
                    newDocument.append("author", newOwnerLong);

                    // Unlike the others, we now want to ask the target if they agree
                    EmbedBuilder targetEmbed = new EmbedBuilder()
                            .setTitle("Playlist Transfer")
                            .setDescription("Would you like to take ownership of the `" + document.getString("friendlyName") + "` playlist?")
                            .setColor(Color.ORANGE)
                            .setAuthor(newOwnerName, null, ctx.getBot().getUserById(newOwnerLong).getEffectiveAvatarUrl());

                    boolean targetDecision;
                    try {
                        targetDecision = sendConfirmation(ctx, targetEmbed.build(), newOwnerLong);
                    } catch (CommandException error) {
                        ctx.send("Exiting edit, you ran out of time!");
                        return;
                    }

                    if (!targetDecision) {
                        ctx.send("Playlist edit canceled!");
                        return;
                    }
                    break;
                } catch (CommandException error) {
                    ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
                }
            }
        }

        // Confirm and edit
        if (sendConfirmation(ctx, embedBuilder.build())) {
            db.updateOne(Filters.eq("_id", document.get("_id", ObjectId.class)), new Document("$set", newDocument));
            ctx.send("Playlist edited!");
        } else {
            ctx.send("Playlist edit canceled!");
        }
    }

    @GroupCommand(group = "playlist", name = "add", aliases = {"a"})
    public void _commandAdd(Context ctx) throws CommandException {
        // >pl add 43280943820438 iojdasjiaidsjadojads url url url url url
        if (ctx.getArgsStripped().size() == 0)
            throw new CommandException("Please provide a playlist name to add a song to");
        List<String> args = new ArrayList<>(ctx.getArgsStripped());

        // First let's work out our target
        long targetId = ctx.getAuthor().getIdLong();
        String possibleTarget = args.get(0).replace("<@", "").replace("!", "").replace(">", "");
        if (possibleTarget.matches("\\d+"))
            targetId = 





    }

    private String getNextMessage(Context ctx) throws WaiterException {
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
