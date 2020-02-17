package sh.niall.misty.cogs;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import sh.niall.misty.audio.AudioGuild;
import sh.niall.misty.audio.AudioGuildManager;
import sh.niall.misty.audio.TrackRequest;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.utils.audio.AudioUtils;
import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Command;
import sh.niall.yui.exceptions.CommandException;

import java.awt.*;
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
        AudioUtils.hasPermissions(ctx.getMe(), ctx.getAuthor().getVoiceState().getChannel());

        // Move to the voice channel
        audioGuildManager.joinChannel(ctx.getGuild(), ctx.getAuthor().getVoiceState().getChannel());

        // Update the text channel
        audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong()).setLastTextChannel(ctx.getChannel().getIdLong());

        // Send a message
        ctx.send("Connected to: " + ctx.getAuthor().getVoiceState().getChannel().getName());
    }

    @Command(name = "play", aliases = {"p"})
    public void _commandPlay(Context ctx) throws CommandException, InterruptedException {
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide a URL for me to play! If you were looking to resume playback, use `resume` instead.");

        // Check to see if user is in a voice channel
        if (!AudioUtils.userInVoice(ctx))
            throw new CommandException("You can't summon me as you're not in a voice call");

        // Check to see if the bot is connected
        if (!ctx.getGuild().getAudioManager().isConnected()) {
            AudioUtils.hasPermissions(ctx.getMe(), ctx.getAuthor().getVoiceState().getChannel());
            audioGuildManager.joinChannel(ctx.getGuild(), ctx.getAuthor().getVoiceState().getChannel());
            audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong()); // Calling this to generate an AudioGuild
        }

        // Check to make sure the User and Bot is in the same channel
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't ask me to play anything because we're not in the same voice channel. Please use `summon` if you want me to move.");

        // Update the audio guild
        audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong()).setLastTextChannel(ctx.getChannel().getIdLong());

        // Setup the Query
        String url = ctx.getArgsStripped().get(0);
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Run the Query
        AudioUtils.runQuery(audioGuildManager.getAudioPlayerManager(), url, ctx.getGuild(), (audioTracks, error) -> {
            // Check if there was an error
            if (error != null) {
                getYui().getErrorHandler().onError(ctx, error);
                return;
            }

            // Queue the music
            for (AudioTrack audioTrack : audioTracks) {
                audioGuild.addToQueue(new TrackRequest(audioTrack, ctx.getAuthor().getIdLong()));
            }

            // Play the music
            if (audioGuild.getAudioPlayer().getPlayingTrack() == null)
                audioGuild.play();

            // Inform the invoker
            if (audioTracks.size() == 1)
                ctx.send("Added the song `" + audioTracks.get(0).getInfo().title + "` to the queue!");
            else
                ctx.send("Added `" + audioTracks.size() + "` songs to the queue!");
        });
    }

    @Command(name = "pause")
    public void _commandPause(Context ctx) throws CommandException {
        // TODO: Moderation override
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

    @Command(name = "resume")
    public void _commandResume(Context ctx) throws CommandException {
        // TODO: Moderation override
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

    /**
     * Stops the music and disconnects the bot
     */
    @Command(name = "stop")
    public void _commandStop(Context ctx) throws CommandException {
        // TODO: Moderation override
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't stop the music as we're not in the same voice channel!");

        audioGuildManager.deleteAudioGuild(ctx.getGuild().getIdLong());
        ctx.send("Good bye!");
    }

    @Command(name = "skip", aliases = {"s"})
    public void _commandSkip(Context ctx) throws CommandException {
        // TODO: Moderation override
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

    @Command(name = "skipto")
    public void _commandSkipTo(Context ctx) throws CommandException, AudioException {
        // TODO: Moderation override
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't skip to a song as we're not in the same voice channel!");

        // Get the Audio guild
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Check if we're paused
        if (audioGuild.isPaused())
            throw new CommandException("I can't skip to the specified song as I'm currently paused.");

        int skipTo;

        try {
            skipTo = Integer.parseInt(ctx.getArgsStripped().get(0));
        } catch (NumberFormatException e) {
            throw new CommandException("Please provide a valid song to skip to. Hint: Use `queue` to get the songs number");
        }
        audioGuild.skipTo(skipTo - 1);
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Skipping to song: " + audioGuild.getCurrentSong().audioTrack.getInfo().title);
    }

    @Command(name = "clear")
    public void _commandClear(Context ctx) throws CommandException {
        // TODO: Moderation override
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't clear the queue because we're not in the same voice channel!");

        // Get the Audio guild
        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());

        // Clear the queue
        audioGuild.clear();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Queue cleared!");
    }

    @Command(name = "volume", aliases = {"vol", "v"})
    public void _commandVolume(Context ctx) throws CommandException, AudioException {
        // First make sure the bot is in a voice call
        if (!ctx.getGuild().getAudioManager().isConnected())
            throw new CommandException("I don't have a volume level because I'm not in a voice call");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        if (ctx.getArgsStripped().isEmpty()) {
            // Display the volume
            ctx.send("\uD83C\uDFA7 The current volume is " + audioGuild.getVolume() + "%");
            return;
        }

        // We want to change the volume
        // TODO: Moderation override
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't change the volume because we're not in the same voice channel!");

        // Detect if the first argument is a int
        if (!ctx.getArgsStripped().get(0).matches("-?(0|[1-9]\\d*)"))
            throw new CommandException("Please specify a valid number between 0-100 to change the volume to.");

        // Change the volume
        int volume = Integer.parseInt(ctx.getArgsStripped().get(0));
        audioGuild.setVolume(volume);
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("\uD83C\uDFA7 The volume has been set to " + volume + "%");
    }

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
        Member requester = ctx.getGuild().getMemberById(trackRequest.requestAuthor);

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Now Playing!", trackRequest.audioTrack.getInfo().uri);
        embedBuilder.setDescription("From " + StringUtils.capitalize(trackRequest.audioTrack.getSourceManager().getSourceName()));
        embedBuilder.setImage(audioGuild.getArtwork());
        embedBuilder.setColor(Color.PINK);
        embedBuilder.setAuthor("Requested by: " + requester.getEffectiveName(), null, requester.getUser().getEffectiveAvatarUrl());
        embedBuilder.addField("Title:", trackRequest.audioTrack.getInfo().title, true);
        embedBuilder.addField("Duration:", duration, false);
        embedBuilder.addField("Volume:", audioGuild.getVolume() + "%", true);
        ctx.send(embedBuilder.build());
    }

    @Command(name = "loop")
    public void _commandLoop(Context ctx) throws CommandException {
        // TODO: Moderation override
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

    @Command(name = "shuffle")
    public void _commandShuffle(Context ctx) throws CommandException {
        // TODO: Moderation override
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

    @Command(name = "seek")
    public void _commandSeek(Context ctx) throws CommandException {
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't restart the song because we're not in the same voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        if (audioGuild.getCurrentSong() == null || audioGuild.isPaused())
            throw new CommandException("I'm currently not playing anything!");

        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please provide a time to seek to.");

        // First check we were given just colons and numbers
        String time = ctx.getArgsStripped().get(0);
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

    @Command(name = "queue", aliases = {"q"})
    public void _commandQueue(Context ctx) throws CommandException {
        if (!ctx.getGuild().getAudioManager().isConnected())
            throw new CommandException("There is no queue because I'm not in a voice channel");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        List<TrackRequest> queue = audioGuild.getQueue();

        if (queue.isEmpty())
            throw new CommandException("The queue is currently empty! Request a song :)");

        StringBuilder output = new StringBuilder("**Music Queue:**\n");
        int count = 1;

        for (TrackRequest trackRequest : queue) {
            if (output.length() > 1800) {
                output.append("and more...");
                break;
            }

            output.append(
                    String.format(
                        "__%s. %s__\nRequested by: %s\nLength: %s\n\n",
                        count,
                        trackRequest.audioTrack.getInfo().title,
                        getYui().getJda().getUserById(trackRequest.requestAuthor).getAsMention(),
                        AudioUtils.durationToString(trackRequest.audioTrack.getDuration())
                    )
            );
            count++;
        }
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send(output.toString());
    }

    @Command(name = "removeduplicates")
    public void _commandRemoveDuplicates(Context ctx) throws CommandException {
        // TODO: Moderation override
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't edit the queue because we're not in the same voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        int amountRemoved = audioGuild.removeDuplicates();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Removed `" + amountRemoved + "` duplicate songs from the queue!");
    }

    @Command(name = "removeinactive")
    public void _commandRemoveInactive(Context ctx) throws CommandException, AudioException {
        // TODO: Moderation override
        if (!AudioUtils.userInSameChannel(ctx))
            throw new CommandException("You can't edit the queue because we're not in the same voice channel!");

        AudioGuild audioGuild = audioGuildManager.getAudioGuild(ctx.getGuild().getIdLong());
        int amountRemoved = audioGuild.removeInactiveUsers();
        audioGuild.setLastTextChannel(ctx.getChannel().getIdLong());
        ctx.send("Removed `" + amountRemoved + "` inactive users songs from the queue!");
    }
}
