package sh.niall.misty.utils.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import sh.niall.yui.Yui;
import sh.niall.yui.exceptions.CommandException;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class AudioGuildManager {

    private HashMap<Long, AudioGuild> audioGuilds = new HashMap<>();
    private AudioPlayerManager audioPlayerManager = new DefaultAudioPlayerManager();
    private Yui yui;

    public AudioGuildManager(Yui yui) {
        this.yui = yui;

        // Handle Lava Player setup
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);

        // Start the inactive checker
        yui.getExecutor().submit(() -> {
            while (true) {
                Thread.sleep(10000);
                runInactiveCheck();
            }
        });
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return audioPlayerManager;
    }

    /**
     * Gets the audio guild for the specified guild. Creates a new one if it doesn't exist.
     *
     * @param guildId The guild id to search for
     * @return The AudioGuild for the specified guild
     */
    public AudioGuild getAudioGuild(long guildId) {
        AudioGuild audioGuild = audioGuilds.get(guildId);
        if (audioGuild == null) {
            audioGuild = new AudioGuild(yui, guildId, audioPlayerManager);
            audioGuilds.put(guildId, audioGuild);
            yui.getJda().getGuildById(guildId).getAudioManager().setSendingHandler(audioGuild.getSendHandler());
        }
        return audioGuild;
    }

    /**
     * Deletes a guilds AudioGuild. Remains silent if one doesn't exist.
     *
     * @param guildId The guildId of the AudioGuild you want to delete
     */
    public void deleteAudioGuild(long guildId) {
        AudioGuild audioGuild = audioGuilds.get(guildId);
        if (audioGuild == null)
            return;

        audioGuild.stop();
        audioGuilds.remove(guildId);
        yui.getJda().getGuildById(guildId).getAudioManager().closeAudioConnection();
    }

    /**
     * Cleans up inactive guilds
     */
    public void runInactiveCheck() {
        for (long key : audioGuilds.keySet()) {
            // First get the guild and locate the voice session
            Guild guild = yui.getJda().getGuildById(key);
            GuildVoiceState guildVoiceState = guild.getSelfMember().getVoiceState();

            // Get the audio guild
            AudioGuild audioGuild = audioGuilds.get(key);

            // If the voice state doesn't exist or we're the only ones there, leave and inform.
            if (guildVoiceState == null || guildVoiceState.getChannel().getMembers().size() < 2) {
                guild.getTextChannelById(audioGuild.getLastTextChannel()).sendMessage(
                        "Leaving the voice call because I'm the only one here."
                ).queue(message -> message.delete().queueAfter(5, TimeUnit.SECONDS));
                deleteAudioGuild(key);
            }
        }
    }

    public void joinChannel(Guild guild, VoiceChannel channel) throws InterruptedException, CommandException {
        // Get the Guilds audio manager
        AudioManager audioManager = guild.getAudioManager();
        audioManager.openAudioConnection(channel);

        // Wait until the bot is connected
        while (audioManager.isAttemptingToConnect()) // Loop until connected
            Thread.sleep(100);

        // Will fail if the voice channel failed to join
        if (!audioManager.isConnected())
            throw new CommandException("Failed to connect to the channel!");
    }
}
