package net.ijbrown.jbgda.config;

import com.google.gson.Gson;
import net.ijbrown.jbgda.loaders.GameType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GameConfigs {

    public void read() throws IOException {
        String resourcePath = "/net/ijbrown/jbgda/config/CHAMPIONS_RTA.json";
        try (InputStream stream = GameConfigs.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            Gson gson = new Gson();
            rta_game_config = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), GameConfig.class);
        }
    }

    public GameConfig getGameConfig(GameType gameType)
    {
        if (gameType == GameType.CHAMPIONS_RTA){
            return rta_game_config;
        } else {
            return new GameConfig();
        }
    }

    public GameConfig rta_game_config = null;

}

