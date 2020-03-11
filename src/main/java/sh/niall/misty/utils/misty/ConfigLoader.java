package sh.niall.misty.utils.misty;

import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class ConfigLoader {
    public static Config loadConfig() throws FileNotFoundException {
        Gson gson = new Gson();
        return gson.fromJson(new FileReader("config.json"), Config.class);
    }
}
