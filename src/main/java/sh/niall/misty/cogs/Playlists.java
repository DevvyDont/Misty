package sh.niall.misty.cogs;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import sh.niall.misty.Misty;
import sh.niall.misty.audio.AudioGuildManager;
import sh.niall.misty.playlists.PlaylistUtils;
import sh.niall.misty.playlists.SongCache;
import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Command;
import sh.niall.yui.commands.interfaces.Group;
import sh.niall.yui.commands.interfaces.GroupCommand;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Playlists extends Cog {

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

    @GroupCommand(group = "playlist", name = "create")
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

    @GroupCommand(group = "playlist", name = "delete")
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
        embedBuilder.addField("Songs:", String.valueOf(((List<String>) document.get("songList")).size()), false);
        embedBuilder.addField("Editors:", String.join("\n", editors), false);
        embedBuilder.addField("Plays:", String.valueOf(document.getInteger("plays")), false);

        // Send the message
        Message question = ctx.send(embedBuilder.build());
        question.addReaction("✅").complete();
        question.addReaction("❌").complete();

        // Wait for the response
        GuildMessageReactionAddEvent reactionAddEvent = (GuildMessageReactionAddEvent) waitForEvent(
                GuildMessageReactionAddEvent.class, event -> {
                    GuildMessageReactionAddEvent e = (GuildMessageReactionAddEvent) event;
                    return (e.getMember().getIdLong() == ctx.getAuthor().getIdLong()) &&
                            (e.getMessageIdLong() == question.getIdLong()) &&
                            (e.getReactionEmote().getEmoji().equals("✅") || e.getReactionEmote().getEmoji().equals("❌"));
                }, 15, TimeUnit.SECONDS);

        // Delete the question and ignore if they said nothing
        question.delete().queue();
        if (reactionAddEvent == null)
            throw new CommandException("Timed out waiting for deletion conformation");

        // Handle no logic
        if (reactionAddEvent.getReactionEmote().getEmoji().equals("❌")) {
            ctx.send("Playlist delete canceled");
            return;
        }

        // If they said yes, delete it!
        db.deleteOne(Filters.eq("_id", document.get("_id", ObjectId.class)));
        ctx.send("Playlist `" + name + "` deleted!");
    }

    @GroupCommand(group = "playlist", name = "edit")
    public void _commandEdit(Context ctx) throws CommandException, WaiterException {
        // Get the playlist name
        String name = String.join(" ", ctx.getArgsStripped());
        String lowerName = name.toLowerCase();

        // Validate the playlist name
        PlaylistUtils.validateName(name);

        // Get the playlist from the database
        Document document = db.find(Filters.and(
                Filters.eq("author", ctx.getAuthor().getIdLong()),
                Filters.eq("searchName", lowerName)
        )).first();
        if (document == null)
            throw new CommandException("Playlist " + name + "does not exist!");

        // Now we need to ask the user what they want to edit
        ctx.send("What would you like to edit?\n1. Playlist name\n2. Playlist Description\n3. Playlist Image\n4. Playlist Editors\n(Type menu number to select)");
        int menuOption;
        int attempts = 0;
        while (true) {
            if (attempts > 3)
                throw new CommandException("Too many attempts, cancelling menu.");

            GuildMessageReceivedEvent message = (GuildMessageReceivedEvent) waitForEvent(
                    GuildMessageReceivedEvent.class,
                    check -> {
                        GuildMessageReceivedEvent e = (GuildMessageReceivedEvent) check;
                        return (e.getChannel().getIdLong() == ctx.getChannel().getIdLong()) && (e.getAuthor().getIdLong() == ctx.getAuthor().getIdLong());
                    }, 15, TimeUnit.SECONDS
            );
            attempts++;
            if (message == null)
                throw new CommandException("Timed out waiting for your option");

            if (!message.getMessage().getContentRaw().matches("\\d+")) {
                ctx.send("Invalid option, please try again");
                continue;
            }
            menuOption = Integer.parseInt(message.getMessage().getContentRaw());
            if (menuOption < 1 || menuOption > 4) {
                ctx.send("Invalid option, please try again");
                continue;
            }
            break;
        }

        if (menuOption == 1) {
            // Edit playlist name

            ctx.send("Please enter the new playlist name");
            GuildMessageReceivedEvent newNameMessage = (GuildMessageReceivedEvent) waitForEvent(
                    GuildMessageReceivedEvent.class,
                    check -> {
                        GuildMessageReceivedEvent e = (GuildMessageReceivedEvent) check;
                        return (e.getChannel().getIdLong() == ctx.getChannel().getIdLong()) && (e.getAuthor().getIdLong() == ctx.getAuthor().getIdLong());
                    }, 15, TimeUnit.SECONDS
            );
            String newName = newNameMessage.getMessage().getContentRaw();
            PlaylistUtils.validateName(newName);









        } else if (menuOption == 2) {
            // Edit Description
            System.out.println("Editing Desc");
        } else if (menuOption == 3) {
            // Edit Image
            System.out.println("Editing Image");
        } else if (menuOption == 4) {
            // Edit Editors
            System.out.println("Editing Editors");
        }
    }
}
