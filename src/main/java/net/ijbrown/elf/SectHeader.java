package net.ijbrown.elf;

public class SectHeader
{
    public final static int SHT_NULL = 0;
    public final static int SHT_SYMTAB = 2;
    public final static int SHT_STRTAB = 3;
    public final static int SHT_NOBITS = 8;
    public final static int SHT_REL = 9;
    public final static int SHT_DYNSYM = 11;
    public int sh_name;
    public int sh_type;
    public int sh_flags;
    public Address sh_addr;
    public int sh_offset;
    public int sh_size;
    public int sh_link;
    public int sh_info;
    public int sh_addralign;
    public int sh_entsize;
    Entity entity;
    int secno;

    SectHeader(Entity e)
    {
        entity = e;
    }

    public int parse(int sectIndex)
    {
        byte[] buffer = entity.buffer;
        int pos = entity.pos;
        boolean endian = entity.isSEndian;

        sh_name = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_type = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_flags = Util.readInt(buffer, pos, endian);
        pos += 4;
        int addr = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_addr = new Address(addr);
        sh_offset = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_size = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_link = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_info = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_addralign = Util.readInt(buffer, pos, endian);
        pos += 4;
        sh_entsize = Util.readInt(buffer, pos, endian);
        pos += 4;
        entity.pos = pos;
        secno = sectIndex;

        return 0;
    }

}
