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

/**
 * Decodes a GOB file.
 */
public class GobExtractor
{
    private final GameType gameType;

    public GobExtractor(GameType gameType)
    {
        this.gameType=gameType;
    }

    public void extract(Path gobFilename, Path gobFile, Path outDirPath) throws IOException
    {
        byte[] fileData = Files.readAllBytes(gobFile);

        var offset=0;
        String lmpName = DataUtil.collectString(fileData, offset);
        LmpExtractor lmpExtractor = new LmpExtractor(gameType);
        while (!lmpName.isEmpty()){
            Logger.info("Extracting {} from {}",  lmpName , gobFilename);
            var lmpDirName = lmpName.replace('.', '_');
            var lmpOutPath = outDirPath.resolve(lmpDirName);
            Files.createDirectories(lmpOutPath);
            int lmpDataOffset = DataUtil.getLEInt(fileData, offset + 0x20);
            lmpExtractor.extractAll(fileData, lmpDataOffset, lmpOutPath);

            offset += 0x28;
            lmpName = DataUtil.collectString(fileData, offset);
        }

    }

}
