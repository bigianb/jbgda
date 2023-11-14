package net.ijbrown.elf;

public class PrgHdrList
{
    Entity entity;
    boolean empty;
    ProgHeader[] phdr;

    PrgHdrList(Entity e)
    {
        entity = e;
    }

    public int parse()
    {
        if (entity.fhdr.e_phnum == 0) {
            empty = true;
            return 1;
        }

        phdr = new ProgHeader[entity.fhdr.e_phnum];
        byte[] buffer = entity.buffer;
        entity.pos = entity.fhdr.e_phoff;
        boolean endian = entity.isSEndian;

        for (int i = 0; i < entity.fhdr.e_phnum; i++) {
            int pos = entity.pos;
            ProgHeader hdr = new ProgHeader();
            hdr.p_type = Util.readInt(buffer, pos, endian);
            pos += 4;
            hdr.p_offset = Util.readInt(buffer, pos, endian);
            pos += 4;
            int addr = Util.readInt(buffer, pos, endian);
            pos += 4;
            hdr.p_vaddr = new Address(addr);
            addr = Util.readInt(buffer, pos, endian);
            pos += 4;
            hdr.p_paddr = new Address(addr);
            hdr.p_filesz = Util.readInt(buffer, pos, endian);
            pos += 4;
            hdr.p_memsz = Util.readInt(buffer, pos, endian);
            pos += 4;
            hdr.p_flags = Util.readInt(buffer, pos, endian);
            pos += 4;
            hdr.p_align = Util.readInt(buffer, pos, endian);
            pos += 4;

            entity.pos = pos;
            phdr[i] = hdr;
        }
        return 0;
    }

    public static class ProgHeader
    {
        public int p_type; //Elf32_Word p_type;
        public int p_offset; //Elf32_Off p_offset;
        public Address p_vaddr; //Elf32_Addr p_vaddr;
        public Address p_paddr; //Elf32_Addr p_paddr;
        public int p_filesz; //Elf32_Word p_filesz;
        public int p_memsz; //Elf32_Word p_memsz;
        public int p_flags; //Elf32_Word p_flags;
        public int p_align; //Elf32_Word p_align;

    }
}
