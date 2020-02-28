package sh.niall.misty.playlists.containers;

/*
Return container for the PlaylistUtils#getTargetAndName method
 */
public class PlaylistLookupContainer {
    public long targetId;
    public String playlistName;

    public PlaylistLookupContainer(long targetId, String playlistName) {
        this.targetId = targetId;
        this.playlistName = playlistName;
    }
}
