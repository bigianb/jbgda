package net.ijbrown.elf;

public class Address
{
    int address;

    Address(int addr)
    {
        address = addr;
    }

    void add(int offset)
    {
        address += offset;
    }

    public int value()
    {
        return address;
    }
}
