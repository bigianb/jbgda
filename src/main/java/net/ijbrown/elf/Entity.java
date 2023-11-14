package net.ijbrown.elf;

public class Entity
{
    public FileHeader fhdr;

    public StrSection shstrtab;
    String name;
    byte[] buffer;
    int pos;
    boolean isSEndian;
    PrgHdrList phdrs;
    SecHdrList shdrs;
    SectionList sects;


    Entity(String n)
    {
        name = n;
    }

    public int parse()
    {
        if (buffer == null) {
            Util.error("File open incorrect");
            return -1;
        }

        fhdr = new FileHeader(this);
        int res = fhdr.parse();
        if (res == -1) {
            Util.error("File Header incorrect");
            return -1;
        }

        phdrs = new PrgHdrList(this);
        res = phdrs.parse();
        if (res == -1) {
            Util.error("Program Headers incorrect");
            return -1;
        }

        shdrs = new SecHdrList(this);
        res = shdrs.parse();
        if (res == -1) {
            Util.error("Section Headers incorrect");
            return -1;
        }

        sects = new SectionList(this);
        res = sects.parse();
        if (res == -1) {
            Util.error("Section Contents incorrect");
            return -1;
        }

        return res;
    }

    public SectHeader getSectHeader(int secno)
    {
        if (secno >= 0 && secno < fhdr.e_shnum)
            return shdrs.shdr[secno];

        Util.error("section header index incorrect");
        return null;
    }

    public Section getSection(int secno)
    {
        if (secno >= 0 && secno < fhdr.e_shnum)
            return sects.sectList.get(secno);

        Util.error("section index incorrect");
        return null;
    }

    public String getSectionNameString(int sectIndex)
    {
        return getString(shstrtab.secno, shdrs.shdr[sectIndex].sh_name);
    }

    public int getSectionCount()
    {
        return sects.sectList.size();
    }

    public String getSectionName(int sectIndex)
    {
        return sects.sectList.get(sectIndex).name;
    }

    public String getString(int tabIndex, int strIndex)
    {

        StrSection sect = (StrSection) sects.sectList.get(tabIndex);

        if (sect == null) {
            Util.error("strtab section incorrect");
            return null;
        }

        if (strIndex < 0 || strIndex >= sect.table.length()) {
            Util.error("Index into strtab incorrect");
            return null;
        }
        if (sect.table.charAt(strIndex) == 0)
            return null;

        int end = sect.table.indexOf(0, strIndex);
        return sect.table.substring(strIndex, end);

    }

}
