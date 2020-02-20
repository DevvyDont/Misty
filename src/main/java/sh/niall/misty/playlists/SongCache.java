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
import sh.niall.yui.tasks.LoopStorage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
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

    public AudioTrack getTrack(Guild guild, String url) throws AudioException, MistyException, IOException {
        return getTrack(guild, url, true);
    }

    public AudioTrack getTrack(Guild guild, String url, boolean useCache) throws AudioException, MistyException, IOException {
        // First check the database
        Document document = db.find(Filters.eq("url", url)).first();
        if (document != null)
            return decodeString(document.getString("data"));

        // We don't have the track in the database
        AudioTrack track = AudioUtils.runQuery(audioPlayerManager, url, guild).get(0);

        // Store in db
        document = new Document();
        document.append("url", url);
        document.append("data", encodeTrack(track));
        document.append("expires", Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(daysToExpire));
        db.insertOne(document);

        // Give it back
        return track;
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
