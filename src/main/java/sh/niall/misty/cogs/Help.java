package sh.niall.misty.cogs;

import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Command;
import sh.niall.yui.exceptions.CommandException;

import java.util.HashMap;

public class Help extends Cog {

    HashMap<String, String> commands = new HashMap<>();

    public Help() {
        // Animals
        commands.put("dog", "Posts a photo/video of a dog.");
        commands.put("cat", "Posts a photo of a cat.");

        // Music
        commands.put("summon", "Summons the bot to your voice channel.");
        commands.put("play", "Queues a song for me to play. Usage: play song-url");
        commands.put("pause", "Pauses the currently playing song.");
        commands.put("resume", "Resumes the currently paused song.");
        commands.put("stop", "Stops the song and makes me leave the voice channel.");
        commands.put("skip", "Skips the current song.");
        commands.put("skipto", "Skips the specified song. To get the song numbers use the queue command. Usage: skipto index");
        commands.put("clear", "Clears the queue and skips the current song.");
        commands.put("volume", "Sets the playback volume. Usage: volume 0-100");
        commands.put("nowplaying", "Displays the current song.");
        commands.put("loop", "Enables/Disables looping of the current song.");
        commands.put("shuffle", "Shuffles the current queue.");
        commands.put("seek", "Seeks to the specified moment in the song. Usage: seek time Examples: seek 30 (seek 30 seconds) seek 1:30 (seek 1 minute 30 seconds).");
        commands.put("restart", "Restarts the current song.");
        commands.put("queue", "Displays the current song queue.");
        commands.put("removeduplicates", "Removes duplicate songs from the queue.");
        commands.put("removeinactive", "Removes songs from the queue which were request by people that aren't in the voice call.");

        // Playlist
        commands.put("playlist create", "Creates a new playlist. Usage: playlist create my new playlist");
        commands.put("playlist delete", "Deletes a playlist. Usage: playlist delete my new playlist");
        commands.put("playlist edit", "Shows the playlist edit menu. Usage: playlist edit my new playlist");
        commands.put("playlist add", "Adds a new song to the specified playlist. Usage: playlist add my new playlist song-url");
        commands.put("playlist remove", "Removes a song from the specified playlist. Usage: playlist remove my new playlist song-url");
        commands.put("playlist list", "Lists a users playlist or displays a playlist. Usage: playlist list @Misty or playlist list my new playlist");
        commands.put("playlist play", "Adds the playlists songs to the music queue. Usage: playlist play my new playlist");

        // Bio
        commands.put("bio", "Show a users bio. Usage: bio or bio @Misty");
        commands.put("bio set", "Sets your bio. Usage: bio set bio-here");
        commands.put("bio clear", "Removes your current bio.");

        // Tags
        commands.put("tag", "Displays a tags content. Usage: tag tag-name");
        commands.put("tag create", "Creates a new tag. Usage: tag create tag-name");
        commands.put("tag delete", "Deletes a tag. Usage: tag delete tag-name");
        commands.put("tag edit", "Shows the tag edit menu. Usage: tag edit tag-name");
        commands.put("tag info", "Shows a tags info. Usage: tag info tag-name");
        commands.put("tag list", "Lists a users tags. Usage: tag list @Misty");
        commands.put("tag claim", "Claims a tag from an inactive user. Usage: tag claim tag-name");

        // Utils
        commands.put("avatar", "Gets the users avatar. Usage: avatar or avatar @Misty");
        commands.put("screenshare", "Gets the screen share link for the invokers voice call.");
    }

    @Command(name = "help")
    public void _commandHelp(Context ctx) throws CommandException {
        if (ctx.getArgsStripped().isEmpty()) {
            postAll(ctx);
            return;
        }

        String helpString = String.join(" ", ctx.getArgsStripped()).trim().toLowerCase();
        if (commands.containsKey(helpString)) {
            commands.get(helpString);
            ctx.send(formatCommand(helpString));
            return;
        }

        switch (helpString) {
            case "animals":
                ctx.send(String.format("**Animals:**\n```%s```", getAnimals()));
                return;
            case "music":
                ctx.send(String.format("**Music:**\n```%s```", getMusic()));
                return;
            case "playlist":
                ctx.send(String.format("**Playlists:**\n```%s```", getPlaylist()));
                return;
            case "bio":
                ctx.send(String.format("**Bios:**\n```%s```", getBios()));
                return;
            case "tags":
                ctx.send(String.format("**Tags:**\n```%s```", getTags()));
                return;
            case "utilities":
                ctx.send(String.format("**Utilities:**\n```%s```", getUtilities()));
                return;
        }
        throw new CommandException("Sorry, I don't know how to get help for that! Try using just the help command");
    }

    private String getUtilities() {
        return formatCommand("avatar") + formatCommand("screenshare");
    }

    private String getBios() {
        StringBuilder builder = new StringBuilder();
        for (String command : commands.keySet()) {
            if (!command.startsWith("bio"))
                continue;
            builder.append(formatCommand(command));
        }
        return builder.toString();
    }

    private String getTags() {
        StringBuilder builder = new StringBuilder();
        for (String command : commands.keySet()) {
            if (!command.startsWith("tag"))
                continue;
            builder.append(formatCommand(command));
        }
        return builder.toString();
    }

    private String getPlaylist() {
        StringBuilder builder = new StringBuilder();
        for (String command : commands.keySet()) {
            if (!command.startsWith("playlist"))
                continue;
            builder.append(formatCommand(command));
        }
        return builder.toString();
    }

    private String getMusic() {
        return formatCommand("summon") +
                formatCommand("play") +
                formatCommand("pause") +
                formatCommand("resume") +
                formatCommand("stop") +
                formatCommand("skip") +
                formatCommand("skipto") +
                formatCommand("clear") +
                formatCommand("volume") +
                formatCommand("nowplaying") +
                formatCommand("loop") +
                formatCommand("shuffle") +
                formatCommand("seek") +
                formatCommand("restart") +
                formatCommand("queue") +
                formatCommand("removeduplicates") +
                formatCommand("removeinactive");
    }

    private String getAnimals() {
        return formatCommand("dog") + formatCommand("cat");
    }

    private void postAll(Context ctx) {
        ctx.send(String.format("**Animals:**\n```%s```", getAnimals()));
        ctx.send(String.format("**Music:**\n```%s```", getMusic()));
        ctx.send(String.format("**Playlists:**\n```%s```", getPlaylist()));
        ctx.send(String.format("**Bios:**\n```%s```", getBios()) + String.format("**Tags:**\n```%s```", getTags()) + String.format("**Utilities:**\n```%s```", getUtilities()));
    }

    private String formatCommand(String command) {
        return String.format("%s - %s\n", command, commands.get(command));
    }


}
