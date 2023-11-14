package net.ijbrown.elf;

import java.util.LinkedList;

public class SymSection extends Section
{
    public LinkedList<SymEntry> symbolList;

    SymSection(Entity e)
    {
        super(e);
    }

    public int specialize()
    {
        SectHeader shdr = entity.getSectHeader(secno);
        int entries = shdr.sh_size / shdr.sh_entsize;

        LinkedList<SymEntry> symList = new LinkedList<>();

        boolean endian = entity.isSEndian;
        int pos = 0;

        for (int j = 0; j < entries; j++) {
            SymEntry sym = new SymEntry();
            sym.st_name = Util.readInt(data, pos, endian);
            pos += 4;
            int value = Util.readInt(data, pos, endian);
            pos += 4;
            sym.st_value = new Address(value);
            sym.st_size = Util.readInt(data, pos, endian);
            pos += 4;
            sym.st_info = Util.readByte(data, pos);
            pos++;
            sym.st_other = Util.readByte(data, pos);
            pos++;
            sym.st_shndx = Util.readShort(data, pos, endian);
            pos += 2;

            symList.add(sym);
        }

        symbolList = symList;

        return 0;
    }

    static class SymEntry
    {
        public int st_name;
        public Address st_value;
        public int st_size;
        public short st_shndx;
        byte st_info;
        byte st_other;
    }

}
