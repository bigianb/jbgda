package net.ijbrown.elf;

import java.io.*;

public class Util
{
    static void error()
    {
        System.out.println("Util operation error");
    }

    static void error(String s)
    {
        System.out.println(s);
    }

    public static byte readByte(byte[] buf, int pos)
    {
        return buf[pos];
    }

    public static short readLEShort(byte[] buf, int pos)
    {
        return readShort(buf, pos, true);
    }

    public static short readShort(byte[] buf, int pos, boolean littleEndian)
    {
        if (!littleEndian) {
            error();
            return -1;
        }
        return (short) ((buf[pos++] & 0xff) | (buf[pos] << 8));
    }

    public static int readLEInt(byte[] buf, int pos)
    {
        return readInt(buf, pos, true);
    }


    public static int readInt(byte[] buf, int pos, boolean littleEndian)
    {
        if (!littleEndian) {
            error();
            return -1;
        }
        return ((buf[pos++] & 0xff) |
                ((buf[pos++] << 8) & 0xff00) |
                ((buf[pos++] << 16) & 0xff0000) |
                ((buf[pos] << 24)));
    }

    public static byte[] loadFileintoBuffer(String pathname)
    {
        File file = new File(pathname);
        return loadFileintoBuffer(file);
    }

    public static byte[] loadFileintoBuffer(File file)
    {
        FileInputStream input;

        try {
            input = new FileInputStream(file);
        } catch (FileNotFoundException exc) {
            return null;
        }


        byte[] buffer = new byte[(int) file.length()];

        try {
            int len = buffer.length;
            int offset = 0;
            int n = 0;
            while (offset < len && n >= 0) {

                n = input.read(buffer, offset, len - offset);
                offset += n;
            }
            input.close();
        } catch (IOException e) {
            return null;
        }
        return buffer;
    }

}
