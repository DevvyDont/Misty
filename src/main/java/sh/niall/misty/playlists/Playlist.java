package sh.niall.misty.playlists;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import sh.niall.misty.playlists.containers.PlaylistSong;
import sh.niall.misty.playlists.enums.Permission;
import sh.niall.yui.exceptions.CommandException;

import java.util.*;

public class Playlist {

    private MongoCollection<Document> db;
    public long author;
    public String friendlyName;
    public String searchName;
    public String image;
    public boolean isPrivate;
    public String description;
    public List<Long> editors;
    public int plays;
    public Map<String, PlaylistSong> songList = new HashMap<>();
    private Document originalDocument = null;

    /**
     * Creates a new playlist and saves it to the database
     *
     * @param db           The playlist Mongo Collection
     * @param author       The current owner of the playlist
     * @param friendlyName The current friendly name of the playlist
     */
    public Playlist(MongoCollection<Document> db, long author, String friendlyName) {
        this.db = db;
        this.author = author;
        this.friendlyName = friendlyName;
        this.searchName = generateSearchName(friendlyName);
        this.image = "";
        this.isPrivate = false;
        this.description = "";
        this.editors = new ArrayList<>();
        this.plays = 0;
    }

    /**
     * Loads the document from the database, throws an error if a document isn't found or is private
     *
     * @param db           The playlist Mongo Collection
     * @param author       The current owner of the playlist
     * @param friendlyName The current friendly name of the playlist
     * @param searchName   The current search name of the playlist
     */
    public Playlist(MongoCollection<Document> db, long author, String friendlyName, String searchName) throws CommandException {
        // Get the document from the database
        Document document = db.find(Filters.and(Filters.eq("author", author), Filters.eq("searchName", searchName))).first();
        if (document == null)
            throw new CommandException("I can't find playlist `" + friendlyName + "`");

        this.db = db;
        loadFromDocument(document);
    }

    /**
     * Loads a playlist from a document
     *
     * @param db       The playlists database
     * @param document The document to load from
     */
    public Playlist(MongoCollection<Document> db, Document document) {
        this.db = db;
        loadFromDocument(document);
    }


    /**
     * Loads the playlist information from a document
     *
     * @param document The document to load from
     */
    private void loadFromDocument(Document document) {
        this.author = document.getLong("author");
        this.friendlyName = document.getString("friendlyName");
        this.searchName = document.getString("searchName");
        image = document.getString("image");
        isPrivate = document.getBoolean("isPrivate");
        description = document.getString("description");
        editors = new ArrayList<>((List<Long>) document.get("editors"));
        plays = document.getInteger("plays");
        originalDocument = document;

        // Handle Song list
        Document songListDoc = document.get("songList", Document.class);
        for (Map.Entry<String, Object> entry : songListDoc.entrySet())
            songList.put(entry.getKey(), new PlaylistSong((Document) entry.getValue()));
    }

    /**
     * Gets the targets permission level for this playlist
     *
     * @param target The ID of the user to check
     * @return Their permission level
     */
    public Permission getUserPermission(long target) {
        if (this.author == target)
            return Permission.OWNER;
        else if (this.editors.contains(target))
            return Permission.EDITOR;
        else if (this.isPrivate)
            return Permission.NONE;
        else
            return Permission.VIEWER;
    }


    /**
     * Saves the playlist to the database
     * Inserts the document if it doesn't already exist
     * Updates the current document using set if it does
     *
     * @throws CommandException Thrown if there was an issue with the database
     */
    public void save() throws CommandException {
        Document document = new Document();
        if (originalDocument == null) {
            // We're handling a new playlist

            // Check the playlist doesn't already exist
            if (db.find(Filters.and(Filters.eq("author", this.author), Filters.eq("searchName", this.searchName))).first() != null)
                throw new CommandException("Playlist " + this.friendlyName + " already exits");

            // Insert the database
            document.append("author", this.author);
            document.append("friendlyName", this.friendlyName);
            document.append("searchName", this.searchName);
            document.append("image", this.image);
            document.append("isPrivate", this.isPrivate);
            document.append("description", this.description);
            document.append("editors", this.editors);
            document.append("plays", this.plays);
            document.append("songList", new HashMap<String, Document>());
            this.db.insertOne(document);
            return;
        }

        try {
            // We're handling an existing playlist
            ObjectId objectId = this.originalDocument.get("_id", ObjectId.class);

            if (!originalDocument.getLong("author").equals(this.author))
                document.put("author", this.author);

            if (!originalDocument.getString("friendlyName").equals(this.friendlyName))
                document.put("friendlyName", this.friendlyName);

            if (!originalDocument.getString("searchName").equals(this.searchName))
                document.put("searchName", this.searchName);

            if (!originalDocument.getString("image").equals(this.image))
                document.put("image", this.image);

            if (!originalDocument.getBoolean("isPrivate").equals(this.isPrivate))
                document.put("isPrivate", this.isPrivate);

            if (!originalDocument.getString("description").equals(this.description))
                document.put("description", this.description);

            if (!originalDocument.getInteger("plays").equals(this.plays))
                document.put("plays", this.plays);

            if (!originalDocument.get("editors").equals(this.editors))
                document.put("editors", this.editors);

            // Handle Song list
            Set<String> oldUrlSet = originalDocument.get("songList", Document.class).keySet();
            Set<String> newUrlSet = this.songList.keySet();
            if (oldUrlSet.size() != newUrlSet.size()) {
                Document storeList = new Document();
                for (Map.Entry<String, PlaylistSong> song : this.songList.entrySet())
                    storeList.put(song.getKey(), song.getValue().toDocument());
                document.put("songList", storeList);
            }

            db.updateOne(Filters.eq("_id", objectId), new Document("$set", document));
        } catch (Exception e) {
            e.printStackTrace();
            throw new CommandException("Failed to update the playlist, please report this to a developer.");
        }
    }

    /**
     * Deletes the playlist from the database
     */
    public void delete() {
        db.deleteOne(Filters.eq("_id", originalDocument.get("_id", ObjectId.class)));
    }

    /**
     * Converts a string into a valid search name
     *
     * @param friendlyName The string to convert
     * @return The converted string
     */
    public static String generateSearchName(String friendlyName) {
        return friendlyName.trim().toLowerCase();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Playlist))
            return false;

        return (this.author == ((Playlist) obj).author) && this.searchName.equals(((Playlist) obj).searchName);
    }
}
