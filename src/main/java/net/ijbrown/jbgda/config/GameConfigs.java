package net.ijbrown.jbgda.config;

import com.google.gson.Gson;
import net.ijbrown.jbgda.loaders.GameType;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class GameConfigs {

    public void read() throws IOException {

        var loader = getClass().getClassLoader();

        URL url = loader.getResource("net/ijbrown/jbgda/config/CHAMPIONS_RTA.json");
        if (url == null){
            throw new IOException("cannot build URL");
        }
        File configFile = null;
        try {
            configFile = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        Gson gson = new Gson();
        rta_game_config = gson.fromJson(new FileReader(configFile), GameConfig.class);
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

