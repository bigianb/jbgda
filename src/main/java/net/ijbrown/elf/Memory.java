package net.ijbrown.elf;

/**
 * Main memory.
 */
public class Memory
{
    public Memory()
    {
        memory = new byte[32 * 1024 * 1024];
    }

    // This should probably be int or long to be more efficient.
    private byte[] memory;

    public void setData(byte[] data, int startAddr)
    {
        System.arraycopy(data, 0, memory, startAddr, data.length);
    }

    public String getString(long address)
    {
        StringBuilder s = new StringBuilder();
        int address32 = (int)(address & 0x1FFFFFFF);
        while (memory[address32] != 0){
            s.append((char) memory[address32]);
            address32++;
        }
        return s.toString();
    }

    public int get8U(long address)
    {
        int address32 = (int)(address & 0x1FFFFFFF);
        return memory[address32]&0xff;
    }

    public int get8(long address)
    {
        int address32 = (int)(address & 0x1FFFFFFF);
        return memory[address32];
    }

    public int get16U(long address)
    {
        int address32 = (int)(address & 0x1FFFFFFF);
        return ((memory[address32]&0xff)|
                ((memory[address32+1]<<8)&0xff00));
    }

    public int get16(long address)
    {
        return (short)get16U(address);
    }

    public int get32(long address)
    {
        if ((address & 0xFFFF0000L) == 0x10000000){
            return readRegister(address);
        }
        int address32 = (int)(address & 0x1FFFFFFF);
        return ((memory[address32]&0xff)|
                ((memory[address32+1]<<8)&0xff00)|
                ((memory[address32+2]<<16)&0xff0000)|
                ((memory[address32+3]<<24)));
    }

    private int readRegister(long address) {
        return 0;
    }

    private void writeRegister(long address, int ival) {
/*
        1000F180h 1h     KPUTCHAR - Console output
        1000F430h 4h     MCH_DRD - RDRAM initialization
        1000F440h 4h     MCH_RICM
        */
        switch ((int)address){
            case 0x1000F180 -> {
                System.out.print((char)ival);
            }
        }
    }

    private void writeGSRegister(long address, long val64) {

    }

    private long readGSRegister(long address) {
        return 0;
    }

    public long get64(long address)
    {
        if ((address & 0xFFFF0000L) == 0x12000000){
            return readGSRegister(address);
        }
        int address32 = (int)(address & 0x1FFFFFFF);
        long val64 = 0;
        for (int i=7; i>=0; --i){
            val64 <<= 8;
            val64 |= (memory[address32+i] & 0xff);
        }
        return val64;
    }

    public void store32(long address, int ival)
    {
        if ((address & 0xFFFF0000L) == 0x10000000){
            writeRegister(address, ival);
        } else {
            int address32 = (int) (address & 0x1FFFFFFF);
            memory[address32] = (byte) (ival & 0xff);
            memory[address32 + 1] = (byte) ((ival >> 8) & 0xff);
            memory[address32 + 2] = (byte) ((ival >> 16) & 0xff);
            memory[address32 + 3] = (byte) ((ival >> 24) & 0xff);
        }
    }

    public void store8(long address, long ival)
    {
        if ((address & 0xFFFF0000L) == 0x10000000){
            writeRegister(address, (int)ival);
        } else {
            int address32 = (int) (address & 0x1FFFFFFF);
            memory[address32] = (byte) (ival & 0xff);
        }
    }

    public void store16(long address, long ival)
    {
        int address32 = (int)(address & 0x1FFFFFFF);
        memory[address32] = (byte)(ival & 0xff);
        memory[address32+1] = (byte)((ival >> 8 ) & 0xff);
    }

    public void store64(long address, long val64)
    {
        if ((address & 0xFFFF0000L) == 0x12000000){
            writeGSRegister(address, val64);
        } else {
            int address32 = (int) (address & 0x1FFFFFFF);
            memory[address32] = (byte) (val64 & 0xff);
            memory[address32 + 1] = (byte) ((val64 >> 8) & 0xff);
            memory[address32 + 2] = (byte) ((val64 >> 16) & 0xff);
            memory[address32 + 3] = (byte) ((val64 >> 24) & 0xff);
            memory[address32 + 4] = (byte) ((val64 >> 32) & 0xff);
            memory[address32 + 5] = (byte) ((val64 >> 40) & 0xff);
            memory[address32 + 6] = (byte) ((val64 >> 48) & 0xff);
            memory[address32 + 7] = (byte) ((val64 >> 56) & 0xff);
        }
    }

    public void storeFloat(long address, float v)
    {
        int address32 = (int)(address & 0x1FFFFFFF);
        int ival = Float.floatToRawIntBits(v);
        memory[address32] = (byte)(ival & 0xff);
        memory[address32+1] = (byte)((ival >> 8 ) & 0xff);
        memory[address32+2] = (byte)((ival >> 16) & 0xff);
        memory[address32+3] = (byte)((ival >> 24) & 0xff);
    }

    public float getFloat(long address)
    {
        int ival = get32(address);
        return Float.intBitsToFloat(ival);
    }

}
