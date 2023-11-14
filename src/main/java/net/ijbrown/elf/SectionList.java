package net.ijbrown.elf;

import java.util.LinkedList;

public class SectionList
{
    Entity entity;
    LinkedList<Section> sectList;

    SectionList(Entity e)
    {
        entity = e;
        sectList = new LinkedList<>();

        Section sect0 = new Section("", SectHeader.SHT_NULL);
        addSection(sect0);
        e.sects = this;
    }

    public int parse()
    {
        byte[] buffer = entity.buffer;

        for (int i = 1; i < entity.fhdr.e_shnum; i++) {
            SectHeader shdr = entity.shdrs.shdr[i];
            int type = shdr.sh_type;
            int size = shdr.sh_size;

            Section stmp;
            if (type == SectHeader.SHT_STRTAB) {
                stmp = new StrSection(entity);
            } else if (type == SectHeader.SHT_SYMTAB || type == SectHeader.SHT_DYNSYM) {
                stmp = new SymSection(entity);
            } else if (type == SectHeader.SHT_REL) {
                stmp = new RelSection(entity);
            } else {
                stmp = new Section(entity);
            }

            stmp.secno = i;
            stmp.sh_type = type;
            sectList.add(stmp);

            if (type == SectHeader.SHT_NOBITS || size == 0)
                continue;

            stmp.data = new byte[size];
            System.arraycopy(buffer, shdr.sh_offset, stmp.data, 0, size);

            stmp.specialize();
        }

        for (Section stmp : sectList) {
            stmp.name = entity.getSectionNameString(stmp.secno);
        }

        return 0;
    }

    public void addSection(Section s)
    {
        s.secno = sectList.size();
        sectList.add(s);

        s.entity = entity;
    }
}
