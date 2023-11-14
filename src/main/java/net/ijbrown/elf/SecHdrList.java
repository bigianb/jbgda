package net.ijbrown.elf;

public class SecHdrList
{
    Entity entity;
    SectHeader[] shdr;

    SecHdrList(Entity e)
    {
        entity = e;
    }

    public int parse()
    {

        if (entity.fhdr.e_shnum == 0) {
            Util.error("No Section Header");
            return 1;
        }

        shdr = new SectHeader[entity.fhdr.e_shnum];
        entity.pos = entity.fhdr.e_shoff;

        for (int i = 0; i < entity.fhdr.e_shnum; i++) {
            SectHeader hdr = new SectHeader(entity);
            hdr.parse(i);
            shdr[i] = hdr;
        }

        return 0;
    }

}
