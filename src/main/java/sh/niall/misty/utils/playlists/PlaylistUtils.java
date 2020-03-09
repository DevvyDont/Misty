package sh.niall.misty.utils.playlists;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import sh.niall.misty.utils.playlists.containers.PlaylistLookupContainer;
import sh.niall.misty.utils.playlists.containers.PlaylistUrlsContainer;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.CommandException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaylistUtils {

    static int nameCharacterMin = 3;
    static int nameCharacterMax = 30;
    static int wordLengthMax = 13;
    static int descriptionCharacterMax = 150;
    static Pattern ytVideoIdPattern = Pattern.compile("(?<=youtu.be/|watch\\?v=|/videos/|embed/)[^#&?]*");
    static Pattern ytPlaylistIdPattern = Pattern.compile("[?&]list=([^#&?]+)*");

    /**
     * Runs validation for a playlist name
     *
     * @param name The string to validate
     * @throws CommandException Thrown if there was an issue with the string
     */
    public static void validatePlaylistName(String name) throws CommandException {
        String nameTrim = name.trim();
        int totalCharacters = nameTrim.replace(" ", "").length();

        if (!(nameCharacterMin <= totalCharacters && totalCharacters <= nameCharacterMax))
            throw new CommandException("Playlist names must be between " + nameCharacterMin + " and " + nameCharacterMax + " characters!");

        if (!name.matches("^[a-zA-Z0-9 ]*$"))
            throw new CommandException("Playlist name must only contain alphanumeric characters (a-Z & 0-9)!");

        if (!name.equals(name.replaceAll(" +", " ")))
            throw new CommandException("Playlist names can't have multiple spaces!");

        for (String word : nameTrim.split(" ")) {
            if (word.length() > wordLengthMax)
                throw new CommandException("Each word in a playlist name can only be " + wordLengthMax + " characters long!");
        }
    }

    /**
     * Runs validation for a playlist description
     *
     * @param description The string to validate
     * @throws CommandException Thrown if there was an issue with the string
     */
    public static void validatePlaylistDescription(String description) throws CommandException {
        if (description.length() > descriptionCharacterMax)
            throw new CommandException("Playlist descriptions must be no longer than " + descriptionCharacterMax + " characters!");

        if (!description.matches("^[a-zA-Z0-9 ]*$"))
            throw new CommandException("Playlist name must only contain alphanumeric characters (a-Z & 0-9)!");

        if (!description.equals(description.replaceAll(" +", " ")))
            throw new CommandException("Playlist names can't have multiple spaces!");
    }

    /**
     * Returns the targets name or unknown user if they can't be found
     *
     * @param ctx The command context
     * @param id  The target ID
     * @return The targets name
     */
    public static String getTargetName(Context ctx, long id) {
        Member member = ctx.getGuild().getMemberById(id);
        if (member != null)
            return member.getEffectiveName();

        User user = ctx.getBot().getUserById(id);
        if (user != null)
            return user.getName() + "#" + user.getDiscriminator();

        return "Unknown User (" + id + ")";
    }

    /**
     * Returns true if the target can't be found
     * (The bot can't see the user)
     *
     * @param ctx The command context
     * @param id  The target ID
     * @return true if they can't be found, false if they can
     */
    public static boolean targetDoesntExist(Context ctx, long id) {
        return (ctx.getGuild().getMemberById(id) == null && ctx.getBot().getUserById(id) == null);
    }

    /**
     * Gets the Target ID, Playlist name and URLs from command arguments
     * Used for the Add and Remove commands
     *
     * @param ctx Context from the command invoked
     * @return The results
     */
    public static PlaylistUrlsContainer getPlaylistAndURLs(Context ctx) throws CommandException {
        // First some validation, we need at least two arguments (name and URL)
        List<String> args = new ArrayList<>(ctx.getArgsStripped()); // Creating new Array so we don't modify the original
        if (args.size() < 2)
            throw new CommandException("Please provide a playlist name and url, see help for more information.");

        // First locate the target
        long targetId = ctx.getAuthor().getIdLong();
        String firstArg = args.get(0);
        if (firstArg.length() < wordLengthMax) {
            String possibleTarget = firstArg.replace("<@!", "").replace("<@", "").replace(">", "");
            if (15 <= possibleTarget.length() && possibleTarget.length() <= 21 && possibleTarget.matches("\\d+")) {
                targetId = Long.parseLong(possibleTarget);
                args.remove(0);
            }
        }

        // Run detection for URLs and find the index of the first one
        List<Url> detectedUrls = new UrlDetector(String.join(" ", args), UrlDetectorOptions.Default).detect();
        if (detectedUrls.isEmpty())
            throw new CommandException("Please provide a URL, see help for more information!");
        int firstUrl = args.indexOf(detectedUrls.get(0).getFullUrl());

        // Substring the arguments and produce a searchable name
        String searchName = String.join(" ", args.subList(0, firstUrl));

        return new PlaylistUrlsContainer(targetId, searchName, detectedUrls);
    }

    /**
     * Gets the Target ID and Playlist name from command arguments
     * Used for the List and Play command
     *
     * @param ctx The Command Context
     * @return The results
     */
    public static PlaylistLookupContainer getTargetAndName(Context ctx) {
        // Set default values
        long targetId = ctx.getAuthor().getIdLong();
        String playlistName = "";
        List<String> args = new ArrayList<>(ctx.getArgsStripped());

        // First work out if we have a possible target
        if (!args.isEmpty()) {
            String possibleTarget = args.get(0).replace("<@!", "").replace("<@", "").replace(">", "");
            int length = possibleTarget.length();
            if (15 <= length && length <= 21 && possibleTarget.matches("\\d+")) {
                targetId = Long.parseLong(possibleTarget);
                args.remove(0);
            }
        }

        // Now work if we have a playlist name
        if (!args.isEmpty()) {
            playlistName = String.join(" ", args);
        }

        // Return it
        return new PlaylistLookupContainer(targetId, playlistName);
    }

    /**
     * Retrieves the Video ID from a Youtube URL
     *
     * @param url The URL to search
     * @return The Video ID, null if none was found
     */
    public static String getYoutubeVideoId(String url) {
        Matcher matcher = ytVideoIdPattern.matcher(url);
        if (!matcher.find())
            return null;
        return matcher.group();
    }

    /**
     * Retrieves the Playlist ID from a Youtube URL
     *
     * @param url The URL to search
     * @return The Playlist ID, null if none was found
     */
    public static String getYoutubePlaylistId(String url) {
        Matcher matcher = ytPlaylistIdPattern.matcher(url);
        if (!matcher.find())
            return null;
        return matcher.group(1);
    }
}
