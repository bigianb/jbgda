package net.ijbrown.elf;

public class StrSection extends Section
{
    public String table;

    StrSection(Entity e)
    {
        super(e);
    }

    public int specialize()
    {
        table = new String(data);

        if (entity.fhdr.e_shstrndx == secno) {
            entity.shstrtab = this;
        }
        return 0;
    }

}
