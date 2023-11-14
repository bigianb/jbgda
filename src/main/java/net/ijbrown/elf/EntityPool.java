package net.ijbrown.elf;

import java.util.Hashtable;

public class EntityPool
{
    private static final Hashtable<String, Entity> pool = new Hashtable<>();

    public static void put(Entity entity)
    {
        pool.put(entity.name, entity);
    }

}
