package net.ijbrown.jbgda.loaders;

public class PalEntry
{
    public byte r;
    public byte g;
    public byte b;
    public byte a;

    public int argb()
    {
        return (a << 24) |
                ((r << 16) & 0xFF0000) |
                ((g << 8) & 0xFF00) |
                (b & 0xFF);
    }

    public int rgb()
    {
        return  0xFF000000 | ((r << 16) & 0xFF0000) |
                ((g << 8) & 0xFF00) |
                (b & 0xFF);
    }

    public static PalEntry[] readPalette(byte[] fileData, int startOffset, int palw, int palh)
    {
        int numEntries = palw * palh;
        PalEntry[] palette = new PalEntry[numEntries];
        for (int i = 0; i < numEntries; ++i) {
            PalEntry pe = new PalEntry();
            pe.r = fileData[startOffset + i * 4];
            pe.g = fileData[startOffset + i * 4 + 1];
            pe.b = fileData[startOffset + i * 4 + 2];
            pe.a = fileData[startOffset + i * 4 + 3];

            palette[i] = pe;
        }
        return palette;
    }

    public static PalEntry[] unswizzlePalette(PalEntry[] palette)
    {
        if (palette.length == 256) {
            PalEntry[] unswizzled = new PalEntry[palette.length];

            int j = 0;
            for (int i = 0; i < 256; i += 32, j += 32) {
                copy(unswizzled, i, palette, j);
                copy(unswizzled, i + 16, palette, j + 8);
                copy(unswizzled, i + 8, palette, j + 16);
                copy(unswizzled, i + 24, palette, j + 24);
            }
            return unswizzled;
        } else {
            return palette;
        }
    }

    private static void copy(PalEntry[] unswizzled, int i, PalEntry[] swizzled, int j)
    {
        System.arraycopy(swizzled, j, unswizzled, i, 8);
    }
}