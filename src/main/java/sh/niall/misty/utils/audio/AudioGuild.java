package sh.niall.misty.utils.audio;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.bson.Document;
import sh.niall.misty.Misty;
import sh.niall.misty.errors.AudioException;
import sh.niall.misty.utils.audio.helpers.SendHandler;
import sh.niall.misty.utils.audio.helpers.TrackRequest;
import sh.niall.yui.Yui;
import sh.niall.yui.exceptions.CommandException;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio Guild
 * Main interaction with Lava Player for each guild
 */
public class AudioGuild extends AudioEventAdapter {

    // Basic info
    private long guildId;
    private int currentVolume = 100;
    private boolean loopSong = false;
    private boolean shuffling = false;
    private long lastTextChannel = 0;
    private TrackRequest currentSong = null;
    private final List<TrackRequest> trackQueue = new ArrayList<>();
    private Yui yui;

    // Audio Player
    private AudioPlayer audioPlayer;
    private SendHandler sendHandler;

    // Static
    public static int maxSongsInQueue = 300;

    public AudioGuild(Yui yui, long guildId, AudioPlayerManager audioPlayerManager) {
        // Setup some basics
        this.yui = yui;
        this.guildId = guildId;
        loadFromDB();

        // Create the audio player
        this.audioPlayer = audioPlayerManager.createPlayer();
        this.audioPlayer.addListener(this);
        this.sendHandler = new SendHandler(this.audioPlayer);
    }

    // Getters and Setters
    public int getVolume() {
        return currentVolume;
    }

    public void setVolume(int currentVolume) throws AudioException {
        if (currentVolume < 0 || 100 < currentVolume)
            throw new AudioException("Volume must be between 0-100!");

        this.currentVolume = currentVolume;
        audioPlayer.setVolume(this.currentVolume);
        saveToDB();
    }

    public boolean isSongLooping() {
        return loopSong;
    }

    public void setSongLoop(boolean loopSong) {
        this.loopSong = loopSong;
    }

    public long getLastTextChannel() {
        return lastTextChannel;
    }

    public void setLastTextChannel(long lastTextChannel) {
        this.lastTextChannel = lastTextChannel;
    }

    public boolean isShuffling() {
        return shuffling;
    }

    public void setShuffling(boolean shuffling) {
        this.shuffling = shuffling;
    }

    public boolean isPaused() {
        return audioPlayer.isPaused();
    }

    public SendHandler getSendHandler() {
        return sendHandler;
    }

