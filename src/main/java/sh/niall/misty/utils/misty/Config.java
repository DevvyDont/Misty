package sh.niall.misty.utils.misty;

import java.util.List;

public class Config {

    String discordErrorImage;
    String discordToken;
    List<String> discordPrefixes;

    String mongoURL;

    public String getDiscordErrorImage() {
        return discordErrorImage;
    }

    public String getDiscordToken() {
        return discordToken;
    }

    public String getMongoURL() {
        return mongoURL;
    }

    public List<String> getDiscordPrefixes() {
        return discordPrefixes;
    }
}
