package sh.niall.misty.playlists;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import sh.niall.misty.playlists.containers.PlaylistLookupContainer;
import sh.niall.misty.playlists.containers.PlaylistUrlsContainer;
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

    public static void validatePlaylistName(String name) throws CommandException {
        String nameTrim = name.trim();
        int totalCharacters = nameTrim.replace(" ", "").length();

        if (totalCharacters > nameCharacterMax || totalCharacters < nameCharacterMin)
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

    public static void validatePlaylistDescription(String description) throws CommandException {
        if (description.length() > descriptionCharacterMax)
            throw new CommandException("Playlist descriptions must be no longer than " + descriptionCharacterMax + " characters!");

        if (!description.matches("^[a-zA-Z0-9 ]*$"))
            throw new CommandException("Playlist name must only contain alphanumeric characters (a-Z & 0-9)!");

        if (!description.equals(description.replaceAll(" +", " ")))
            throw new CommandException("Playlist names can't have multiple spaces!");
    }

    public static String getTargetName(Context ctx, long id) {
        Member member = ctx.getGuild().getMemberById(id);
        if (member != null)
            return member.getEffectiveName() + "#" + member.getUser().getDiscriminator();

        User user = ctx.getBot().getUserById(id);
        if (user != null)
            return user.getName() + "#" + user.getDiscriminator();

        return "Unknown User (" + id + ")";
    }

    public static boolean targetDoesntExist(Context ctx, long id) {
        return (ctx.getGuild().getMemberById(id) == null && ctx.getBot().getUserById(id) == null);
    }

    /**
     * Used for the Add and Remove commands
     * Calculates the target id, playlist name and the urls from the arguments
     *
     * @param ctx Context from the command invoked
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

    public static PlaylistLookupContainer getTargetAndName(Context ctx) throws CommandException {
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

    public static String getYoutubeVideoId(String url) {
        Matcher matcher = ytVideoIdPattern.matcher(url);
        if (!matcher.find())
            return null;
        return matcher.group();
    }

    public static String getYoutubePlaylistId(String url) {
        Matcher matcher = ytPlaylistIdPattern.matcher(url);
        if (!matcher.find())
            return null;
        return matcher.group(1);
    }
}