    public TrackRequest getCurrentSong() {
        return currentSong;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    /**
     * Adds a new song to the end of the queue
     *
     * @param trackRequest The song to add to the queue
     */
    public void addToQueue(TrackRequest trackRequest) throws CommandException {
        if (trackQueue.size() == maxSongsInQueue)
            throw new CommandException("The queue is currently full! Please try again after a few songs");
        trackQueue.add(trackRequest);
    }

    /**
     * Gets the current queue
     *
     * @return The queue of tracks
     */
    public List<TrackRequest> getQueue() {
        return new ArrayList<>(trackQueue);
    }

    /**
     * Removes a song from the queue
     *
     * @param value The song to remove
     * @throws AudioException Thrown if the song does not exist.
     */
    public void removeFromQueue(int value) throws AudioException {
        try {
            trackQueue.remove(value);
        } catch (IndexOutOfBoundsException e) {
            throw new AudioException("Value " + value + " does not exist in the queue.");
        }
    }

    /**
     * Removes duplicate songs based on the URL or File Path
     */
    public int removeDuplicates() {
        List<TrackRequest> newQueue = new ArrayList<>();
        int oldSize = trackQueue.size();
        for (TrackRequest trackRequest : trackQueue) {
            if (!newQueue.contains(trackRequest))
                newQueue.add(trackRequest);
        }
        trackQueue.clear();
        trackQueue.addAll(newQueue);
        return oldSize - trackQueue.size();
    }

    /**
     * Removes tracks if the user that requested them isn't in the call
     */
    public int removeInactiveUsers() {
        GuildVoiceState guildVoiceState = yui.getJda().getGuildById(guildId).getSelfMember().getVoiceState();

        // Get the current members
        List<Member> members = guildVoiceState.getChannel().getMembers();
        List<Long> memberIds = new ArrayList<>();
        for (Member member : members)
            memberIds.add(member.getIdLong());

        // Remove tracks which don't have the requested member in the channel
        int oldSize = trackQueue.size();
        trackQueue.removeIf(trackRequest -> (!memberIds.contains(trackRequest.requestAuthor)));
        return oldSize - trackQueue.size();
    }

    /**
     * Skips to the specified song
     *
     * @param value The index of the song to skip to
     * @throws AudioException Thrown if Looping is enabled
     */
    public void skipTo(int value) throws AudioException, CommandException {
        if (loopSong)
            throw new AudioException("Looping is enabled, so we can't skip forward to a new song.");

        if (value < 0 || value >= trackQueue.size())
            throw new CommandException("Please provide a valid song to skip to. Hint: Use `queue` to get the songs number");


        trackQueue.subList(value, trackQueue.size());
        playNextSong();
    }

    public void play() {
        playNextSong();
        audioPlayer.setPaused(false);
    }

    /**
     * Restarts the currently playing song
     */
    public void restart() {
        audioPlayer.getPlayingTrack().setPosition(0);
    }

    /**
     * Pauses the song
     */
    public void pause() {
        audioPlayer.setPaused(true);
    }

    /**
     * Resumes the song
     */
    public void resume() {
        audioPlayer.setPaused(false);
    }

    /**
     * Clears the queue
     */
    public void clear() {
        trackQueue.clear();
    }

    /**
     * Stops the current playing song and deletes the audio player
     */
    public void stop() {
        audioPlayer.stopTrack();
        audioPlayer.destroy();
    }

    public long getTrackLength() {
        return audioPlayer.getPlayingTrack().getDuration();
    }

    public void seek(long seekTo) {
        audioPlayer.getPlayingTrack().setPosition(seekTo);
    }

    /**
     * Plays the next song based on the AudioGuild settings
     */
    private void playNextSong() {
        TrackRequest toPlay;

        if (trackQueue.isEmpty()) {
            currentSong = null;
            return;
        }

        if (loopSong && currentSong != null) {
            toPlay = currentSong;
            toPlay.audioTrack = toPlay.audioTrack.makeClone();

        } else if (shuffling)
            toPlay = trackQueue.remove((int) (Math.random() * trackQueue.size()));

        else
            toPlay = trackQueue.remove(0);

        currentSong = toPlay;
        audioPlayer.playTrack(toPlay.audioTrack);
        audioPlayer.setVolume(currentVolume);
    }

    /**
     * Gets a preview image of the currently playing song
     *
     * @return The URL to the image
     */
    public String getArtwork() {
        AudioTrack audioTrack = currentSong.audioTrack;
        String platform = audioTrack.getSourceManager().getSourceName();

        if (platform.equals("youtube"))
            return "https://i3.ytimg.com/vi/" + audioTrack.getInfo().identifier + "/hqdefault.jpg";
        return null;
    }

    /**
     * Called when a track ends
     */
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // Encountered some sort of error
        if (endReason.mayStartNext)
            playNextSong();
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        yui.getJda().getTextChannelById(this.lastTextChannel).sendMessage(
                "⚠️ " + exception.getMessage()
        ).queue();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        playNextSong();
        MessageChannel channel = yui.getJda().getTextChannelById(lastTextChannel);
        channel.sendMessage("The music source stopped responding, moving to the next track.").queue();
    }

    /**
     * Loads the database state into the AudioGuild
     */
    private void loadFromDB() {
        // Get the document from the database
        MongoCollection<Document> collection = Misty.database.getCollection("audio");
        Document document = collection.find(Filters.eq("_id", Long.toString(guildId))).first();

        // If null we need to create a new document
        if (document == null) {
            document = new Document();
            document.append("_id", Long.toString(guildId));
            document.append("volume", currentVolume);
            collection.insertOne(document);
        }

        // Update our local values
        this.currentVolume = document.getInteger("volume");
    }

    /**
     * Saves the current AudioGuild state to the database
     */
    private void saveToDB() {
        // Update the database
        MongoCollection<Document> collection = Misty.database.getCollection("audio");
        collection.updateOne(Filters.eq("_id", Long.toString(guildId)), new Document("$set", new Document("volume", currentVolume)));
    }
}
