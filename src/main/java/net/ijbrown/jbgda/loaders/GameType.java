package net.ijbrown.jbgda.loaders;

/**
 * Identifies the game. Different games have slightly different data structures.
 */
public enum GameType
{
    DARK_ALLIANCE("Dark Alliance"),
    CHAMPIONS_OF_NORRATH("Champions of Norrath"),
    CHAMPIONS_RTA("Return to Arms"),
    JUSTICE_LEAGUE_HEROES("Justice League Heroes");

    public String getName() {
        return name;
    }

    private final String name;

    GameType(String name){
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
