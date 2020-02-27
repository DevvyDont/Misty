package sh.niall.misty.playlists.containers;

import org.bson.Document;

public class PlaylistSong {
    public long addedTimestamp;
    public long addedBy;

    public PlaylistSong(Document document) {
        this.addedTimestamp = document.getLong("addedTimestamp");
        this.addedBy = document.getLong("addedBy");
    }

    public PlaylistSong(long addedTimestamp, long addedBy) {
        this.addedTimestamp = addedTimestamp;
        this.addedBy = addedBy;
    }

    public Document toDocument() {
        Document document = new Document();
        document.put("addedTimestamp", addedTimestamp);
        document.put("addedBy", addedBy);
        return document;
    }
}
