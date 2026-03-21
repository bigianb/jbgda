/*  Copyright (C) 2011 Ian Brown

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.ijbrown.jbgda.loaders;

import org.tinylog.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes a worldname.tex file.
 */
public class LevelTexDecode
{
    private final GameType gameType;
    private byte[] fileData;

    private static class TexEntry
    {
        public int cellOffset;
        public int directoryOffset;
        public int size;

        public TexEntry(int cellOffset, int directoryOffset, int size) {

            this.cellOffset = cellOffset;
            this.directoryOffset = directoryOffset;
            this.size = size;
        }
    }

    private final List<TexEntry> texEntries = new ArrayList<>();

    public LevelTexDecode(GameType gameType) {
        this.gameType = gameType;
    }

    public void read(String filename, File dir) throws IOException
    {
        File file = new File(dir, filename);
        read(file);
    }

    public void read(File file) throws IOException
    {
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(file));

        int fileLength = (int) file.length();
        fileData = new byte[fileLength];

        int offset = 0;
        int remaining = fileLength;
        while (remaining > 0) {
            int read = is.read(fileData, offset, remaining);
            if (read == -1) {
                throw new IOException("Read less bytes then expected when reading file");
            }
            remaining -= read;
            offset += read;
        }
        if (gameType != GameType.DARK_ALLIANCE){
            readEntries();
        }
    }

    private void readEntries()
    {
        int offset = 4;
        int cellOffset = 0;
        while (cellOffset >= 0) {
            cellOffset = DataUtil.getLEInt(fileData, offset);
            int dirOffset = DataUtil.getLEInt(fileData, offset + 4);
            int size = DataUtil.getLEInt(fileData, offset + 8);
            offset += 12;
            if (cellOffset >= 0){
                texEntries.add(new TexEntry(cellOffset, dirOffset, size));
            }
        }
    }

    /**
     * Given the offset to the start of a chunk, returns the number of entries in that chunk.
     *
     * @param offset The offset to the start of a chunk.
     * @return The number of entries in that chunk.
     */
    public int getNumEntries(int offset)
    {
        return DataUtil.getLEInt(fileData, offset);
    }

    public void extractAll(File outDirFile) throws IOException
    {
        for (var entry : texEntries){
            int numTexturesInEntry = DataUtil.getLEInt(fileData, entry.directoryOffset);
            for (int i=1; i <= numTexturesInEntry; ++i) {
                int offset = entry.directoryOffset + i * 64;

                File outFile = new File(outDirFile, "leveltex_"+entry.cellOffset + "_" + i + ".png");
                try {
                    extract(outFile, offset, entry.directoryOffset);
                } catch (RuntimeException e) {
                    Logger.warn("Failed to decode {}", outFile);
                    //throw new RuntimeException(e);
                }
            }
        }
    }

    private int convertOffset(int offIn, int segmentStartOffset, int directoryEntryOffset)
    {
        // Dark Alliance encodes pointers as offsets from the entry in the texture entry table.
        // Return to arms (more sensibly) encodes pointers as offsets from the current chunk loaded from the disc.
        if (gameType == GameType.DARK_ALLIANCE){
            return offIn + directoryEntryOffset;
        } else {
            return offIn + segmentStartOffset;
        }
    }

    static class HuffmanTable
    {
        public HuffmanTable()
        {
            values = new ArrayList<>();
        }
        public int lenBytes;
        public int subtracts;
        public List<Integer> values;
    }

    private HuffmanTable decodeHuffman(int offset)
    {
        HuffmanTable table = new HuffmanTable();

        int pos = offset;
        int numBits = DataUtil.getLEInt(fileData, pos);
        table.subtracts = 0x21 - numBits;

        int code2 =  DataUtil.getLEInt(fileData, pos + 8);
        pos += 4;
        while (code2 != -1) {
            int code1 = DataUtil.getLEInt(fileData, pos);
            numBits += 1;

            table.values.add(code1 >> 2);

            code2 =  DataUtil.getLEInt(fileData, pos + 4);
            pos += 8;
        }


        return table;
    }


    private void extractVQ(int pixelWidth, int pixelHeight, int deltaOffset, int compressedDataOffset)
    {
        int v1 = DataUtil.getLEUShort(fileData, compressedDataOffset);
        int v2 = DataUtil.getLEUShort(fileData, compressedDataOffset + 2); // huff table len in words
        int palOffset =  compressedDataOffset + 4;
        if (fileData.length <= compressedDataOffset + 256 * 4){
            Logger.error("not enough bytes to decode image");
            return;
        }
        PalEntry[] palette = PalEntry.readPalette(fileData, palOffset, 16, 16);
        palette = PalEntry.unswizzlePalette(palette);

        // offset to first huffman table
        int off1 = compressedDataOffset + 4 + v1 * 4;
        decodeHuffman(off1);


    }

    public void extract(File outputfile, int offset, int chunkStartOffset) throws IOException
    {
        var deltaOffset = convertOffset(0, chunkStartOffset, offset);

        int pixelWidth = DataUtil.getLEUShort(fileData, offset);
        int pixelHeight = DataUtil.getLEUShort(fileData, offset + 2);
        int header10 = DataUtil.getLEInt(fileData, offset + 0x10);
        int flags = DataUtil.getLEUShort(fileData, offset + 8);

        boolean usesVQCompression = (flags & 0x1) == 0x01;
        boolean flag100 = (flags & 0x100) == 0x0100;

        int compressedDataOffset = header10 + deltaOffset;

        // CHAMPIONS OF NORRATH have flag 1 set whilst BGDA, RTA and JLH do not
        if (usesVQCompression){
            extractVQ(pixelWidth, pixelHeight, deltaOffset, compressedDataOffset);
            return;
        }

        int palOffset = DataUtil.getLEInt(fileData, compressedDataOffset) + deltaOffset;
        if (compressedDataOffset <= 0 || compressedDataOffset >= fileData.length)
        {
            return;
        }
        int decodeOffset = palOffset + 0xc00;

        PalEntry[] palette = PalEntry.readPalette(fileData, palOffset, 16, 16);
        palette = PalEntry.unswizzlePalette(palette);
        HuffVal[] huffVals = decode(decodeOffset);

        int width = (pixelWidth + 0x0f) & ~0x0f;
        int height = (pixelHeight + 0x0f) & ~0x0f;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int p = compressedDataOffset + 4;

        while (fileData[p] >= 0) {
            int x0 = fileData[p];
            int y0 = fileData[p + 1];
            int x1 = fileData[p + 2];
            int y1 = fileData[p + 3];
            p += 4;

            for (int yblock = y0; yblock <=y1; ++yblock) {
                for (int xblock = x0; xblock <=x1; ++xblock) {
                    int blockDataStart = DataUtil.getLEInt(fileData, p) + deltaOffset;
                    decodeBlock(xblock, yblock, blockDataStart, palOffset + 0x400, image, palette, huffVals);
                    p += 4;
                }
            }
        }
        ImageIO.write(image, "png", outputfile);
    }

    private final int[] backJumpTable = new int[]{-1, -16, -17, -15, -2};

    private void decodeBlock(int xblock, int yblock, int blockDataStart, int table0Start, BufferedImage image, PalEntry[] palette, HuffVal[] huffVals)
    {
        int tableOffset = table0Start + 0x800;
        int table1Len = DataUtil.getLEInt(fileData, tableOffset) * 2;
        int table1Start = tableOffset + 4;
        int table2Start = table1Start + table1Len;
        int table3Start = table2Start + 0x48;

        int[] pix8s = new int[16 * 16];
        int curpix8 = 0;
        int startBit = 0;
        int prevPixel = 0;
        for (int y = 0; y < 16; ++y) {
            for (int x = 0; x < 16; ++x) {
                int startWordIdx = startBit / 16;
                int word1 = DataUtil.getLEUShort(fileData, blockDataStart + startWordIdx * 2);
                int word2 = DataUtil.getLEUShort(fileData, blockDataStart + startWordIdx * 2 + 2);
                // if startBit is 0, word == word1
                // if startBit is 1, word is 15 bits of word1 and 1 bit of word2
                int word = ((word1 << 16 | word2) >> (16 - (startBit & 0x0f))) & 0xFFFF;

                int byte1 = (word >> 8) & 0xff;
                HuffVal hv = huffVals[byte1];
                int pixCmd;
                if (hv.numBits != 0) {
                    pixCmd = hv.val;
                    startBit += hv.numBits;
                } else {
                    // Must be more than an 8 bit code
                    int bit = 9;
                    int a = word >> (16 - bit);
                    int v = DataUtil.getLEInt(fileData, table3Start + bit * 4);
                    while (v < a) {
                        ++bit;
                        if (bit > 16) {
                            throw new RuntimeException("A decoding error occured");
                        }
                        a = word >> (16 - bit);
                        v = DataUtil.getLEInt(fileData, table3Start + bit * 4);
                    }
                    startBit += bit;
                    int val = DataUtil.getLEInt(fileData, table2Start + bit * 4);
                    int table1Index = a + val;

                    pixCmd = DataUtil.getLEShort(fileData, table1Start + table1Index * 2);
                }
                int pix8 = 0;
                if (pixCmd < 0x100) {
                    pix8 = pixCmd;
                } else if (pixCmd < 0x105) {
                    int backjump = backJumpTable[pixCmd - 0x100];
                    if ((curpix8 + backjump) >= 0) {
                        pix8 = pix8s[curpix8 + backjump];
                    } else {
                        throw new RuntimeException("Something went wrong");
                    }
                } else {
                    int table0Index = (pixCmd - 0x105) + prevPixel * 8;
                    pix8 = fileData[table0Start + table0Index] & 0xFF;
                }

                pix8s[curpix8++] = pix8;

                prevPixel = pix8 & 0xFF;
                PalEntry pixel = palette[pix8 & 0xFF];
                // Ignore alpha channel for now
                image.setRGB(xblock * 16 + x, yblock * 16 + y, pixel.rgb());
            }
        }
    }

    static class HuffVal
    {
        public short val;
        public short numBits;
    }

    private HuffVal[] decode(int tableOffset)
    {
        HuffVal[] out = new HuffVal[256];

        int table1Len = DataUtil.getLEInt(fileData, tableOffset) * 2;
        int table1Start = tableOffset + 4;
        int table2Start = table1Start + table1Len;
        int table3Start = table2Start + 0x48;

        for (int i = 0; i < 256; ++i) {
            int bit = 1;
            int a = i >> (8 - bit);
            int v = DataUtil.getLEInt(fileData, table3Start + bit * 4);
            while (v < a) {
                ++bit;
                if (bit > 8) {
                    break;
                }
                a = i >> (8 - bit);
                v = DataUtil.getLEInt(fileData, table3Start + bit * 4);
            }
            out[i] = new HuffVal();
            if (bit <= 8) {
                int val = DataUtil.getLEInt(fileData, table2Start + bit * 4);
                int table1Index = a + val;
                out[i].val = DataUtil.getLEShort(fileData, table1Start + table1Index * 2);
                out[i].numBits = (short) bit;
            }
        }

        return out;
    }

}
