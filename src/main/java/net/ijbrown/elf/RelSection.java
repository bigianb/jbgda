package net.ijbrown.elf;

import java.util.LinkedList;

public class RelSection extends Section
{
    LinkedList<RelEntry> relList;

    RelSection(Entity e)
    {
        super(e);
    }

    public int specialize()
    {
        SectHeader shdr = entity.shdrs.shdr[secno];
        int entries = shdr.sh_size / shdr.sh_entsize;

        relList = new LinkedList<>();

        boolean endian = entity.isSEndian;
        int pos = 0;

        for (int j = 0; j < entries; j++) {
            RelEntry rtmp = new RelEntry();
            rtmp.r_offset = Util.readInt(data, pos, endian);
            pos += 4;
            rtmp.r_info = Util.readInt(data, pos, endian);
            pos += 4;
            relList.add(rtmp);
        }
        return 0;
    }

    static class RelEntry
    {
        public int r_offset;
        public int r_info;
    }
}
