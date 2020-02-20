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

    public static void validateName(String name) throws CommandException {
        if (name.length() > nameCharacterMax)
            throw new CommandException("Playlist names must be under " + nameCharacterMax + " characters!");

        if (!name.matches("^[a-zA-Z0-9 ]*$"))
            throw new CommandException("Playlist name must only contain alphanumeric characters (a-Z & 0-9)!");

        if (name.replace(" ", "").length() < nameCharacterMin)
            throw new CommandException("Playlist name must have at least " + nameCharacterMin + " characters!");
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
