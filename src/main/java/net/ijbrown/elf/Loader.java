package net.ijbrown.elf;

import java.io.File;

public class Loader
{

    public Entity load(File file)
    {
        Entity entity = new Entity(file.getName());
        entity.buffer = Util.loadFileintoBuffer(file);
        int res = entity.parse();

        if (res == -1) {
            return null;
        }
        EntityPool.put(entity);

        return entity;
    }
}
