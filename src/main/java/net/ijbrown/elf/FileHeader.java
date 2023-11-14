package net.ijbrown.elf;

public class FileHeader
{
    public final static int EI_NIDENT = 16;
    public static final int EI_MAG0 = 0; //File identification
    public static final int EI_MAG1 = 1; //File identification
    public static final int EI_MAG2 = 2; //File identification
    public static final int EI_MAG3 = 3; //File identification
    public static final int EI_CLASS = 4; //File class
    public static final int EI_DATA = 5; //Data encoding

    public static final byte ELFMAG0 = 0x7f; //e_ident[EI_MAG0]
    public static final byte ELFMAG1 = 'E'; //e_ident[EI_MAG1]
    public static final byte ELFMAG2 = 'L'; //e_ident[EI_MAG2]
    public static final byte ELFMAG3 = 'F'; //e_ident[EI_MAG3]
    public static final int ELFCLASS32 = 1; //32-bit objects
    public static final int ELFDATA2LSB = 1; //small endian
    public static int headerSize = 52;
    public short e_type;
    public short e_machine;
    public int e_version;
    //public static final int RESERVED 11-16 Reserved for future use
    public Address e_entry;
    public int e_phoff;
    public int e_shoff;
    public int e_flags;
    public short e_ehsize;
    public short e_phentsize;
    public short e_phnum;
    public short e_shentsize;
    public short e_shnum;
    public short e_shstrndx;
    Entity entity;
    FileHeader(Entity e)
    {
        entity = e;
    }

    int parse()
    {
        byte[] buffer = entity.buffer;
        if (buffer[EI_MAG0] != ELFMAG0 ||
                buffer[EI_MAG1] != ELFMAG1 ||
                buffer[EI_MAG2] != ELFMAG2 ||
                buffer[EI_MAG3] != ELFMAG3) {
            System.out.println("Not ELF file");
            return -1;
        }

        if (buffer[EI_CLASS] != ELFCLASS32) {
            System.out.println("Not 32-bit file");
            return -1;
        }
        entity.isSEndian = (buffer[EI_DATA] == ELFDATA2LSB);
        int pos = EI_NIDENT;
        e_type = Util.readShort(buffer, pos, entity.isSEndian);
        pos += 2;
        e_machine = Util.readShort(buffer, pos, entity.isSEndian);
        pos += 2;
        e_version = Util.readInt(buffer, pos, entity.isSEndian);
        pos += 4;
        int entry = Util.readInt(buffer, pos, entity.isSEndian);
        pos += 4;
        e_entry = new Address(entry);
        e_phoff = Util.readInt(buffer, pos, entity.isSEndian);
        pos += 4;
        e_shoff = Util.readInt(buffer, pos, entity.isSEndian);
        pos += 4;
        e_flags = Util.readInt(buffer, pos, entity.isSEndian);
        pos += 4;
        e_ehsize = Util.readShort(buffer, pos, entity.isSEndian);
        pos += 2;
        e_phentsize = Util.readShort(buffer, pos, entity.isSEndian);
        pos += 2;
        e_phnum = Util.readShort(buffer, pos, entity.isSEndian);
        pos += 2;
        e_shentsize = Util.readShort(buffer, pos, entity.isSEndian);
        pos += 2;
        e_shnum = Util.readShort(buffer, pos, entity.isSEndian);
        pos += 2;
        e_shstrndx = Util.readShort(buffer, pos, entity.isSEndian);

        entity.pos = FileHeader.headerSize;
        return 0;
    }
}

