package sh.niall.misty.utils.playlists.containers;

import com.linkedin.urls.Url;

import java.util.List;

/*
Return container for the PlaylistUtils#getPlaylistAndURLs method
 */
public class PlaylistUrlsContainer {
    public long targetId;
    public String playlistName;
    public List<Url> songUrls;

    public PlaylistUrlsContainer(long targetId, String playlistName, List<Url> songUrls) {
        this.targetId = targetId;
        this.playlistName = playlistName;
        this.songUrls = songUrls;
    }
}
