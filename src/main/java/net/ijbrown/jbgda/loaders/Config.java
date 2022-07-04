package net.ijbrown.jbgda.loaders;

public class Config {

    private final GameType gameType;

    public Config(GameType gameType)
    {
        this.gameType = gameType;
    }


    public String getRootDir()
    {
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isMacOs = osName.startsWith("mac os x");

        String rootDir = "/emu/bgda";
        if (isMacOs){
            String home = System.getProperty("user.home");
            rootDir = home + "/ps2_games";
        }
        switch (gameType) {
            case DARK_ALLIANCE -> rootDir += "/DARK_ALLIANCE";
            case CHAMPIONS_OF_NORRATH -> rootDir += "/CHAMPIONS_OF_NORRATH";
            case CHAMPIONS_RTA -> rootDir += "/RTA";
            case JUSTICE_LEAGUE_HEROES -> rootDir += "/JUSTICE_LEAGUE_HEROES";
        }

        return rootDir;
    }

    public String getDataDir()
    {
        String rootDir = getRootDir();
        switch (gameType) {
            case DARK_ALLIANCE, CHAMPIONS_OF_NORRATH, CHAMPIONS_RTA -> rootDir += "/BG/DATA";
            case JUSTICE_LEAGUE_HEROES -> rootDir += "/GAME/DATA";
        }
        return rootDir;
    }
}
