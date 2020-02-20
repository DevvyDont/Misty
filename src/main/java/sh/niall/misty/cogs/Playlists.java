package sh.niall.misty.cogs;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
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
import sh.niall.yui.commands.interfaces.Group;
import sh.niall.yui.commands.interfaces.GroupCommand;
import sh.niall.yui.exceptions.CommandException;

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
        if(db.count(Filters.eq("author", ctx.getAuthor().getIdLong())) >= maxPlaylists)
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
    public void _commandDelete(Context ctx) throws CommandException {
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
        getYui().getEventWaiter().waitForNewEvent(GuildMessageReactionAddEvent.class, event -> {
            // Make sure, Reaction on correct message, Reaction came from author, Reaction is valid
            GuildMessageReactionAddEvent e = (GuildMessageReactionAddEvent) event;
            return (e.getMember().getIdLong() == ctx.getAuthor().getIdLong()) &&
                    (e.getMessageIdLong() == question.getIdLong()) &&
                    (e.getReactionEmote().getEmoji().equals("✅") ||
                    e.getReactionEmote().getEmoji().equals("❌"));
        }, event -> {
            // Delete the question message
            question.delete().queue();

            // If we timed out, null is returned
            if (event == null)
                getYui().getErrorHandler().onError(ctx, new CommandException("Timed out waiting for deletion conformation"));

            // Cast and handle confirm logic
            GuildMessageReactionAddEvent e = (GuildMessageReactionAddEvent) event;
            if (e.getReactionEmote().getEmoji().equals("❌")) {
                ctx.send("Playlist delete canceled");
                return;
            }

            db.deleteOne(Filters.eq("_id", document.get("_id", ObjectId.class)));
            ctx.send("Playlist `" + name + "` deleted!");
        }, 15, TimeUnit.SECONDS);
    }

    @GroupCommand(group = "playlist", name = "edit")
    public void _commandEdit(Context ctx) throws CommandException {
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



        // Edit name
        // Edit Editors
        // Edit image
        // Edit Description
    }



}
