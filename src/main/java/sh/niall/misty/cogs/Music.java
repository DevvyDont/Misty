package sh.niall.misty.cogs;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.errors.MistyException;
import sh.niall.misty.utils.audio.AudioGuild;
import sh.niall.misty.utils.audio.AudioGuildManager;
import sh.niall.misty.utils.audio.AudioUtils;
import sh.niall.misty.utils.audio.helpers.TrackRequest;
import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.misty.utils.ui.Helper;
import sh.niall.misty.utils.ui.paginator.Paginator;
import sh.niall.yui.cogs.cog.Cog;
import sh.niall.yui.cogs.commands.annotations.Command;
import sh.niall.yui.cogs.commands.context.Context;
import sh.niall.yui.cogs.commands.help.annotations.CommandHelp;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.YuiException;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Music extends Cog {

    private AudioGuildManager audioGuildManager;

    public Music(AudioGuildManager audioGuildManager) {
        this.audioGuildManager = audioGuildManager;
    }

    /**
     * Moves the bot to the users voice channel
     */
    @CommandHelp(desc = "Make me join the voice channel")
    @Command(name = "summon", aliases = {"join"})
    public void _commandSummon(Context ctx) throws CommandException, InterruptedException {
        // Check to see if user is in a voice channel
        if (!AudioUtils.userInVoice(ctx))
            throw new CommandException("You can't summon me as you're not in a voice call");

        // Ignore if bot is already in the voice channel
        if (AudioUtils.userInSameChannel(ctx)) {
            ctx.send("I'm already in your voice channel!");
            return;
        }

        // Now check if we have the needed perms in the voice channel
        AudioUtils.hasPermissions(ctx.getSelf(), ctx.getAuthor().getVoiceState().getChannel());

        // Move to the voice channel
        audioGuildManager.joinChannel(ctx.getGuild(), ctx.getAuthor().getVoiceState().getChannel());

        // Update the text channel
        audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong()).setLastTextChannel(ctx.getChannel().getIdLong());

        // Send a message
        ctx.send("Connected to: " + ctx.getAuthor().getVoiceState().getChannel().getName());
    }

    @CommandHelp(desc = "Plays a song", arguments = {"Song URL"})
    @Command(name = "play", aliases = {"p"})
    public void _commandPlay(Context ctx) throws CommandException, InterruptedException, AudioException, MistyException {
        if (ctx.getArguments().isEmpty())
            throw new CommandException("Please provide a URL for me to play! If you were looking to resume playback, use `resume` instead.");

        // Handle summon checks
        AudioUtils.runSummon(audioGuildManager, ctx);

        // Setup the Query
        String messageArgs = String.join(" ", ctx.getArguments());
        List<Url> urls = new UrlDetector(messageArgs, UrlDetectorOptions.Default).detect();
        String queryString = urls.isEmpty() ? "ytsearch:" + messageArgs : urls.get(0).getFullUrl();
        boolean usedSearch = urls.isEmpty();
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Run the Query
        List<AudioTrack> trackList = AudioUtils.runQuery(audioGuildManager.getAudioPlayerManager(), queryString, ctx.getGuild());

        // Queue the music
        for (AudioTrack audioTrack : trackList) {
            audioGuild.addToQueue(new TrackRequest(audioTrack, ctx.getAuthor().getIdLong()));
            if (usedSearch) // Only add the first found song
                break;
        }

        // Play the music
        if (audioGuild.getAudioPlayer().getPlayingTrack() == null)
            audioGuild.play();

        // Inform the invoker
        if (trackList.size() == 1 || usedSearch)
            ctx.send("Added the song `" + trackList.get(0).getInfo().title + "` to the queue!");
        else
            ctx.send("Added `" + trackList.size() + "` songs to the queue!");
    }

    @CommandHelp(desc = "Pauses the currently playing song")
    @Command(name = "pause")
    public void _commandPause(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't pause the music as we're not in the same voice channel!");

        // Get the Audio guild
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Warn if paused
        if (audioGuild.isPaused())
            throw new CommandException("I'm already paused!");

        audioGuild.pause();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Paused!");
    }

    @CommandHelp(desc = "Resumes the song if the song is paused")
    @Command(name = "resume")
    public void _commandResume(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't resume the music as we're not in the same voice channel!");

        // Get the Audio guild
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Warn if paused
        if (!audioGuild.isPaused())
            throw new CommandException("I'm not currently paused!");

        audioGuild.resume();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Resuming! The current song is: " + audioGuild.getCurrentSong().audioTrack.getInfo().title);
    }

    @CommandHelp(desc = "Makes me leave the voice channel")
    @Command(name = "stop", aliases = {"leave", "disconnect"})
    public void _commandStop(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't stop the music as we're not in the same voice channel!");

        audioGuildManager.deleteAudioGuild(ctx.getGuild().getIdLong());
        ctx.send("Good bye!");
    }

    @CommandHelp(desc = "Skip to the next song")
    @Command(name = "skip", aliases = {"s"})
    public void _commandSkip(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't skip this song as we're not in the same voice channel!");

        // Get the Audio guild
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Check if we're paused
        if (audioGuild.isPaused())
            throw new CommandException("I can't skip to the next song as I'm currently paused.");

        audioGuild.play();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Skipping!");
    }

    @CommandHelp(desc = "Skip to a song in the queue", arguments = {"Queue ID"})
    @Command(name = "skipto")
    public void _commandSkipTo(Context ctx) throws CommandException, AudioException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't skip to a song as we're not in the same voice channel!");

        // Get the Audio guild
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Check if we're paused
        if (audioGuild.isPaused())
            throw new CommandException("I can't skip to the specified song as I'm currently paused.");

        int skipTo;

        try {
            skipTo = Integer.parseInt(ctx.getArguments().get(0));
        } catch (NumberFormatException e) {
            throw new CommandException("Please provide a valid song to skip to. Hint: Use `queue` to get the songs number");
        }
        audioGuild.skipTo(skipTo - 1);
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Skipping to song: " + audioGuild.getCurrentSong().audioTrack.getInfo().title);
    }

    @CommandHelp(desc = "Clears the queue")
    @Command(name = "clear")
    public void _commandClear(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't clear the queue because we're not in the same voice channel!");

        // Get the Audio guild
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Clear the queue
        audioGuild.clear();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Queue cleared!");
    }

    @CommandHelp(desc = "Displays the current volume, or changes it.", arguments = {"Volume to change to [0-100]"})
    @Command(name = "volume", aliases = {"vol", "v"})
    public void _commandVolume(Context ctx) throws CommandException, AudioException {
        // First make sure the bot is in a voice call
        if (!ctx.getGuild().getAudioManager().isConnected())
            throw new CommandException("I don't have a volume level because I'm not in a voice call");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        if (ctx.getArguments().isEmpty()) {
            // Display the volume
            ctx.send("\uD83C\uDFA7 The current volume is " + audioGuild.getVolume() + "%");
            return;
        }

        // We want to change the volume
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't change the volume because we're not in the same voice channel!");

        // Detect if the first argument is a int
        if (!ctx.getArguments().get(0).matches("-?(0|[1-9]\\d*)"))
            throw new CommandException("Please specify a valid number between 0-100 to change the volume to.");

        // Change the volume
        int volume = Integer.parseInt(ctx.getArguments().get(0));
        audioGuild.setVolume(volume);
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("\uD83C\uDFA7 The volume has been set to " + volume + "%");
    }

    @CommandHelp(desc = "Shows information about the currently playing song")
    @Command(name = "nowplaying", aliases = {"np"})
    public void _commandNowPlaying(Context ctx) throws CommandException {
        // First make sure the bot is connected
        if (!ctx.getGuild().getAudioManager().isConnected())
            throw new CommandException("I'm not playing anything because I'm not currently in a voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        TrackRequest trackRequest = audioGuild.getCurrentSong();

        if (trackRequest == null || audioGuild.isPaused())
            throw new CommandException("I'm currently not playing anything!");

        // Send the embed
        String duration = String.format(
                "%s/%s",
                AudioUtils.durationToString(trackRequest.audioTrack.getPosition()),
                AudioUtils.durationToString(trackRequest.audioTrack.getDuration())
        );

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Now Playing!", trackRequest.audioTrack.getInfo().uri);
        embedBuilder.setDescription("From " + StringUtils.capitalize(trackRequest.audioTrack.getSourceManager().getSourceName()));
        embedBuilder.setImage(audioGuild.getArtwork());
        embedBuilder.setColor(Helper.randomColor());
        embedBuilder.setAuthor("Requested by: " + UserSettings.getName(ctx, trackRequest.requestAuthor), null, UserSettings.getAvatarUrl(ctx, trackRequest.requestAuthor));
        embedBuilder.addField("Title:", trackRequest.audioTrack.getInfo().title, true);
        embedBuilder.addField("Duration:", duration, false);
        embedBuilder.addField("Volume:", audioGuild.getVolume() + "%", true);
        ctx.send(embedBuilder.build());
    }

    @CommandHelp(desc = "Enable/Disable song looping")
    @Command(name = "loop")
    public void _commandLoop(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't edit the loop setting because we're not in the same voice channel!");

        // Edit the loop settings
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        audioGuild.setSongLoop(!audioGuild.isSongLooping());
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());

        if (audioGuild.isSongLooping())
            ctx.send("\uD83D\uDD01 Looping!");
        else
            ctx.send("❌ No longer looping!");
    }

    @CommandHelp(desc = "Shuffle the current queue")
    @Command(name = "shuffle")
    public void _commandShuffle(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't edit the shuffle setting because we're not in the same voice channel!");

        // Edit the loop settings
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        audioGuild.setShuffling(!audioGuild.isShuffling());
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());

        if (audioGuild.isShuffling())
            ctx.send("\uD83D\uDD01 Shuffling!");
        else
            ctx.send("❌ No longer Shuffling!");
    }

    @CommandHelp(desc = "Skip to a certain point of the song", arguments = {"Timestamp"})
    @Command(name = "seek")
    public void _commandSeek(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't restart the song because we're not in the same voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        if (audioGuild.getCurrentSong() == null || audioGuild.isPaused())
            throw new CommandException("I'm currently not playing anything!");

        if (ctx.getArguments().isEmpty())
            throw new CommandException("Please provide a time to seek to.");

        // First check we were given just colons and numbers
        String time = ctx.getArguments().get(0);
        if (!time.matches("[0-9:]+"))
            throw new CommandException("Please provide a valid time to seek to. See `help seek` for more information");

        // Get the times
        String[] times = time.split(":");
        int hours = 0, minutes = 0, seconds = 0;

        if (times.length == 1) {
            seconds = Integer.parseInt(times[0]);
        } else if (times.length == 2) {
            minutes = Integer.parseInt(times[0]);
            seconds = Integer.parseInt(times[1]);

            if (seconds > 59)
                throw new CommandException("You can only seek up to 59 seconds when also providing minutes.");
        } else if (times.length == 3) {
            hours = Integer.parseInt(times[0]);
            minutes = Integer.parseInt(times[1]);
            seconds = Integer.parseInt(times[2]);

            if (seconds > 59)
                throw new CommandException("You can only seek up to 59 seconds when also providing minutes.");
            else if (minutes > 59)
                throw new CommandException("You can only seek up to 59 minutes when also providing hours.");
        } else {
            throw new CommandException("Please provide a valid time to seek to. See `help seek` for more information");
        }

        long totalTime = TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes) + TimeUnit.SECONDS.toMillis(seconds);
        if (totalTime >= audioGuild.getTrackLength())
            throw new CommandException("You can't seek to a position longer than the song");

        audioGuild.seek(totalTime);
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Seeking to: " + AudioUtils.durationToString(audioGuild.getCurrentSong().audioTrack.getPosition()));
    }

    @CommandHelp(desc = "Restarts the currently playing song")
    @Command(name = "restart")
    public void _commandRestart(Context ctx) throws CommandException {
        // TODO: Moderation override
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't restart the song because we're not in the same voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        if (audioGuild.getCurrentSong() == null || audioGuild.isPaused())
            throw new CommandException("I'm currently not playing anything!");

        audioGuild.restart();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Restarting song: " + audioGuild.getCurrentSong().audioTrack.getInfo().title);
    }

    @CommandHelp(desc = "Displays the servers queue")
    @Command(name = "queue", aliases = {"q"})
    public void _commandQueue(Context ctx) throws YuiException {
        // Check if the bot is connected
        if (!ctx.getGuild().getAudioManager().isConnected())
            throw new CommandException("There is no queue because I'm not in a voice channel");

        // Check if there's songs currently in the queue
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        if (audioGuild.getQueue().isEmpty())
            throw new CommandException("The queue is currently empty, request a song!");

        // Setup the song loop
        List<EmbedBuilder> embedBuilders = new ArrayList<>();
        Color embedColor = Helper.randomColor();
        long queueTotalTime = 0;
        int trackNumber = 1;

        // Loop over all the songs and add them to the queue
        for (List<TrackRequest> requests : ListUtils.partition(new ArrayList<>(audioGuild.getQueue()), 5)) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Music Queue");
            embedBuilder.setColor(embedColor);
            StringBuilder builder = new StringBuilder();
            for (TrackRequest request : requests) {
                // Request user
                User user = getYui().getJda().getUserById(request.requestAuthor);
                String requester = (user != null) ? user.getAsMention() : "Unknown User";

                // Add the song to the builder (Looks neater than using fields)
                builder.append(String.format("**%s. %s**\n", trackNumber, request.audioTrack.getInfo().title));
                builder.append(String.format("Requested by: %s\n", requester));
                builder.append(String.format("Length: %s\n", AudioUtils.durationToString(request.audioTrack.getDuration())));
                builder.append(String.format("Url: %s\n\n", request.audioTrack.getInfo().uri));
                trackNumber++;
                queueTotalTime += request.audioTrack.getDuration();
            }
            embedBuilder.setDescription(builder.toString());
            embedBuilders.add(embedBuilder);
        }

        // Add total time
        String queueTime = String.format("Total time remaining: %s\n", AudioUtils.durationToString(queueTotalTime));
        for (EmbedBuilder embedBuilder : embedBuilders)
            embedBuilder.setDescription(queueTime + embedBuilder.getDescriptionBuilder().toString());

        // Run the paginator
        Paginator paginator = new Paginator(ctx, embedBuilders, 160, true);
        paginator.run();
    }

    @CommandHelp(name = "Remove Duplicates", desc = "Removes duplicate songs from the queue")
    @Command(name = "removeduplicates")
    public void _commandRemoveDuplicates(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't edit the queue because we're not in the same voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        int amountRemoved = audioGuild.removeDuplicates();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Removed `" + amountRemoved + "` duplicate songs from the queue!");
    }

    @CommandHelp(name = "Remove Inactive", desc = "Removes songs from the queue, requested by members not in call")
    @Command(name = "removeinactive")
    public void _commandRemoveInactive(Context ctx) throws CommandException, AudioException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't edit the queue because we're not in the same voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        int amountRemoved = audioGuild.removeInactiveUsers();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Removed `" + amountRemoved + "` inactive users songs from the queue!");
    }
}
