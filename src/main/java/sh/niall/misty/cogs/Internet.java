package sh.niall.misty.cogs;

import net.dv8tion.jda.api.EmbedBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import sh.niall.misty.utils.settings.UserSettings;
import sh.niall.misty.utils.ui.Helper;
import sh.niall.misty.utils.ui.paginator.Paginator;
import sh.niall.yui.cogs.Cog;
import sh.niall.yui.commands.Context;
import sh.niall.yui.commands.interfaces.Command;
import sh.niall.yui.exceptions.CommandException;
import sh.niall.yui.exceptions.WaiterException;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Internet extends Cog {

    OkHttpClient client = new OkHttpClient();
    final String[] dogChoices = {"🐶 Woof! 🐶", "🐶 Bark! 🐶", "🐶 Arf! 🐶"};
    final String[] catChoices = {"\uD83D\uDC31 Meow! \uD83D\uDC31", "\uD83D\uDC31 Purr! \uD83D\uDC31"};
    final String[] foxChoices = {"\uD83E\uDD8A Wa-pa-pa-pa-pa-pa-pow! \uD83E\uDD8A", "\uD83E\uDD8A Hatee-hatee-hatee-ho! \uD83E\uDD8A", "\uD83E\uDD8A Joff-tchoff-tchoffo-tchoffo-tchoff! \uD83E\uDD8A", "\uD83E\uDD8A Fraka-kaka-kaka-kaka-kow! \uD83E\uDD8A"};
    final DateTimeFormatter urbanInput = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            embedBuilder.setColor(Helper.randomColor());
            embedBuilder.setImage(url);
            context.send(embedBuilder.build());
        }

        // Close the request
        resourceRequest.close();
        dogRequest.close();
    }

    @Command(name = "cat", aliases = {"meow"})
    public void _commandCat(Context context) throws IOException, CommandException {
        context.getChannel().sendTyping().queue();

        // Request a resource
        Response resourceRequest = client.newCall(new Request.Builder().url("https://api.thecatapi.com/v1/images/search").build()).execute();

        // Ensure we got through
        if (resourceRequest.code() != 200) {
            resourceRequest.close();
            throw new CommandException("There were no available cats to photograph!");
        }

        // Covert string to JSON
        JSONObject jsonObject = (new JSONArray(resourceRequest.body().string())).getJSONObject(0);

        // Create the embed
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Cat Photo!");
        embedBuilder.setDescription(catChoices[(int) (Math.random() * catChoices.length)]);
        embedBuilder.setColor(Helper.randomColor());
        embedBuilder.setImage((String) jsonObject.get("url"));
        context.send(embedBuilder.build());
    }

    @Command(name = "fox")
    public void _commandFox(Context context) throws IOException, CommandException {
        context.getChannel().sendTyping().queue();

        // Request a resource
        Response resourceRequest = client.newCall(new Request.Builder().url("https://randomfox.ca/floof").build()).execute();

        // Ensure we got through
        if (resourceRequest.code() != 200) {
            resourceRequest.close();
            throw new CommandException("There were no available foxes to photograph!");
        }

        // Covert string to JSON
        JSONObject jsonObject = new JSONObject(resourceRequest.body().string());

        // Create the embed
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Fox Photo!");
        embedBuilder.setDescription(foxChoices[(int) (Math.random() * foxChoices.length)]);
        embedBuilder.setColor(Helper.randomColor());
        embedBuilder.setImage((String) jsonObject.get("image"));
        context.send(embedBuilder.build());
    }

    @Command(name = "urbandictionary", aliases = {"ud", "urban"})
    public void _commandUrban(Context ctx) throws CommandException, IOException, WaiterException, ParseException {
        if (ctx.getArgsStripped().isEmpty())
            throw new CommandException("Please specify a word to search!");

        String word = String.join("+", ctx.getArgsStripped());
        String query = "http://api.urbandictionary.com/v0/define?term=" + word;
        Response response = client.newCall(new Request.Builder().url(query).build()).execute();
        if (response.code() != 200) {
            response.close();
            throw new CommandException("There was an error looking up your word.");
        }

        List<EmbedBuilder> embedBuilders = new ArrayList<>();
        UserSettings userSettings = new UserSettings(ctx);
        for (Object object : new JSONObject(response.body().string()).getJSONArray("list")) {
            JSONObject jsonObject = (JSONObject) object;
            EmbedBuilder embedBuilder = new EmbedBuilder();
            String date = jsonObject.getString("written_on").replace("T", " ").split("\\.")[0];
            embedBuilder.setTitle("Urban Dictionary");
            embedBuilder.setDescription("Word: " + word);
            embedBuilder.setAuthor(UserSettings.getName(ctx), null, ctx.getUser().getEffectiveAvatarUrl());
            embedBuilder.addField("Definition:", jsonObject.getString("definition"), false);
            embedBuilder.addField("Example:", jsonObject.getString("example"), false);
            embedBuilder.addField("Author:", jsonObject.getString("author"), true);
            embedBuilder.addField("Thumbs Up/Down", String.format("\uD83D\uDC4D %s \uD83D\uDC4E %s", jsonObject.getInt("thumbs_up"), jsonObject.getInt("thumbs_down")), true);
            embedBuilder.addField("Written on:", userSettings.getShortDate(LocalDateTime.parse(date, urbanInput).toEpochSecond(ZoneOffset.UTC)), true);
            embedBuilders.add(embedBuilder);
        }
        response.close();

        if (embedBuilders.isEmpty())
            ctx.send("I found no results for the word: " + word);
        else
            new Paginator(ctx, embedBuilders, 160, true).run();
    }
}
