package sh.niall.misty;

import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.LoggerFactory;
import sh.niall.misty.audio.AudioGuildManager;
import sh.niall.misty.cogs.Animals;
import sh.niall.misty.cogs.Music;
import sh.niall.misty.cogs.Playlists;
import sh.niall.misty.cogs.Utilities;
import sh.niall.misty.errors.ErrorHandler;
import sh.niall.misty.playlists.SongCache;
import sh.niall.misty.utils.config.Config;
import sh.niall.misty.utils.config.ConfigLoader;
import sh.niall.misty.utils.database.Database;
import sh.niall.yui.Yui;
import sh.niall.yui.exceptions.PrefixException;
import sh.niall.yui.prefix.PrefixManager;

import javax.security.auth.login.LoginException;
import java.io.FileNotFoundException;

public class Misty {

    public static Config config;
    public static Database database;

    public static void main(String[] args) throws LoginException, FileNotFoundException, PrefixException {
        // Initialize globals
        config = ConfigLoader.loadConfig();
        database = new Database();

        // Generate JDA Builder
        JDABuilder builder = new JDABuilder(config.getDiscordToken());
        builder.setAudioSendFactory(new NativeAudioSendFactory());
        builder.setActivity(Activity.watching("Anime"));

        // Setup Yui
        PrefixManager prefixManager = new PrefixManager(config.getDiscordPrefixes());
        Yui yui = new Yui(builder, prefixManager, new ErrorHandler());

        // Create the audio manager
        AudioGuildManager audioGuildManager = new AudioGuildManager(yui);
        SongCache songCache = new SongCache(yui, audioGuildManager.getAudioPlayerManager());

        // Add the cogs in
        yui.addCogs(
                new Animals(),
                new Music(audioGuildManager),
                new Playlists(audioGuildManager, songCache),
                new Utilities()
        );

        // Build JDA
        builder.build();
        LoggerFactory.getLogger(Misty.class).info("I'm online and ready to go!");
    }

}
