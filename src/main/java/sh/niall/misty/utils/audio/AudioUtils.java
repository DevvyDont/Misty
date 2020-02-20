package sh.niall.misty.utils.audio;


import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.errors.MistyException;
import sh.niall.misty.utils.audio.helpers.TrackWaiter;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.CommandException;

import java.util.Collections;
import java.util.List;

public class AudioUtils {

    public static boolean userInVoice(Context ctx) {
        return ctx.getAuthor().getVoiceState() != null && ctx.getAuthor().getVoiceState().inVoiceChannel();
    }

    /**
     * Checks if the bot is in the same channel as the author
     */
    public static boolean userInSameChannel(Context ctx) {
        // Check if the bot is connected
        if (!ctx.getGuild().getAudioManager().isConnected())
            return false;

        // Check if the member is connected
        if (!userInVoice(ctx))
            return false;

        // Check if the member and bot are in the same channel
        return ctx.getGuild().getAudioManager().getConnectedChannel().getId().equals(ctx.getAuthor().getVoiceState().getChannel().getId());
    }

    /**
     * Checks to see if the bot has the needed permissions in the voice channel
     */
    public static void hasPermissions(Member bot, VoiceChannel channel) throws CommandException {
        for (Permission perm : new Permission[]{Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.VOICE_USE_VAD}) {
            if (!bot.hasPermission(channel, perm))
                throw new CommandException(String.format("I don't have the permission `%s` in the channel `%s`", perm.getName(), channel.getName()));
        }
    }

    public static String durationToString(Long duration) {
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static List<AudioTrack> runQuery(AudioPlayerManager audioMgr, String query, Guild guild) throws MistyException, AudioException {
        TrackWaiter waiter = new TrackWaiter();
        audioMgr.loadItemOrdered(guild, query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                waiter.setResult(Collections.singletonList(track), null);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                waiter.setResult(playlist.getTracks(), null);
            }

            @Override
            public void noMatches() {
                waiter.setResult(null, new AudioException("No results found!"));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                waiter.setResult(null, new AudioException(exception.getMessage()));
            }
        });
        return waiter.getResult();
    }

}
