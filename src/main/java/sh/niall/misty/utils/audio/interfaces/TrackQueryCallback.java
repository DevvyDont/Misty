package sh.niall.misty.utils.audio.interfaces;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public interface TrackQueryCallback {
    void results(List<AudioTrack> tracks, Exception error);
}
