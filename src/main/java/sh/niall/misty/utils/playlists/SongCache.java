package sh.niall.misty.utils.playlists;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import org.apache.commons.codec.binary.Base64;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.niall.misty.Misty;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.errors.MistyException;
import sh.niall.misty.utils.audio.AudioUtils;
import sh.niall.yui.Yui;
import sh.niall.yui.exceptions.CommandException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SongCache {

    private MongoCollection<Document> db = Misty.database.getCollection("songCache");
    private AudioPlayerManager audioPlayerManager;
    private int daysToExpire = 30;
    private Logger logger = LoggerFactory.getLogger(SongCache.class);

    public SongCache(Yui yui, AudioPlayerManager audioPlayerManager) {
        this.audioPlayerManager = audioPlayerManager;
        ((ScheduledExecutorService) Executors.newSingleThreadScheduledExecutor()).scheduleAtFixedRate(this::updateTask, 0, 1, TimeUnit.DAYS);
    }

    /**
     * Gets a song from the cache. If it's not in the cache, retrieves it from the source and stores it. For playlists use #getPlaylist.
     *
     * @param guild The current guild
     * @param url   The URL to lookup
     * @return The AudioTrack found
     * @throws AudioException   Thrown if there was an issue looking up the song
     * @throws MistyException   Thrown if there was an issue looking up the song
     * @throws IOException      Thrown if there was an issue decoding/encoding the track
     * @throws CommandException Thrown if no songs were found
     */
    public AudioTrack getTrack(Guild guild, String url) throws AudioException, MistyException, IOException, CommandException {
        // First check the database
        Document document = db.find(Filters.eq("url", url)).first();
        if (document != null)
            return decodeString(document.getString("data"));

        // We don't have the track in the database
        List<AudioTrack> tracks = AudioUtils.runQuery(audioPlayerManager, url, guild);
        if (tracks.isEmpty())
            throw new CommandException("URL returned no results!");

        // Store in db
        document = new Document();
        document.append("url", url);
        document.append("data", encodeTrack(tracks.get(0)));
        document.append("expires", Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(daysToExpire));
        db.insertOne(document);

        // Give it back
        return tracks.get(0);
    }

    /**
     * Looks up the playlist and stores the video urls
     *
     * @param guild The current guild
     * @param url   The URL to lookup
     * @return A list of AudioTracks found
     * @throws AudioException   Thrown if there was an issue looking up the song
     * @throws MistyException   Thrown if there was an issue looking up the song
     * @throws IOException      Thrown if there was an issue decoding/encoding the track
     * @throws CommandException Thrown if no songs were found
     */
    public List<AudioTrack> getPlaylist(Guild guild, String url) throws AudioException, MistyException, CommandException, IOException {
        // URLs we want to query directly and then cache the videos
        List<AudioTrack> tracks = AudioUtils.runQuery(audioPlayerManager, url, guild);
        if (tracks.isEmpty())
            throw new CommandException("URL returned no results!");

        List<AudioTrack> addedTracks = new ArrayList<>();
        for (AudioTrack track : tracks) {
            Document document = db.find(Filters.eq("url", track.getInfo().uri)).first();
            addedTracks.add(track);
            if (document != null)
                continue;

            // Store in db
            document = new Document();
            document.append("url", track.getInfo().uri);
            document.append("data", encodeTrack(track));
            document.append("expires", Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(daysToExpire));
            db.insertOne(document);
        }
        return addedTracks;
    }

    /**
     * Encodes a track for storage
     *
     * @param audioTrack The AudioTrack to encode
     * @return A byte string to store
     * @throws IOException Thrown if there was an issue encoding
     */
    private String encodeTrack(AudioTrack audioTrack) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        audioPlayerManager.encodeTrack(new MessageOutput(outputStream), audioTrack);
        return Base64.encodeBase64String(outputStream.toByteArray());
    }

    /**
     * Converts a byte string into an AudioTrack
     *
     * @param input The Byte String
     * @return The Audio Track
     * @throws IOException Thrown if there was an issue decoding
     */
    private AudioTrack decodeString(String input) throws IOException {
        return audioPlayerManager.decodeTrack(new MessageInput(new ByteArrayInputStream(Base64.decodeBase64(input)))).decodedTrack;
    }

    /**
     * Updates songs which have expired
     */
    public void updateTask() {
        this.logger.info("Updating song cache...");
        try {
            // Find all documents which have expired
            for (Document document : db.find(Filters.lte("expires", Instant.now().getEpochSecond()))) {
                // Get the URL and request for a new track
                String url = decodeString(document.getString("data")).getInfo().uri;
                AudioTrack newTrack = AudioUtils.runQuery(audioPlayerManager, url, null).get(0);
                String newData;

                // Encoding the new track can error if youtube makes it unavailable
                try {
                    newData = encodeTrack(newTrack);
                } catch (NullPointerException ignored) {
                    this.logger.info(String.format("Removing %s from the cache as it's unavailable", url));
                    continue;
                }

                // Create the updated document
                Document updatedDocument = new Document();

                // Set the expired field - This is changed no matter what
                updatedDocument.append("expires", Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(daysToExpire));

                // Check to see if the entry has expired
                if (!newData.equals(document.getString("data")))
                    updatedDocument.append("data", newData);

                // Update DB and wait
                db.updateOne(Filters.eq("_id", document.get("_id", ObjectId.class)), new Document("$set", updatedDocument));
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            }
            this.logger.info("Song cache updated!");
        } catch (Exception e) {
            this.logger.error("Updating song cache failed!");
            e.printStackTrace();
        }
    }
}
