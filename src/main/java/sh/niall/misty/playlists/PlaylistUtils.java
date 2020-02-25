package sh.niall.misty.playlists;

import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.BSON;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.CommandException;

public class PlaylistUtils {

    static int nameCharacterMin = 3;
    static int nameCharacterMax = 30;
    static int wordLengthMax = 13;

    /**
     * Validates the playlist names and returns the "searchName"
     */
    public static void validateName(String name) throws CommandException {
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

    public static String getName(Context ctx, long id) {
        Member member = ctx.getGuild().getMemberById(id);
        if (member != null)
            return member.getEffectiveName()  + "#" + member.getUser().getDiscriminator();

        User user = ctx.getBot().getUserById(id);
        if (user != null)
            return user.getName() + "#" + user.getDiscriminator();

        return "Unknown User (" + id + ")";
    }


}
