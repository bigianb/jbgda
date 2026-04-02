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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Map<String, LevelTexImgInfo> extractAll(File outDirFile) throws IOException
    {
        Map<String, LevelTexImgInfo> infoMap = new HashMap<>();
        for (var entry : texEntries){
            int numTexturesInEntry = DataUtil.getLEInt(fileData, entry.directoryOffset);
            for (int i=1; i <= numTexturesInEntry; ++i) {
                int offset = entry.directoryOffset + i * 64;

                File outFile = new File(outDirFile, "leveltex_"+entry.cellOffset + "_" + i + ".png");
                try {
                    var info = extract(outFile, offset, entry.directoryOffset, entry.cellOffset);
                    infoMap.put(entry.cellOffset + "_" + i, info);
                } catch (RuntimeException e) {
                    Logger.warn("Failed to decode {}", outFile);
                    throw new RuntimeException(e);
                }
            }
        }
        return infoMap;
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
            subtracts = new int[33];
        }
        public int lenBytes;
        public int[] subtracts;
        public int numBitsOrig;
        public List<Integer> values;
        public List<Byte> huffman;

        public void buildHuffman(int targetLen, int dataOffset, byte[] fileData)
        {
            BitstreamState bs = new BitstreamState(fileData, dataOffset);

            huffman.clear();
            for (int i=0; i < targetLen * 4; ++i){
                huffman.add((byte) 0);
            }
            if (numBitsOrig > 0) {
                int huffIndex = 0;
                while (huffIndex < targetLen * 4) {
                    long bits32 = bs.readBits(32);
                    long threshold = (bits32 >>> 2);

                    int groupBoundary = 0;
                    boolean foundBoundary = false;
                    while (!foundBoundary) {
                        int boundary = values.get(groupBoundary);
                        foundBoundary = boundary > threshold;
                        if (!foundBoundary) {
                            groupBoundary += 1;
                        }
                    }

                    int subtractsIdx = subtracts[0] - groupBoundary;
                    int codeword = (int)bits32 >>> subtractsIdx;
                    bs.putBack(bits32, subtractsIdx);

                    int huffval = codeword - subtracts[subtractsIdx];
                    if (huffval < 0 || huffval >= 256) {
                        Logger.warn("huffval = {}, subtractsIdx = {}, codeword = {}, threshold = {}", huffval, subtractsIdx, codeword, threshold);
                    }
                    huffman.set(huffIndex++, (byte) huffval);
                }
            }
        }
    }

    private HuffmanTable decodeHuffman(int offset)
    {
        Logger.debug("Decoding Huffman table at offset {}", offset);
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
            table.subtracts[32 - numBits] = (code1 >>> shiftVal) - code2;

            Logger.debug("    numBits = {}, shiftVal = {}, code1 = {}, code2 = {}", numBits, shiftVal, code1, code2);
            Logger.debug("        tableVal = {}, subtracts = {}", table.values.getLast(), table.subtracts[32 - numBits]);

            pos += 8;
            code2 =  DataUtil.getLEInt(fileData, pos + 4);
            numBits += 1;
        }
        table.values.add(0x7FFFFFFF);
        table.lenBytes = pos + 8 - offset;

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

        // put back numBits from the LSB of val
        void putBack(long val, int numBits){
            if (numBits <= 0){
                return;
            }
            bitsInBuffer += numBits;
            bitBuffer >>>= numBits;   // make space
            int bbLeftShift = 0x40 - numBits;
            long mask = (~(-1L << (long)numBits));
            long keep = val & mask;
            bitBuffer |= keep << bbLeftShift;
        }

        long readBits(int num)
        {
            if (num <= 0){
                return 0;
            }
            while (bitsInBuffer < num) {
                long bVar2 = data[streamPos++] & 0xFF;
                int xx = 0x38 - bitsInBuffer;
                bitBuffer |= (bVar2 << xx);
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
        byte[] pix8s = new byte[256];

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);

        // pageNum is 100 * y + x ... so 4849 for example
        // chunkStartOffset is the start of the page data (i.e. the directory)
        int vqPaletteOffset = DataUtil.getLEInt(fileData, compressedDataOffset) + deltaOffset;
        int numPalEntries = DataUtil.getLEUShort(fileData, vqPaletteOffset);
        int huffLenWords = DataUtil.getLEUShort(fileData, vqPaletteOffset + 2); // huff table len in words
        int palOffset =  vqPaletteOffset + 4;
        if (fileData.length <= palOffset + 256 * 4){
            Logger.error("extractVQ: not enough bytes to decode image, file = {}, file length = {}, compressedDataOffset = {}, palOffset = {}", outputfile, fileData.length, compressedDataOffset, palOffset);
            return;
        }
        PalEntry[] palette = PalEntry.readPalette(fileData, palOffset, 16, 16);
        // vq palette is not swizzled
        //palette = PalEntry.unswizzlePalette(palette);

        // offset to first huffman table
        int off1 = palOffset + numPalEntries * 4;
        var table1 = decodeHuffman(off1);

        int off2 = off1 + table1.lenBytes;

        var table2 = decodeHuffman(off2);

        int off3 = off2 + table2.lenBytes;          // stream1
        table1.buildHuffman(huffLenWords, off3, fileData);

        int blocksWidthBits = 1;
        int bw = (pixelWidth + 0xf) >> 4;
        if (bw > 1) {
            do {
                blocksWidthBits += 1;
            } while (1 << (blocksWidthBits & 0x1f) <= bw);
        }

        int blocksHeightBits = 1;
        int bh = (pixelHeight + 0xf) >> 4;
        if (bh > 1) {
            do {
                blocksHeightBits += 1;
            } while (1 << (blocksHeightBits & 0x1f) <= bh);
        }

        Logger.debug("pixel width = {}, bw = {}, pixel height = {}, bh = {}", pixelWidth, bw, pixelHeight, bh);
        Logger.debug("blocksWidthBits = {}, blocksHeightBits = {}", blocksWidthBits, blocksHeightBits);
        BitstreamState bitstreamState2 = new BitstreamState(fileData, compressedDataOffset + 4);

        while( true ) {
            long x0 = bitstreamState2.readBits(blocksWidthBits);
            long y0 = bitstreamState2.readBits(blocksHeightBits);
            long x1 = bitstreamState2.readBits(blocksWidthBits);
            long y1 = bitstreamState2.readBits(blocksHeightBits);

            Logger.debug("x0 = {}, y0 = {}, x1 = {}, y1 = {}", x0, y0, x1, y1);

            if (x1 < x0) break;

            long y = y0;

            while (y < y1) {
                long x = x0;
                long pixel_y = y * 0x10;
                long pixel_y_bot = pixel_y + 0x10;

                while (x < x1) {
                    Logger.debug("Decode block ({}, {})", x, y);
                    // Decode a 16x16 pixel block.
                    // Each huffman code gives a 2x2 pixel block.

                    var currentBitstream = bitstreamState2;
                    BitstreamState bitstreamState3 = new BitstreamState(fileData, 0);

                    long xPixelStart = x * 0x10;
                    long xPixelMax = xPixelStart + 0x10;

                    if (xPixelMax >= pixelWidth){
                        xPixelMax = pixelWidth;
                    }

                    var yPixelMax = pixel_y_bot;
                    if (yPixelMax >= pixelHeight) {
                        yPixelMax = pixelHeight;
                    }

                    var flag = currentBitstream.readBits(1);
                    Logger.debug("flag = {}", flag);
                    if (flag != 0) {
                        // Read 24 bits
                        var payloadOffsetBits = currentBitstream.readBits(24);
                        var payloadOffsetBytes = (int)(payloadOffsetBits >>> 3) & 0x1FFFFFFF;
                        int skipBits = (int)(payloadOffsetBits & 0x07);

                        Logger.debug("payloadOffsetBits = {}, payloadOffsetBytes = {}, skipBits = {}", payloadOffsetBits, payloadOffsetBytes, skipBits);
                        bitstreamState3.setStreamPos(chunkStartOffset + payloadOffsetBytes);
                        currentBitstream = bitstreamState3;

                        if (skipBits != 0) {
                            currentBitstream.readBits(skipBits);
                        }
                        currentBitstream.readBits(1);
                    }
                    var yPixel = pixel_y;

                    if (table2.numBitsOrig == 0) {
                        // use huffman table 1 if there is no second table.
                        for (int localY = 0; yPixel < yPixelMax; yPixel += 2, localY += 1) {
                            int blockOffsetRow1 = localY * 0x10;
                            int blockOffsetRow2 = blockOffsetRow1 + 0x10;

                            for (long xPixel = xPixelStart; xPixel < xPixelMax; xPixel += 2) {
                                pix8s[blockOffsetRow1] = table1.huffman.get(0);
                                pix8s[blockOffsetRow1 + 1] = table1.huffman.get(1);
                                pix8s[blockOffsetRow2] = table1.huffman.get(2);
                                pix8s[blockOffsetRow2 + 1] = table1.huffman.get(3);

                                blockOffsetRow1 += 2;
                                blockOffsetRow2 += 2;
                            }
                        }
                    } else {
                        // use huffman table 2 to find the entry in huffman table 1
                        Logger.debug("loop y from {} to {}", yPixel, yPixelMax);
                        for (int localY = 0; yPixel < yPixelMax; yPixel += 2, localY += 2) {
                            int blockOffsetRow1 = localY * 0x10;
                            int blockOffsetRow2 = blockOffsetRow1 + 0x10;
                            Logger.debug("  loop x from {} -> {}, blockStartRow = {}", xPixelStart, xPixelMax, localY);
                            for (long xPixel = xPixelStart; xPixel < xPixelMax; xPixel += 2) {
                                Logger.debug("    xPixel = {}", xPixel);

                                long bits32 = currentBitstream.readBits(32);

                                long threshold = (bits32 >>> 2);
                                Logger.debug("    reading bits32, threshold = {}", threshold);
                                int groupBoundary = 0;
                                boolean foundBoundary = false;
                                while (!foundBoundary) {
                                    int boundary = table2.values.get(groupBoundary);
                                    foundBoundary = boundary > threshold;
                                    if (!foundBoundary) {
                                        groupBoundary += 1;
                                    }
                                }
                                Logger.debug("      groupBoundary = {}", groupBoundary);
                                // 32 - codelen
                                int subtractsIdx = table2.subtracts[0] - groupBoundary;
                                int codelen = 32 - subtractsIdx;
                                int codeword = (int)bits32 >>> subtractsIdx;
                                Logger.debug("      codelen = {}, codeword = {}, subtractsIdx = {}", codelen, codeword, subtractsIdx);
                                currentBitstream.putBack(bits32, subtractsIdx);

                                int huffIndex = (codeword - table2.subtracts[subtractsIdx]) * 4;
                                Logger.debug("      subtractsVal = {}, huffIndex = {}", table2.subtracts[subtractsIdx], huffIndex);
                                if (huffIndex < table1.huffman.size()) {
                                    pix8s[blockOffsetRow1] = table1.huffman.get(huffIndex);
                                    pix8s[blockOffsetRow1 + 1] = table1.huffman.get(huffIndex + 1);
                                    pix8s[blockOffsetRow2] = table1.huffman.get(huffIndex + 2);
                                    pix8s[blockOffsetRow2 + 1] = table1.huffman.get(huffIndex + 3);
                                }
                                blockOffsetRow1 += 2;
                                blockOffsetRow2 += 2;
                            }
                        }
                    }

                    int idx= 0;
                    for (int blockY=0; blockY < 16; blockY++) {
                        for (int blockX = 0; blockX < 16; blockX++) {
                            byte pix8 = pix8s[idx++];
                            PalEntry pixel = palette[pix8 & 0xFF];
                            int px = (int)x * 16 + blockX;
                            int py = (int)y * 16 + blockY;
                            if (px < pixelWidth && py < pixelHeight) {
                                image.setRGB(px, py, pixel.rgb());
                            }
                        }
                    }
                    x++;
                }
                ++y;
            }
        }
        ImageIO.write(image, "png", outputfile);
    }

    public static class LevelTexImgInfo
    {
        public int width;
        public int height;
    }



    public LevelTexImgInfo extract(File outputfile, int offset, int chunkStartOffset, int cellOffset) throws IOException
    {
        var deltaOffset = convertOffset(0, chunkStartOffset, offset);

        int pixelWidth = DataUtil.getLEUShort(fileData, offset);
        int pixelHeight = DataUtil.getLEUShort(fileData, offset + 2);
        int header10 = DataUtil.getLEInt(fileData, offset + 0x10);
        int flags = DataUtil.getLEUShort(fileData, offset + 8);

        boolean usesVQCompression = (flags & 0x1) == 0x01;
        boolean flag100 = (flags & 0x100) == 0x0100;

        int compressedDataOffset = header10 + deltaOffset;

        LevelTexImgInfo info = new LevelTexImgInfo();
        info.width = pixelWidth;
        info.height = pixelHeight;

        // CHAMPIONS OF NORRATH have flag 1 set whilst BGDA, RTA and JLH do not
        if (usesVQCompression){
            extractVQ(outputfile, pixelWidth, pixelHeight, chunkStartOffset, deltaOffset, compressedDataOffset, cellOffset);
            return info;
        }

        int palOffset = DataUtil.getLEInt(fileData, compressedDataOffset) + deltaOffset;
        if (compressedDataOffset <= 0 || compressedDataOffset >= fileData.length)
        {
            return info;
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
        return info;
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
