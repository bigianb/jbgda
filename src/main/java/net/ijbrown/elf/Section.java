package net.ijbrown.elf;

public class Section
{
    // https://refspecs.linuxfoundation.org/LSB_2.1.0/LSB-Core-generic/LSB-Core-generic/elftypes.html
    public static final int SHT_PROGBITS = 1;

    Entity entity;
    int secno;
    String name;

    public int getType() {
        return sh_type;
    }

    int sh_type;
    byte[] data;

    Section(Entity e)
    {  //for parseing
        entity = e;
    }

    Section(String n, int stype)
    { //for linking
        name = n;
        sh_type = stype;
    }

    public int specialize()
    {
        return 0;
    }

    public byte[] getData()
    {
        return data;
    }
}
