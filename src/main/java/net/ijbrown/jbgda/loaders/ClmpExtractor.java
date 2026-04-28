/*  Copyright (C) 2026 Ian Brown

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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * extracts files from a .clmp file.
 */
public class ClmpExtractor {

    public ClmpExtractor() {

    }


    public void extractAll(Path lmpFilename, Path lmpFile, Path lmpOutPath, boolean streamable) throws IOException
    {
        Logger.info("Extracting {}", lmpFilename);
        byte[] fileData = Files.readAllBytes(lmpFile);

        extractAll(fileData, 0, lmpOutPath, streamable);
    }

    public void extractAll(byte[] fileData, int fileStartOffset, Path outDir, boolean streamable) throws IOException
    {
        int sig = DataUtil.getLEInt(fileData, fileStartOffset); // 0x434C4D50. CLMP
        int zero = DataUtil.getLEInt(fileData, fileStartOffset+4);
        int offsetIn4kBlocks = DataUtil.getLEInt(fileData, fileStartOffset+8);
        int checksum = DataUtil.getLEInt(fileData, fileStartOffset+12);
        int dataLen = DataUtil.getLEInt(fileData, fileStartOffset+16);
        int priority = DataUtil.getLEUShort(fileData, fileStartOffset+20);

        int blockSize = streamable ? 0x1000 : 0x100;

        int hashTableOffset = fileStartOffset + offsetIn4kBlocks * blockSize;
        int bitCount= 0;
        for (int i = dataLen + dataLen / 2; i != 0; i >>= 1) {
            ++bitCount;
        }

        System.out.println("Offset in 4k blocks = " + offsetIn4kBlocks + ", dataLen = " + dataLen + ", bitCount = " + bitCount);

        if (hashTableOffset > fileData.length) {return;}

        int hashTablecount = 1 << (bitCount & 0x1f);
        for (int i = 0; i < hashTablecount; ++i) {
            int entryOffset = hashTableOffset + i * 20;
            int hashVal = DataUtil.getLEInt(fileData, entryOffset);
            int fileDataOffset = DataUtil.getLEInt(fileData, entryOffset+4) * blockSize;
            int fileDataLen = DataUtil.getLEInt(fileData, entryOffset+12);
            if (hashVal != 0 && fileDataLen != 0) {
                System.out.println("File: hash = " + hashVal + ", fileDataOffset = " + fileDataOffset + ", fileDataLen = " + fileDataLen);
                var subfileName = Integer.toHexString(hashVal);
                var ext = guessFiletype(fileData, fileStartOffset + fileDataOffset, fileDataLen);
                subfileName += ext;
                Path outFilePath = outDir.resolve(subfileName);
                try (OutputStream out = new BufferedOutputStream(
                        Files.newOutputStream(outFilePath))) {
                    out.write(fileData, fileStartOffset + fileDataOffset, fileDataLen);
                }
            }
        }
    }

    private String guessFiletype(byte[] fileData, int i, int fileDataLen) {
        String ext = ".unk";
        if (fileDataLen > 256){
            if (fileData[i + 0x98] == 0x50 && fileData[i + 0xA8] == 0x51 && fileData[i + 0xB8] == 0x52 && fileData[i + 0xC8] == 0x53){
                ext = ".tex";
            }
        }

        return ext;
    }
}
