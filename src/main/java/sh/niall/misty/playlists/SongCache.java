package sh.niall.misty.playlists;

import com.mongodb.DBCursor;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.managers.AudioManager;
import org.apache.commons.codec.binary.Base64;
import org.bson.Document;
import org.bson.types.ObjectId;
import sh.niall.misty.Misty;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.errors.MistyException;
import sh.niall.misty.utils.audio.AudioUtils;
import sh.niall.yui.Yui;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.tasks.LoopStorage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SongCache {

    private MongoCollection<Document> db = Misty.database.getCollection("songCache");
    private AudioPlayerManager audioPlayerManager;
    private Yui yui;
    private int daysToExpire = 30;

    public SongCache(Yui yui, AudioPlayerManager audioPlayerManager) {
        this.audioPlayerManager = audioPlayerManager;
        this.yui = yui;
        yui.getExecutor().execute(() -> {
            while (true) {
                updateTask();
                try {
                    Thread.sleep(TimeUnit.HOURS.toMillis(1));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

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

    private String encodeTrack(AudioTrack audioTrack) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        audioPlayerManager.encodeTrack(new MessageOutput(outputStream), audioTrack);
        return Base64.encodeBase64String(outputStream.toByteArray());
    }

    private AudioTrack decodeString(String input) throws IOException {
        return audioPlayerManager.decodeTrack(new MessageInput(new ByteArrayInputStream(Base64.decodeBase64(input)))).decodedTrack;
    }

    public void updateTask() {
        try {
            // Find all documents which have expired
            for (Document document : db.find(Filters.lte("expires", Instant.now().getEpochSecond()))) {
                // Get the URL and request for a new track
                String url = decodeString(document.getString("data")).getInfo().uri;
                AudioTrack newTrack = AudioUtils.runQuery(audioPlayerManager, url, null).get(0);
                String newData = encodeTrack(newTrack);

                // If it matches, don't bother updating it.
                if (newData.equals(document.getString("data")))
                    continue;

                // If it's different, update it.
                Document updatedDocument = new Document();
                updatedDocument.append("data", newData);
                updatedDocument.append("expires", Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(daysToExpire));
                db.updateOne(Filters.eq("_id", document.get("_id", ObjectId.class)), new Document("$set", updatedDocument));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
