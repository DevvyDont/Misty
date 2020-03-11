package sh.niall.misty.utils.audio.helpers;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Track Request
 * Class used to hold tracks in a queue.
 */
public class TrackRequest {
    public AudioTrack audioTrack;
    public long requestAuthor;

    public TrackRequest(AudioTrack audioTrack, long requestAuthor) {
        this.audioTrack = audioTrack;
        this.requestAuthor = requestAuthor;
    }

    // We match based on the URL
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TrackRequest))
            return false;

        return ((TrackRequest) obj).audioTrack.getInfo().uri.equals(this.audioTrack.getInfo().uri);
    }
}
