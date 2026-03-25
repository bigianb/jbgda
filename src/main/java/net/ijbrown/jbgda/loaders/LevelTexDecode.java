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
            huffman = new ArrayList<>();
            gbits = new int[32];
            subtracts = new int[33];
        }
        public int lenBytes;
        public int[] subtracts;
        public int numBitsOrig;
        public List<Integer> values;
        public int[] gbits;
        public List<Byte> huffman;

        public void buildGbits()
        {
            while (values.size() < 0x20){
                values.add(0x7fffffff);
            }

            int idx = 0;
            while (idx < 0x20){
                for (int i=0; i<4; ++i){
                    int v = values.get(idx + 3 - i);
                    gbits[idx + i] = v;
                }
                idx += 4;
            }
        }

        public void buildHuffman(int targetLen, int dataOffset, byte[] fileData)
        {
            long bitBuffer = 0;

            int puStack_1e4 = dataOffset;
            huffman.clear();
            for (int i=0; i < targetLen; ++i){
                huffman.add((byte) 0);
            }
            if (numBitsOrig > 0) {
                int y0 = 0;
                if (targetLen != 0) {
                    int uVar28 = 0;
                    int iStack_1e8 = 0;
                    do {  // y0 < targetLen
                        int iVar19 = 0;
                        do {  // iVar19 < 4
                            int huffIndex = iVar19 + y0 * 4;
                            for (; iStack_1e8 < 0x20; iStack_1e8 += 8) {
                                byte b = fileData[puStack_1e4++];
                                bitBuffer |= (long)b << (long)(0x38 - iStack_1e8);
                            }
                            iStack_1e8 -= 0x20;

                            int uVar34 = (int)(bitBuffer >>> 0x20);
                            int lVar18 = uVar34 >>> 2;

                            long sumNumLeadingZeroBytes = 0;

                            // auVar26 is a 128 bit value with lVar18 repeated 4 times.

                            //auVar26 = _pextlw(lVar18,lVar18);
                            //auVar26 = _pextlw(auVar26._0_8_,auVar26._0_8_);

                            int puVar38 = 0;
                            int numLeadingZeroBytes = 0;
                            do {
                                int uVar30 = gbits[puVar38 + 0];
                                int uVar31 = gbits[puVar38 + 1];
                                int uVar32 = gbits[puVar38 + 2];
                                int uVar33 = gbits[puVar38 + 3];

                                // auVar36 = 128 bit of the huffman code.
                                // _3 is the high word.

                                int auVar36_3 = uVar33;
                                int auVar36_2 = uVar32;
                                int auVar36_1 = uVar31;
                                int auVar36_0 = uVar30;

                                //auVar36 = _pcgtw(auVar36,auVar26);
                                auVar36_3 = auVar36_3 > lVar18 ? -1 : 0;
                                auVar36_2 = auVar36_2 > lVar18 ? -1 : 0;
                                auVar36_1 = auVar36_1 > lVar18 ? -1 : 0;
                                auVar36_0 = auVar36_0 > lVar18 ? -1 : 0;

                                // Pack the lower 16 bits of each _3, _2, _1, _0 into _1 and _0
                                //auVar36 = _ppach(in_zero_qw,auVar36);
                                // Pack the lower 64 bits into the lower 32 bits.
                                //auVar36 = _ppacb(in_zero_qw,auVar36);

                                // parallel leading zero count word - 1
                                //int uVar35 = _plzcw(auVar36._0_8_ >> 1);
                                //numLeadingZeroBytes = uVar35 + 1 >> 3;

                                numLeadingZeroBytes = 0;
                                if (auVar36_3 == 0){
                                    ++numLeadingZeroBytes;
                                    if (auVar36_2 == 0){
                                        ++numLeadingZeroBytes;
                                        if (auVar36_1 == 0){
                                            ++numLeadingZeroBytes;
                                            if (auVar36_0 == 0){
                                                ++numLeadingZeroBytes;
                                            }
                                        }
                                    }
                                }
                                sumNumLeadingZeroBytes += numLeadingZeroBytes;

                                puVar38 += 4;

                            } while (numLeadingZeroBytes > 3);

                            // Shift out the 32 bits in uVar34
                            bitBuffer <<= 0x20;
                            int subtractsIdx = subtracts[0] - (int)sumNumLeadingZeroBytes;
                            int safeIndex = subtractsIdx & 0x1F;
                            if (subtractsIdx != 0) {
                                // add subtractsIdx bits back into the read buffer
                                iStack_1e8 += subtractsIdx;     // bits read into bit buffer
                                int bbLeftShift = 0x40 - subtractsIdx;
                                bitBuffer >>>= subtractsIdx;
                                // only keep the least significant safeIndex bits.
                                long mask = (~(-1 << safeIndex));
                                long keep = uVar34 & mask;
                                bitBuffer |= keep << bbLeftShift;
                            }

                            int huffval = (uVar34 >>> safeIndex) - subtracts[subtractsIdx];
                            huffman.set(huffIndex, (byte) huffval);

                            iVar19 += 1;
                        } while (iVar19 < 4);
                        y0 += 1;
                    } while (y0 < targetLen);
                }
            }

        }
    }

    private HuffmanTable decodeHuffman(int offset)
    {
        HuffmanTable table = new HuffmanTable();

        int pos = offset;
        int numBits = DataUtil.getLEInt(fileData, pos) & 0x1F;
        table.numBitsOrig = numBits;
        table.subtracts[0] = 33 - numBits;

        pos += 4;
        int code2 =  DataUtil.getLEInt(fileData, pos + 4);

        while (code2 != -1) {
            int code1 = DataUtil.getLEInt(fileData, pos);

            table.values.add(code1 >>> 2);
            int shiftVal = (32 - numBits) & 0x1f;
            table.subtracts[32 - numBits] = code1 >>> shiftVal;

            code2 =  DataUtil.getLEInt(fileData, pos + 4);
            if (code2 != -1) {
                // so we can calculate len bytes.
                pos += 8;
            }
            numBits += 1;
        }
        table.lenBytes = pos + 8 - offset;

        table.buildGbits();
        return table;
    }

    static class BitstreamState
    {
        public BitstreamState(byte[] data, int pos) {
            this.data = data;
            streamPos = pos;
            bitBuffer = 0;
            bitsInBuffer = 0;
        }

        void setStreamPos(int pos)
        {
            streamPos = pos;
            bitBuffer = 0;
            bitsInBuffer = 0;
        }

        long readBits(int num)
        {
            if (num <= 0){
                return 0;
            }
            while (bitsInBuffer < num) {
                long bVar2 = data[streamPos++];
                int xx = 0x38 - bitsInBuffer;
                bitBuffer |= bVar2 << xx;
                bitsInBuffer += 8;
            }

            long rval = bitBuffer >>> (64 - num);

            bitsInBuffer -= num;
            bitBuffer <<= num;
            return rval;
        }

        long bitBuffer;
        int bitsInBuffer;
        int streamPos;
        byte[] data;
    }

    private void extractVQ(File outputfile, int pixelWidth, int pixelHeight, int chunkStartOffset, int deltaOffset, int compressedDataOffset, int pageNum) throws IOException {
        byte[] auStack_1c0 = new byte[256];

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);

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

        int off3 = off2 + table2.lenBytes;          // stream1
        table1.buildHuffman(v2, off3, fileData);

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

        long uStack_1e0 = 0;
        BitstreamState bitstreamState2 = new BitstreamState(fileData, compressedDataOffset + 4);

        while( true ) {
            long x0 = bitstreamState2.readBits(blocksWidth);
            long y0 = bitstreamState2.readBits(blocksHeight);
            long x1 = bitstreamState2.readBits(blocksWidth);
            long y1 = bitstreamState2.readBits(blocksHeight);

            if (x1 < x0) break;

            long y = y0;

            while (y < y1) {
                long x = x0;
                int iStack_64 = 0;
                long pixel_y = y * 0x10;

                long pixel_y_bot = pixel_y + 0x10;
                while (x < x1) {
                    //puVar40 = &uStack_1e0;
                    var currentBitstream = bitstreamState2;
                    BitstreamState bitstreamState3 = new BitstreamState(fileData, 0);
                    y0 = 0;
                    long iStack_74 = x * 0x10;
                    int sVar3 = pixelWidth;
                    long lVar18 = iStack_74 + 0x10;
                    if (sVar3 <= lVar18){
                        lVar18 = sVar3;
                    }
                    long uVar34 = uStack_1e0;
                    var lVar44 = pixel_y_bot;
                    if (pixelHeight <= pixel_y_bot) {
                        lVar44 = pixelHeight;
                    }
                    var flag = bitstreamState2.readBits(1);

                    if (flag != 0) {
                        // Read 24 bits
                        var bits24 = bitstreamState2.readBits(24);
                        var pageOffset = (int)bits24 >>> 3;
                        int skipBits = (int)(bits24 & 0x07);

                        bitstreamState3.setStreamPos(chunkStartOffset + pageOffset);
                        currentBitstream = bitstreamState3;

                        if (skipBits != 0) {
                            bitstreamState3.readBits(skipBits);
                        }
                        bitstreamState3.readBits(1);
                    }
                    var lVar42 = pixel_y;

                    // data following second huffman table
                    if (DataUtil.getLEInt(fileData, off3) == 0) {
                        // use huffman table 1
                        for (; lVar42 < lVar44; lVar42 = ((int)lVar42 + 2)) {
                            int ytop = (int)y0 * 0x20;
                            int ybot = ytop + 0x10;
                            y0 += 1;
                            for (long iVar37 = iStack_74; iVar37 < lVar18; iVar37 += 2) {
                                auStack_1c0[ytop] = table1.huffman.get(0);
                                auStack_1c0[ytop + 1] = table1.huffman.get(1);
                                auStack_1c0[ybot] = table1.huffman.get(2);
                                auStack_1c0[ybot + 1] = table1.huffman.get(3);

                                ybot += 2;
                                ytop += 2;
                            }
                        }
                    } else {
                        // use huffman table 2 to find the entry in huffman table 1
                        for (; lVar42 < lVar44; lVar42 = ((int)lVar42 + 2)) {
                            long lVar45 = iStack_74;
                            int palY = (int)y0 * 0x20;
                            int iVar19 = palY + 0x10;
                            y0 += 1;
                            if (lVar45 < lVar18) {
                                do {
                                    long bits32 = currentBitstream.readBits(32);

                                    long lVar25 = (bits32 >>> 2);
                                    int sumNumLeadingZeroBytes = 0;

                                    int puVar38 = 0;
                                    int numLeadingZeroBytes = 0;
                                    do {
                                        int uVar30 = table2.gbits[puVar38 + 0];
                                        int uVar31 = table2.gbits[puVar38 + 1];
                                        int uVar32 = table2.gbits[puVar38 + 2];
                                        int uVar33 = table2.gbits[puVar38 + 3];

                                        int auVar36_3 = uVar33 > lVar25 ? -1 : 0;
                                        int auVar36_2 = uVar32 > lVar25 ? -1 : 0;
                                        int auVar36_1 = uVar31 > lVar25 ? -1 : 0;
                                        int auVar36_0 = uVar30 > lVar25 ? -1 : 0;

                                        numLeadingZeroBytes = 0;
                                        if (auVar36_3 == 0){
                                            ++numLeadingZeroBytes;
                                            if (auVar36_2 == 0){
                                                ++numLeadingZeroBytes;
                                                if (auVar36_1 == 0){
                                                    ++numLeadingZeroBytes;
                                                    if (auVar36_0 == 0){
                                                        ++numLeadingZeroBytes;
                                                    }
                                                }
                                            }
                                        }
                                        sumNumLeadingZeroBytes += numLeadingZeroBytes;
                                        puVar38 = puVar38 + 4;
                                    } while (numLeadingZeroBytes > 3);
                                    int subtractsIdx = table2.subtracts[0] - sumNumLeadingZeroBytes;
                                    int safeIndex = subtractsIdx & 0x1F;

                                    // push back into the stream.
                                    if (subtractsIdx != 0) {
                                        currentBitstream.bitsInBuffer += subtractsIdx;
                                        currentBitstream.bitBuffer >>>= subtractsIdx;   // make space
                                        int bbLeftShift = 0x40 - subtractsIdx;
                                        long mask = (~(-1 << safeIndex));
                                        long keep = bits32 & mask;
                                        currentBitstream.bitBuffer |= keep << bbLeftShift;
                                    }

                                    int huffIndex = (((int)bits32 >>> safeIndex) - table2.subtracts[subtractsIdx]) * 4;

                                    auStack_1c0[palY] = table1.huffman.get(huffIndex);
                                    auStack_1c0[palY + 1] = table1.huffman.get(huffIndex+1);
                                    auStack_1c0[iVar19] = table1.huffman.get(huffIndex+2);
                                    auStack_1c0[iVar19 + 1] = table1.huffman.get(huffIndex+3);

                                    lVar45 += 2;
                                    palY += 2;
                                    iVar19 += 2;
                                } while (lVar45 < lVar18);
                            }
                        }
                    }

                    /*
                      palY = 0;
                      blockY = 0;
                      yy = 0;
                      do {
                        iVar19 = 7;
                        // output
                        pbVar34 = (byte *)((iStack_64 * 8 + yy) * 4 + (int)apuStack_210[0]);
                        do {
                          pbVar14 = unscrambleTable + palY;
                          iVar38 = palY + 1;
                          iVar19 += -1;
                          iVar24 = palY + 2;
                          iVar25 = palY + 3;
                          palY += 4;
                          *pbVar34 = auStack_1c0[*pbVar14];
                          pbVar34[1] = auStack_1c0[unscrambleTable[iVar38]];
                          pbVar34[2] = auStack_1c0[unscrambleTable[iVar24]];
                          pbVar34[3] = auStack_1c0[unscrambleTable[iVar25]];
                          pbVar34 = pbVar34 + 4;
                        } while (-1 < iVar19);
                        blockY += 1;
                        yy += width * 8;
                      } while (blockY < 8);
                     */

                    int idx= 0;
                    for (int blockY=0; blockY < 8; blockY++) {
                        for (int blockX = 0; blockX < 8 * 4; blockX++) {
                            byte pix8 = auStack_1c0[idx++];
                            PalEntry pixel = palette[pix8 & 0xFF];
                            image.setRGB((int)x * 16 + blockX, (int)y * 16 + blockY, pixel.rgb());
                        }
                    }

                    x++;
                    iStack_64++;
                }

                ++y;

            }
        }
        ImageIO.write(image, "png", outputfile);
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
            extractVQ(outputfile, pixelWidth, pixelHeight, chunkStartOffset, deltaOffset, compressedDataOffset, cellOffset);
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
