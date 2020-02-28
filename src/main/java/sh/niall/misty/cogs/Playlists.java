package sh.niall.misty.cogs;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import sh.niall.misty.Misty;
import sh.niall.misty.audio.AudioGuild;
import sh.niall.misty.audio.AudioGuildManager;
import sh.niall.misty.audio.TrackRequest;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.errors.MistyException;
import sh.niall.misty.playlists.Playlist;
import sh.niall.misty.playlists.PlaylistUtils;
import sh.niall.misty.playlists.SongCache;
import sh.niall.misty.playlists.containers.PlaylistLookupContainer;
import sh.niall.misty.playlists.containers.PlaylistSong;
import sh.niall.misty.playlists.containers.PlaylistUrlsContainer;
import sh.niall.misty.playlists.enums.Permission;
import sh.niall.misty.utils.audio.AudioUtils;
import sh.niall.misty.utils.cogs.MistyCog;
import sh.niall.misty.utils.ui.Helper;
import sh.niall.misty.utils.ui.Menu;
import sh.niall.misty.utils.ui.Paginator;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Group;
import sh.niall.yui.commands.interfaces.GroupCommand;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;

import java.awt.*;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
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
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide a playlist name.");

        // Get all of the users playlists, ensure they have less than 10
        if (this.db.count(Filters.eq("author", ctx.getAuthor().getIdLong())) >= maxPlaylists)
            throw new CommandException("You can only have a total of " + maxPlaylists + " playlists!");

        // Validate the name
        String friendlyName = String.join(" ", ctx.getArgsStripped());
        PlaylistUtils.validatePlaylistName(friendlyName);

        // Make the playlist
        new Playlist(this.db, ctx.getAuthor().getIdLong(), friendlyName).save();

        // Inform the user
        ctx.send("Playlist " + friendlyName + " created!");
    }

    @GroupCommand(group = "playlist", name = "delete", aliases = {"d"})
    public void _commandDelete(Context ctx) throws CommandException, WaiterException {
        // Get the playlist
        String friendlyName = String.join(" ", ctx.getArgsStripped());
        Playlist playlist = new Playlist(this.db, ctx.getAuthor().getIdLong(), friendlyName, Playlist.generateSearchName(friendlyName));

        // Convert editors to real names
        StringBuilder stringBuilder = new StringBuilder();
        for (Long editor : playlist.editors) {
            stringBuilder.append(PlaylistUtils.getTargetName(ctx, editor)).append("\n");
        }

        // Validate they want to delete the playlist
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Playlist delete confirm!");
        embedBuilder.setDescription("Are you sure you want to delete the playlist " + friendlyName);
        embedBuilder.setAuthor(ctx.getAuthor().getEffectiveName(), null, ctx.getUser().getEffectiveAvatarUrl());
        embedBuilder.addField("Songs:", String.valueOf(playlist.songList.size()), true);
        if (stringBuilder.length() != 0)
            embedBuilder.addField("Editors:", stringBuilder.toString(), true);
        embedBuilder.addField("Plays:", String.valueOf(playlist.plays), true);

        // Handle the result
        if (sendConfirmation(ctx, embedBuilder.build())) {
            playlist.delete();
            ctx.send("Playlist `" + friendlyName + "` deleted!");
        } else {
            ctx.send("Playlist delete canceled");
        }
    }

    @GroupCommand(group = "playlist", name = "edit", aliases = {"e"})
    public void _commandEdit(Context ctx) throws CommandException, WaiterException {
        // Get the playlist
        String friendlyName = String.join(" ", ctx.getArgsStripped());
        Playlist playlist = new Playlist(this.db, ctx.getAuthor().getIdLong(), friendlyName, Playlist.generateSearchName(friendlyName));

        // Setup the update information
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Edit Conformation")
                .setDescription("Are you sure you want to make these changes to the playlist " + friendlyName + " ?")
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
                        "Playlist Privacy",
                        "Add Editor",
                        "Remove Editor",
                        "Change Ownership",
                }
        );

        // Since all the other logic is done in a loop, handle the prompts first
        switch (menuOption) {
            case 1: // Rename
                ctx.send("What would you like to rename the playlist to?");
                break;
            case 2: // Description
                ctx.send("What would you like the playlist description to be?\n(Reply with `clear` to remove the current description)");
                break;
            case 3: // Image
                ctx.send("What would you like the playlist image to be?\n(Reply with `clear` to remove the current image)");
                break;
            case 4: // Privacy
                ctx.send("Would you like the playlist to be private? Making a playlist private means only editors and yourself will be able to play it. yes/no");
                break;
            case 5: // Add Editor
                if (playlist.editors.size() >= maxEditors)
                    throw new CommandException("You can't have more than " + maxEditors + " editors! Please remove an editor from the playlist first.");
                ctx.send("Who would you like to add to the playlist editor list?");
                break;
            case 6: // Remove Editor
                if (playlist.editors.isEmpty())
                    throw new CommandException("You don't have any playlist editors, so you can't remove any!");
                ctx.send("Who would you like to remove from the playlist editor list?");
                break;
            case 7: // Change Ownership
                ctx.send("Who would you like the new owner to be?");
                break;
        }

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
            String trimmed = newMessage.trim();

            try {
                switch (menuOption) {
                    case 1:
                        if (playlist.friendlyName.equals(newMessage))
                            throw new CommandException("The old and new playlist names match!");
                        PlaylistUtils.validatePlaylistName(newMessage);
                        if (db.find(Filters.and(Filters.eq("author", playlist.author), Filters.eq("searchName", Playlist.generateSearchName(newMessage)))).first() != null)
                            throw new CommandException("You already have a playlist called: " + newMessage);
                        embedBuilder.addField("Old Name:", playlist.friendlyName, true);
                        embedBuilder.addField("New Name:", newMessage, true);
                        playlist.friendlyName = newMessage;
                        playlist.searchName = Playlist.generateSearchName(newMessage);
                        break inputLoop;
                    case 2:
                        if (trimmed.toLowerCase().equals("clear"))
                            trimmed = "";
                        else
                            PlaylistUtils.validatePlaylistDescription(trimmed);

                        embedBuilder.addField("Old Description:", playlist.description, false);
                        embedBuilder.addField("New Description:", trimmed, false);
                        playlist.description = trimmed;
                        break inputLoop;
                    case 3:
                        String url;
                        if (trimmed.toLowerCase().equals("clear")) {
                            url = "";
                            embedBuilder.addField("New Image:", "You've decided to remove the current image. Are you sure?", true);

                        } else {
                            List<Url> urls = new UrlDetector(trimmed, UrlDetectorOptions.Default).detect();
                            if (urls.isEmpty())
                                throw new CommandException("Provided URL is invalid");
                            url = urls.get(0).getFullUrl();
                            String[] splits = url.split("\\.");
                            String suffix = splits[splits.length - 1];
                            if (!(suffix.equals("png") || suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("gif")))
                                throw new CommandException("Playlist images must be a png, jpeg, jpg or gif");
                            embedBuilder.addField("New Image:", "Please verify the image is displaying correctly.", true);
                            embedBuilder.setThumbnail(newMessage);
                        }
                        playlist.image = url;
                        break inputLoop;
                    case 4:
                        Set<String> yes = Set.of("true", "yes", "ya", "y", "ye", "ok");
                        Set<String> no = Set.of("false", "no", "n", "nope");
                        String choice = trimmed.toLowerCase();
                        if (yes.contains(choice)) {
                            playlist.isPrivate = true;
                            embedBuilder.addField("Playlist Privacy:", "You have decided to make this playlist private", true);
                            break inputLoop;
                        } else if (no.contains(choice)) {
                            playlist.isPrivate = false;
                            embedBuilder.addField("Playlist Privacy:", "You have decided to make this playlist public", true);
                            break inputLoop;
                        }
                        throw new CommandException("Invalid choice given.");
                    case 5:
                        String newEditor = trimmed.split(" ")[0].replace("<@", "").replace("!", "").replace(">", "");
                        if (!newEditor.matches("\\d+"))
                            throw new CommandException("Invalid user! Please provide a valid user to add as an editor.");

                        long newTarget = Long.parseLong(newEditor);

                        if (playlist.editors.contains(newTarget))
                            throw new CommandException("They're already an editor!");

                        if (PlaylistUtils.targetDoesntExist(ctx, newTarget))
                            throw new CommandException("I don't know who that is! Please make sure the editor you're trying to add is in this server.");

                        if (ctx.getBot().getUserById(newTarget).isBot())
                            throw new CommandException("You can't make a bot an editor!");

                        playlist.editors.add(newTarget);
                        embedBuilder.addField("New editor:", PlaylistUtils.getTargetName(ctx, newTarget), false);
                        embedBuilder.addField("WARNING:", "Editors can Add and Remove songs from a playlist. Please make sure you trust who you're adding.", false);
                        embedBuilder.setColor(Color.RED);
                        break inputLoop;
                    case 6:
                        String toRemove = trimmed.split(" ")[0].replace("<@", "").replace("!", "").replace(">", "");
                        if (!toRemove.matches("\\d+"))
                            throw new CommandException("Invalid user! Please provide a valid user to remove.");

                        long removeTarget = Long.parseLong(toRemove);
                        if (!playlist.editors.contains(removeTarget))
                            throw new CommandException("They're not an editor for this playlist.");

                        playlist.editors.remove(removeTarget);
                        embedBuilder.addField("Removing editor:", PlaylistUtils.getTargetName(ctx, removeTarget), false);
                        embedBuilder.setColor(Color.RED);
                        break inputLoop;
                    case 7:
                        String newOwner = newMessage.split(" ")[0].replace("<@", "").replace("!", "").replace(">", "");
                        if (!newOwner.matches("\\d+"))
                            throw new CommandException("Invalid user! Please provide a valid user to change ownership to.");

                        long newOwnerLong = Long.parseLong(newOwner);
                        if (PlaylistUtils.targetDoesntExist(ctx, newOwnerLong))
                            throw new CommandException("I don't know who that is! Please make sure the new owner is in this server.");

                        if (ctx.getBot().getUserById(newOwnerLong).isBot())
                            throw new CommandException("You can't make a bot an owner of a playlist!");

                        if (db.count(Filters.eq("author", newOwnerLong)) >= maxPlaylists)
                            throw new CommandException("They already have the maximum amount of playlists!");

                        if (db.find(Filters.and(Filters.eq("author", newOwnerLong), Filters.eq("searchName", playlist.searchName))).first() != null)
                            throw new CommandException("They already have a playlist called " + playlist.friendlyName);

                        embedBuilder.addField("New Owner:", PlaylistUtils.getTargetName(ctx, newOwnerLong), false);
                        embedBuilder.addField("WARNING:", "You will lose access to this playlist and the playlist editors will be reset!", false);
                        embedBuilder.setColor(Color.RED);
                        playlist.editors = new ArrayList<>();
                        playlist.author = newOwnerLong;
                        break inputLoop;
                }
            } catch (CommandException error) {
                ctx.send("That won't work, please try again!\n`" + error.getMessage() + "`");
            }
        }

        if (menuOption == 7) { // Validate new ownership with the new owner
            EmbedBuilder targetEmbed = new EmbedBuilder()
                    .setTitle("Playlist Transfer")
                    .setDescription("Would you like to take ownership of the `" + playlist.friendlyName + "` playlist?")
                    .setColor(Color.ORANGE)
                    .setAuthor(PlaylistUtils.getTargetName(ctx, playlist.author), null, ctx.getBot().getUserById(playlist.author).getEffectiveAvatarUrl());

            boolean targetDecision;
            try {
                targetDecision = sendConfirmation(ctx, targetEmbed.build(), playlist.author);
            } catch (CommandException error) {
                ctx.send("Transfer canceled! New owner took too long to respond.");
                return;
            }
            if (!targetDecision)
                throw new CommandException("Transfer canceled! The new owner declined!");
        }

        // Confirm and edit
        if (sendConfirmation(ctx, embedBuilder.build())) {
            playlist.save();
            ctx.send("Playlist edited!");
        } else {
            ctx.send("Playlist edit canceled!");
        }
    }

    @GroupCommand(group = "playlist", name = "add", aliases = {"a"})
    public void _commandAdd(Context ctx) throws CommandException, AudioException, MistyException, IOException, WaiterException {
        // First translate our arguments into data
        PlaylistUrlsContainer results = PlaylistUtils.getPlaylistAndURLs(ctx);
        Playlist playlist = new Playlist(db, results.targetId, results.playlistName, Playlist.generateSearchName(results.playlistName));

        // Check invokers permissions
        Permission permission = playlist.getUserPermission(ctx.getAuthor().getIdLong());
        if (!(permission.equals(Permission.EDITOR) || permission.equals(Permission.OWNER)))
            throw new CommandException("You don't have permission to add songs to this playlist!");

        // Add the URLS
        Set<String> toAdd = new HashSet<>();
        for (Url url : results.songUrls) {
            String domain = url.getHost().toLowerCase().replace("www.", "");
            if (url.getPath().length() <= 1)
                throw new CommandException("Invalid URL provided: " + url.getFullUrl());

            if (domain.equals("youtu.be")) {
                toAdd.add("https://www.youtube.com/watch?v=" + url.getPath().substring(1));
                continue;
            } else if (domain.equals("youtube.com") && Arrays.asList("/watch", "/playlist").contains(url.getPath().toLowerCase())) {
                String playlistId = PlaylistUtils.getYoutubePlaylistId(url.getFullUrl());
                if (playlistId != null) {
                    for (AudioTrack audioTrack : songCache.getPlaylist(ctx.getGuild(), "https://www.youtube.com/playlist?list=" + playlistId))
                        toAdd.add(audioTrack.getInfo().uri);
                    continue;
                }
                String videoId = PlaylistUtils.getYoutubeVideoId(url.getFullUrl());
                if (videoId != null) {
                    toAdd.add("https://www.youtube.com/watch?v=" + videoId);
                    continue;
                }
            } else if (domain.equals("soundcloud.com")) {
                for (AudioTrack audioTrack : songCache.getPlaylist(ctx.getGuild(), url.getFullUrl())) {
                    toAdd.add(audioTrack.getInfo().uri);
                }
                continue;
            }
            throw new CommandException("Invalid URL provided: " + url.getFullUrl());
        }

        // Get the keys and add the songs (duplicates ignored) and compare the size
        Set<String> songListSet = new HashSet<>(playlist.songList.keySet());
        songListSet.addAll(toAdd);
        if (songListSet.size() > playlistSongLimit)
            throw new CommandException("There's not enough room in the playlist to add `" + toAdd.size() + "` songs!");

        // Add all the songs to the song list
        int added = 0;
        for (String url : toAdd) {
            if (playlist.songList.containsKey(url))
                continue;

            playlist.songList.put(url, new PlaylistSong(Instant.now().getEpochSecond(), ctx.getAuthor().getIdLong()));
            added++;
        }

        // Confirm to save
        if (sendConfirmation(ctx, String.format("Are you sure you want to add %s %s to %s?", added, Helper.singularPlural(added, "song", "songs"), playlist.friendlyName))) {
            playlist.save();
            ctx.send(
                    String.format(
                            "Found %s %s and added %s %s which brings the playlist total to %s %s!",
                            toAdd.size(), Helper.singularPlural(toAdd.size(), "song", "songs"),
                            added, Helper.singularPlural(added, "song", "songs"),
                            playlist.songList.size(), Helper.singularPlural(playlist.songList.size(), "song", "songs")
                    )
            );
        } else {
            ctx.send("Okay, I won't update the playlist!");
        }
    }

    @GroupCommand(group = "playlist", name = "remove", aliases = {"r"})
    public void _commandRemove(Context ctx) throws CommandException, WaiterException {
        // First translate our arguments into data
        PlaylistUrlsContainer results = PlaylistUtils.getPlaylistAndURLs(ctx);
        Playlist playlist = new Playlist(db, results.targetId, results.playlistName, Playlist.generateSearchName(results.playlistName));

        // Check invokers permissions
        Permission permission = playlist.getUserPermission(ctx.getAuthor().getIdLong());
        if (!(permission.equals(Permission.EDITOR) || permission.equals(Permission.OWNER)))
            throw new CommandException("You don't have permission to remove songs from this playlist!");

        Set<String> keySet = playlist.songList.keySet();
        for (Url url : results.songUrls) {
            if (!keySet.contains(url.getFullUrl()))
                throw new CommandException(String.format("Song %s isn't in playlist %s", url.getFullUrl(), playlist.friendlyName));
        }

        if (sendConfirmation(ctx, String.format("Are you sure you want to remove %s songs from %s?", results.songUrls.size(), playlist.friendlyName))) {
            for (Url url : results.songUrls)
                playlist.songList.remove(url.getFullUrl());
            playlist.save();
            ctx.send(String.format("Removed %s songs from %s", results.songUrls.size(), playlist.friendlyName));
        } else {
            ctx.send("Okay, I won't update the playlist!");
        }


    }

    @GroupCommand(group = "playlist", name = "list", aliases = {"l"})
    public void _commandList(Context ctx) throws CommandException, AudioException, MistyException, IOException {
        PlaylistLookupContainer result = PlaylistUtils.getTargetAndName(ctx);
        List<EmbedBuilder> embedList = new ArrayList<>();

        if (result.playlistName.isEmpty()) { // List the users playlists
            // Get the list of playlists
            List<Playlist> playlists = new ArrayList<>();
            for (Document document : db.find(Filters.or(Filters.eq("author", result.targetId), Filters.eq("editors", result.targetId)))) {
                Playlist playlist = new Playlist(db, document);
                Permission permission = playlist.getUserPermission(ctx.getAuthor().getIdLong());
                if (permission.equals(Permission.VIEWER) || permission.equals(Permission.EDITOR) || permission.equals(Permission.OWNER))
                    playlists.add(new Playlist(db, document));
            }

            // Ensure we found some
            if (playlists.isEmpty())
                throw new CommandException(String.format("I found no playlists by the user %s", PlaylistUtils.getTargetName(ctx, result.targetId)));

            while (!playlists.isEmpty()) {
                // Create the page
                int added = 0;
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle(PlaylistUtils.getTargetName(ctx, result.targetId) + " Playlists!");
                embedBuilder.setDescription("Showing playlists they own and can edit.");
                embedBuilder.setColor(Color.GREEN);

                // Add the playlists
                while (!playlists.isEmpty() && added < 5) {
                    added++;
                    Playlist playlist = playlists.remove(0);
                    String builder = playlist.description + "\n" +
                            "Songs: " + playlist.songList.size() + "\n" +
                            "Plays: " + playlist.plays + "\n" +
                            "Author: " + PlaylistUtils.getTargetName(ctx, playlist.author) + "\n" +
                            "Editors: " + playlist.editors.size() + "\n" +
                            "Role: " + StringUtils.capitalize(playlist.getUserPermission(result.targetId).toString().toLowerCase()) + "\n";
                    embedBuilder.addField(String.format("**%s**", playlist.friendlyName), builder, true);
                }

                // Add to the list of pages
                embedList.add(embedBuilder);
            }
        } else { // Display the specified playlist
            // Get the playlist
            Playlist playlist = new Playlist(db, result.targetId, result.playlistName, Playlist.generateSearchName(result.playlistName));
            Permission permission = playlist.getUserPermission(ctx.getAuthor().getIdLong());
            if (permission.equals(Permission.NONE))
                throw new CommandException("You are not allowed to view this playlist!");

            List<String> urls = new ArrayList<>(playlist.songList.keySet());
            if (urls.isEmpty())
                throw new CommandException(String.format("Playlist %s has no songs!", playlist.friendlyName));

            while (!urls.isEmpty()) {

                int added = 0;
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle(String.format("**%s**", playlist.friendlyName));
                embedBuilder.setDescription(playlist.description);
                embedBuilder.setColor(Color.PINK);
                if (!playlist.image.isEmpty())
                    embedBuilder.setThumbnail(playlist.image);
                if (!PlaylistUtils.targetDoesntExist(ctx, playlist.author))
                    embedBuilder.setAuthor(PlaylistUtils.getTargetName(ctx, playlist.author), null, ctx.getBot().getUserById(playlist.author).getEffectiveAvatarUrl());

                while (!urls.isEmpty() && added < 6) {
                    added++;
                    String url = urls.remove(0);
                    PlaylistSong playlistSong = playlist.songList.get(url);
                    AudioTrack audioTrack = songCache.getTrack(ctx.getGuild(), url);
                    String builder = "Duration: " + AudioUtils.durationToString(audioTrack.getInfo().length) + "\n" +
                            "Added By: " + PlaylistUtils.getTargetName(ctx, playlistSong.addedBy) + "\n" +
                            "Added: " + Instant.ofEpochSecond(playlistSong.addedTimestamp).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                            "Url: " + url;
                    embedBuilder.addField(audioTrack.getInfo().title, builder, true);
                }
                embedList.add(embedBuilder);
            }
        }

        // Add page numbers
        List<MessageEmbed> output = new ArrayList<>();
        int page = 1;
        int total = embedList.size();
        for (EmbedBuilder embedBuilder : embedList) {
            embedBuilder.setFooter(String.format("Page %s of %s", page, total));
            output.add(embedBuilder.build());
            page++;
        }

        Paginator paginator = new Paginator(getYui(), (TextChannel) ctx.getChannel(), output, 60);
        paginator.run();
    }

    @GroupCommand(group = "playlist", name = "play", aliases = {"p"})
    public void _commandPlay(Context ctx) throws CommandException, InterruptedException, AudioException, MistyException, IOException {
        // Handle summon checks
        AudioUtils.runSummon(audioGuildManager, ctx);

        // Understand the request
        PlaylistLookupContainer result = PlaylistUtils.getTargetAndName(ctx);
        if (result.playlistName.isEmpty())
            throw new CommandException("Please provide a playlist name to play!");

        // Find the playlist (throws command exception if it can't find)
        Playlist playlist = new Playlist(db, result.targetId, result.playlistName, Playlist.generateSearchName(result.playlistName));

        // Get the audio guild and check the queue has enough room
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        Set<String> songList = playlist.songList.keySet();
        if (audioGuild.getQueue().size() + songList.size() > AudioGuild.maxSongsInQueue)
            throw new CommandException("Can't add playlist as there's not enough room in the queue! Please clear the queue or try again later.");

        // Add a play to the counter
        if (Integer.MAX_VALUE > playlist.plays) {
            playlist.plays++;
            playlist.save();
        }

        // Add the songs to the queue
        for (String url : songList) {
            AudioTrack audioTrack = songCache.getTrack(ctx.getGuild(), url);
            audioGuild.addToQueue(new TrackRequest(audioTrack, ctx.getAuthor().getIdLong()));
        }

        // Play the music
        if (audioGuild.getAudioPlayer().getPlayingTrack() == null)
            audioGuild.play();

        // Inform the invoker
        ctx.send(String.format("Added `%s` %s from the `%s` playlist to the queue!", songList.size(), Helper.singularPlural(songList.size(), "song", "songs"), playlist.friendlyName));
    }

}
