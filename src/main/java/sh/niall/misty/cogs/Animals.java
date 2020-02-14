package sh.niall.misty.cogs;

import net.dv8tion.jda.api.EmbedBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Command;
import sh.niall.yui.exceptions.CommandException;

import java.awt.*;
import java.io.IOException;

public class Animals extends Cog {

    OkHttpClient client = new OkHttpClient();
    String[] dogChoices = {"🐶 Woof! 🐶", "🐶 Bark! 🐶", "🐶 Arf! 🐶"};
    String[] catChoices = {"\uD83D\uDC31 Meow! \uD83D\uDC31", "\uD83D\uDC31 Purr! \uD83D\uDC31"};
    String[] catUrls = {"https://cataas.com/c", "https://cataas.com/c/gif"};

    @Command(name = "dog", aliases = {"puppo", "puppos"})
    public void _commandDog(Context context) throws IOException, CommandException {
        // We're doing a request, so send a typing message
        context.getChannel().sendTyping().queue();

        // Request a resource
        Response resourceRequest = client.newCall(new Request.Builder().url("https://random.dog/woof").build()).execute();

        // Ensure we got through
        if (resourceRequest.code() != 200) {
            resourceRequest.close();
            throw new CommandException("There were no available dogs to photograph!");
        }

        // Locate the dog!
        String fileName = resourceRequest.body().string();
        String url = "https://random.dog/" + fileName;
        Response dogRequest = client.newCall(new Request.Builder().url(url).build()).execute();

        // Check the photo exists
        if (dogRequest.code() != 200) {
            resourceRequest.close();
            dogRequest.close();
            throw new CommandException("We found you a dog, but they were a little too shy to see you :(");
        }

        // Videos can't be embed, so we have to upload them differently
        if (fileName.endsWith(".mp4") || fileName.endsWith(".webm")) {
            context.getChannel().sendFile(dogRequest.body().byteStream(), fileName).content("Here's a 🐶 Video:").complete();
        } else {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Dog Photo!");
            embedBuilder.setDescription(dogChoices[(int) (Math.random() * dogChoices.length)]);
            embedBuilder.setColor(Color.cyan);
            embedBuilder.setImage(url);
            context.send(embedBuilder.build());
        }

        // Close the request
        resourceRequest.close();
        dogRequest.close();
    }

    @Command(name = "cat", aliases = {"meow"})
    public void _commandCat(Context context) {
        context.getChannel().sendTyping().queue();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Cat Photo!");
        embedBuilder.setDescription(catChoices[(int) (Math.random() * catChoices.length)]);
        embedBuilder.setColor(Color.cyan);
        embedBuilder.setImage(catUrls[(int) (Math.random() * catUrls.length)]);
        context.send(embedBuilder.build());
    }
}
