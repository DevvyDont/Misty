package sh.niall.misty.utils.audio.helpers;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.errors.MistyException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TrackWaiter {

    private List<AudioTrack> result;
    private AudioException error;

    public List<AudioTrack> getResult() throws MistyException, AudioException {
        long timeout = 5000;
        long endTime = Instant.now().getEpochSecond() + TimeUnit.MILLISECONDS.toSeconds(timeout);
        while (Instant.now().getEpochSecond() < endTime && result == null) {
            synchronized (this) {
                try {
                    wait(timeout);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new MistyException("There was an error getting the track.");
                }
            }
        }
        if (error != null)
            throw error;
        return result;
    }

    public void setResult(List<AudioTrack> result, AudioException error) {
        this.result = result;
        this.error = error;
        synchronized (this) {
            notify();
        }
    }
}
