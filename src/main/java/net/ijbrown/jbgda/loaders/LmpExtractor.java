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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * extracts files from a .lmp file.
 */
public class LmpExtractor {

    private final GameType gameType;

    public LmpExtractor(GameType gameType) {
        this.gameType=gameType;
    }


    public void extractAll(Path lmpFilename, Path lmpFile, Path lmpOutPath) throws IOException
    {
        Logger.info("Extracting {}", lmpFilename);
        byte[] fileData = Files.readAllBytes(lmpFile);

        extractAll(fileData, 0, lmpOutPath);
    }

    public void extractAll(byte[] fileData, int fileStartOffset, Path outDir) throws IOException
    {
        int numFiles = DataUtil.getLEInt(fileData, fileStartOffset);
        Logger.info("LMP contains {} Files", numFiles);

        int headerOffset = fileStartOffset+4;
        for (int fileNo=0; fileNo < numFiles; ++fileNo){
            int stringOffset;
            int subOffset;
            int subLen;
            String subfileName;
            if (gameType == GameType.DARK_ALLIANCE) {
                // Name inline with header
                subfileName = DataUtil.collectString(fileData, headerOffset);
                subOffset = DataUtil.getLEInt(fileData, headerOffset + 0x38);
                subLen = DataUtil.getLEInt(fileData, headerOffset + 0x3C);
                headerOffset += 0x40;
            } else {
                // name referenced from header
                stringOffset = DataUtil.getLEInt(fileData, headerOffset);
                subOffset = DataUtil.getLEInt(fileData, headerOffset+4);
                subLen = DataUtil.getLEInt(fileData, headerOffset+8);
                subfileName = DataUtil.collectString(fileData, fileStartOffset+stringOffset);
                headerOffset += 0x0C;
            }
            Logger.info("Extracting: {}, offset={}, length={}", subfileName, subOffset, subLen);

            Path outFilePath = outDir.resolve(subfileName);
            try (OutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(outFilePath, CREATE, APPEND))) {
                out.write(fileData, fileStartOffset + subOffset, subLen);
            }
        }
    }
}
