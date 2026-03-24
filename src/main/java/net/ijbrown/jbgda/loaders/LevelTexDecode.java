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
                    extract(outFile, offset, entry.directoryOffset, entry.cellOffset);
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
        public int numBits;
        public List<Integer> values;
    }

    private HuffmanTable decodeHuffman(int offset)
    {
        HuffmanTable table = new HuffmanTable();

        int pos = offset;
        int numBits = DataUtil.getLEInt(fileData, pos);
        table.numBits = numBits;
        table.subtracts = 0x21 - numBits;

        pos += 4;
        int code2 =  DataUtil.getLEInt(fileData, pos + 4);

        while (code2 != -1) {
            int code1 = DataUtil.getLEInt(fileData, pos);
            numBits += 1;

            table.values.add(code1 >> 2);

            code2 =  DataUtil.getLEInt(fileData, pos + 4);
            if (code2 != -1) {
                // so we can calculate len bytes.
                pos += 8;
            }
        }
        table.lenBytes = pos + 8 - offset;

        return table;
    }


    private void extractVQ(int pixelWidth, int pixelHeight, int chunkStartOffset, int deltaOffset, int compressedDataOffset, int pageNum)
    {
        // pageNum is 100 * y + x ... so 4849 for example
        // chunkStartOffset is the start of the page data (i.e. the directory)
        int vqPaletteOffset = DataUtil.getLEInt(fileData, compressedDataOffset) + deltaOffset;
        int v1 = DataUtil.getLEUShort(fileData, vqPaletteOffset);
        int v2 = DataUtil.getLEUShort(fileData, vqPaletteOffset + 2); // huff table len in words
        int palOffset =  vqPaletteOffset + 4;
        if (fileData.length <= compressedDataOffset + 256 * 4){
            Logger.error("not enough bytes to decode image");
            return;
        }
        PalEntry[] palette = PalEntry.readPalette(fileData, palOffset, 16, 16);
        palette = PalEntry.unswizzlePalette(palette);

        // offset to first huffman table
        int off1 = palOffset + v1 * 4;
        var table1 = decodeHuffman(off1);

        int off2 = off1 + table1.lenBytes;

        var table2 = decodeHuffman(off2);

        int off3 = off2 + table2.lenBytes;


        int blocksWidth = 1;
        int bw = (pixelWidth + 0xf) >> 4;
        if (bw > 1) {
            do {
                blocksWidth += 1;
            } while (1 << (blocksWidth & 0x1f) <= bw);
        }

        int blocksHeight = 1;
        int bh = (pixelHeight + 0xf) >> 4;
        if (bh > 1) {
            do {
                blocksHeight += 1;
            } while (1 << (blocksHeight & 0x1f) <= bh);
        }

        Logger.info("pixel width = {}, bw = {}, pixel height = {}, bh = {}", pixelWidth, bw, pixelHeight, bh);

        int compressedData2 = compressedDataOffset + 4;

        int iStack_94 = 0x40 - blocksHeight;
        int iStack_98 = 0x40 - blocksWidth;

        long uStack_1e0 = 0;
        int iStack_1d8 = 0;
        while( true ) {
            long x0 = 0;
            long uVar34 = uStack_1e0;
            // read blocksWidth bits from stream into iStack9c
            if (blocksWidth != 0) {
                for (; iStack_1d8 < blocksWidth; iStack_1d8 += 8) {
                    long bVar2 = fileData[compressedData2];
                    int xx = 0x38 - iStack_1d8;
                    compressedData2++;
                    uVar34 |= bVar2 << xx;
                }
                uStack_1e0 = uVar34 << blocksWidth;
                iStack_1d8 -= blocksWidth;
                x0 = (uVar34 >>> iStack_98);
            }
            long y0 = 0;
            uVar34 = uStack_1e0;
            // read blocksHeight bits into y0
            if (blocksHeight != 0) {
                for (; iStack_1d8 < blocksHeight; iStack_1d8 += 8) {
                    long bVar2 = fileData[compressedData2];
                    y0 = 0x38 - iStack_1d8;
                    compressedData2++;
                    uVar34 |= bVar2 << y0;
                }
                uStack_1e0 = uVar34 << blocksHeight;
                iStack_1d8 -= blocksHeight;
                y0 = (uVar34 >>>  iStack_94);
            }
            long x1 = 0;
            uVar34 = uStack_1e0;
            // read blocksWidth bits into x1
            if (blocksWidth != 0) {
                for (; iStack_1d8 < blocksWidth; iStack_1d8 += 8) {
                    long bVar2 = fileData[compressedData2];
                    int yy = 0x38 - iStack_1d8;
                    compressedData2++;
                    uVar34 |= bVar2 << yy;
                }
                uStack_1e0 = uVar34 << blocksWidth;
                iStack_1d8 -= blocksWidth;
                x1 = (uVar34 >>> iStack_98);
            }
            long y1 = 0;
            uVar34 = uStack_1e0;
            // read blocksHeight bits into y1
            if (blocksHeight != 0) {
                for (; iStack_1d8 < (int) blocksHeight; iStack_1d8 += 8) {
                    long bVar2 = fileData[compressedData2];
                    int shift = 0x38 - iStack_1d8;
                    compressedData2++;
                    uVar34 |= bVar2 << shift;
                }
                uStack_1e0 = uVar34 << blocksHeight;
                iStack_1d8 -= blocksHeight;
                y1 = (uVar34 >>>  iStack_94);
            }
            if (x1 < x0) break;

            long y = y0;

            if (y0 < y1){
                do {
                    long x = x0;
                    if (x0 < x1){
                        long pixel_y = y * 0x10;
                        int iStack_64 = 0;
                        long pixel_y_bot = pixel_y + 0x10;
                        // Assume this is chunk start offset.
                        //pcStack_6c = texturePageTable + pageNum;
                        var pcStack_6c = chunkStartOffset;
                        do {
                            //puVar40 = &uStack_1e0;
                            y0 = 0;
                            long iStack_74 = x * 0x10;
                            int sVar3 = pixelWidth;
                            long lVar18 = iStack_74 + 0x10;
                            if (sVar3 <= lVar18){
                                lVar18 = sVar3;
                            }
                            uVar34 = uStack_1e0;
                            var lVar44 = pixel_y_bot;
                            if (pixelHeight <= pixel_y_bot) {
                                lVar44 = pixelHeight;
                            }
                            // read 1 bit from the stream
                            for (; iStack_1d8 < 1; iStack_1d8 += 8) {
                                long bVar2 = fileData[compressedData2];
                                int shift = 0x38 - iStack_1d8;
                                compressedData2++;
                                uVar34 |= bVar2 << shift;
                            }
                            var uVar16 = uVar34 << 1;
                            iStack_1d8 += -1;

                            uStack_1e0 = uVar16;

                            if (uVar34 < 0) {
                                // Bit read is 1
                                // Read 24 bits
                                for (; iStack_1d8 < 0x18; iStack_1d8 += 8) {
                                    long bVar2 = fileData[compressedData2];
                                    compressedData2++;
                                    uVar16 |= bVar2 << (0x38 - iStack_1d8);
                                }
                                uStack_1e0 = uVar16 << 0x18;
                                iStack_1d8 = iStack_1d8 + -0x18;

                                var uVar43 = (uVar16 >>> 0x20);
                                uVar34 = (uVar43 >> 8) & 7;

                                uVar43 >>= 8;
                                var palX = uVar43 >> 3;

                                var cVar1 = fileData[pcStack_6c];
                                /*
                                if (cVar1 < 9) {
                                    pbStack_b4 = (byte *)((int)world->segment_cache +
                                                         (palX & 0x1ffff) +
                                                         (uint)(byte)(&texturePageSegLoc)
                                                                     [((int)uVar38 >> 0x14) + cVar1 * 0x80] * 0x20000);
                                  } else {
                                    pbStack_b4 = (byte *)((int)&texturePageLoc[cVar1] + palX);
                                  }

                                */
                                var pbStack_b4 = chunkStartOffset + (int)palX;

                                //puVar40 = &uStack_1d0;
                                int uStack_b8 = 0;
                                int uStack_c0 = 0;
                                long uStack_1c8 = 0;
                                long uStack_1d0 = 0;
                                int uStack_1c4 = pbStack_b4;

                                if (uVar34 != 0) {
                                    do {
                                        long bVar2 = fileData[uStack_1c4];
                                        uStack_1c4++;
                                        long palY = 0x38 - uStack_1c8;
                                        uStack_1c8 += 8;
                                        uStack_1d0 |= bVar2 << palY;
                                    } while (uStack_1c8 < uVar34);

                                    uStack_1d0 <<= uVar34;
                                    uStack_1c8 -= uVar34;
                                }
                                for (; uStack_1c8 < 1; uStack_1c8 += 8) {
                                    uStack_1d0 |= (long) fileData[uStack_1c4] << (0x38 - uStack_1c8);
                                    uStack_1c4++;
                                }
                                uStack_1d0 = uStack_1d0 << 1;
                                uStack_1c8--;
                            }
                            var lVar42 = pixel_y;

                            /*
                            if (*puStack_a8 == 0) {
                                for (; lVar42 < lVar44; lVar42 = (long)((int)lVar42 + 2)) {
                                    palY = y0 * 0x20;
                                    iVar19 = palY + 0x10;
                                    y0 += 1;
                                    for (iVar37 = iStack_74; iVar37 < lVar18; iVar37 += 2) {
                                        puVar39 = auStack_1c0 + iVar19;
                                        auStack_1c0[palY] = huffmanTemp;
                                        iVar20 = palY + 1;
                                        iVar7 = iVar19 + 1;
                                        palY += 2;
                                        auStack_1c0[iVar20] = DAT_00355d01;
                                        iVar19 += 2;
                                        *puVar39 = DAT_00355d02;
                                        auStack_1c0[iVar7] = DAT_00355d03;
                                    }
                                }
                            } else {
                                for (; lVar42 < lVar44; lVar42 = (long)((int)lVar42 + 2)) {
                                    lVar45 = (long)iStack_74;
                                    palY = y0 * 0x20;
                                    iVar19 = palY + 0x10;
                                    y0 += 1;
                                    if (lVar45 < lVar18) {
                                        do {
                                            if ((int)puVar40[1] < 0x20) {
                                                do {
                                                    pbVar15 = *(byte **)((int)puVar40 + 0xc);
                                                    uVar34 = puVar40[1];
                                                    bVar2 = *pbVar15;
                                                    iVar37 = (int)uVar34 + 8;
                                                    *(byte **)((int)puVar40 + 0xc) = pbVar15 + 1;
                                                    *(int *)(puVar40 + 1) = iVar37;
                                                    *puVar40 = *puVar40 | (ulong)bVar2 << (long)(0x38 - (int)uVar34);
                                                } while (iVar37 < 0x20);
                                                iVar37 = (int)puVar40[1];
                                            } else {
                                                iVar37 = (int)puVar40[1];
                                            }
                                            uVar34 = *puVar40;
                                            *(int *)(puVar40 + 1) = iVar37 + -0x20;
                                            uVar43 = (uint)(uVar34 >> 0x20);
                                            lVar25 = (long)(int)(uVar43 >> 2);
                                            iVar20 = 0;
                                            auVar26 = _pextlw(lVar25,lVar25);
                                            auVar26 = _pextlw(auVar26._0_8_,auVar26._0_8_);
                                            puVar38 = &DAT_0037dd90;
                                            uVar28 = gbits;
                                            uVar30 = DAT_0037dd84;
                                            uVar31 = DAT_0037dd88;
                                            uVar33 = DAT_0037dd8c;
                                            do {
                                                auVar10._4_4_ = uVar30;
                                                auVar10._0_4_ = uVar28;
                                                auVar10._8_4_ = uVar31;
                                                auVar10._12_4_ = uVar33;
                                                auVar36 = _pcgtw(auVar10,auVar26);
                                                auVar36 = _ppach(in_zero_qw,auVar36);
                                                auVar36 = _ppacb(in_zero_qw,auVar36);
                                                uVar28 = *puVar38;
                                                uVar30 = puVar38[1];
                                                uVar31 = puVar38[2];
                                                uVar33 = puVar38[3];
                                                uVar35 = _plzcw(auVar36._0_8_ >> 1);
                                                puVar38 = puVar38 + 4;
                                                palX = (int)uVar35 + 1U >> 3;
                                                iVar20 += palX;
                                            } while (-1 < (int)(palX - 4));
                                            palX = gsubtracts - iVar20;
                                            *puVar40 = uVar34 << 0x20;
                                            iVar20 = (&gsubtracts)[palX];
                                            if ((long)(int)palX != 0) {
                                                *(uint *)(puVar40 + 1) = iVar37 + -0x20 + palX;
                                                *puVar40 = (uVar34 << 0x20) >> (long)(int)palX |
                                                        ((long)uVar34 >> 0x20 & ~(long)(-1 << (palX & 0x1f)) & 0xffffffff) << (long)(int)(0x40 - palX);
                                            }
                                            iVar20 = ((uVar43 >> (palX & 0x1f)) - iVar20) * 4;
                                            auStack_1c0[palY] = (&huffmanTemp)[iVar20];
                                            puVar39 = auStack_1c0 + iVar19;
                                            lVar45 = (long)((int)lVar45 + 2);
                                            auStack_1c0[palY + 1] = (&DAT_00355d01)[iVar20];
                                            iVar37 = iVar19 + 1;
                                            palY += 2;
                                            iVar19 += 2;
                                            *puVar39 = (&DAT_00355d02)[iVar20];
                                            auStack_1c0[iVar37] = (&DAT_00355d03)[iVar20];
                                        } while (lVar45 < lVar18);
                                    }
                                }
                            }
                            */

                            /*
                            y0 = 0;
                            iVar19 = 0;
                            palY = 0;
                            do {
                                iVar37 = 7;
                                puVar39 = (undefined1 *)((iStack_64 * 8 + palY) * 4 + (int)puStack_78);
                                do {
                                    pbVar15 = &unscrambleTable + y0;
                                    pbVar22 = &DAT_006e9081 + y0;
                                    iVar37 += -1;
                                    pbVar24 = &DAT_006e9082 + y0;
                                    pbVar29 = &DAT_006e9083 + y0;
                                    y0 += 4;
                                    *puVar39 = auStack_1c0[*pbVar15];
                                    puVar39[1] = auStack_1c0[*pbVar22];
                                    puVar39[2] = auStack_1c0[*pbVar24];
                                    puVar39[3] = auStack_1c0[*pbVar29];
                                    puVar39 = puVar39 + 4;
                                } while (-1 < iVar37);
                                iVar19 += 1;
                                palY += iStack_84;
                            } while (iVar19 < 8);
                            */

                            x++;
                            iStack_64++;
                        } while (x < x1);
                    }
                    ++y;
                } while (y < y1);
            }
        }
    }

    public void extract(File outputfile, int offset, int chunkStartOffset, int cellOffset) throws IOException
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
            extractVQ(pixelWidth, pixelHeight, chunkStartOffset, deltaOffset, compressedDataOffset, cellOffset);
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
