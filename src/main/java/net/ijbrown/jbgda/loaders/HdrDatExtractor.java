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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Extracts a *.HDR / *.DAT file pair archive
 */
public class HdrDatExtractor
{
    private final GameType gameType;

    public HdrDatExtractor(GameType gameType)
    {
        this.gameType=gameType;
    }

    public void extract(String baseFilename, Path hdrPath, Path datPath, Path outDirPath) throws IOException
    {
        boolean isPicCache = baseFilename.startsWith("PIC");
        var headerEntries = readHeader(hdrPath, isPicCache);

        byte[] datData = Files.readAllBytes(datPath);

        for (var entry : headerEntries) {
            var entryPath = outDirPath.resolve(entry.name);
            Files.createDirectories(entryPath);
            for (int el=0; el<entry.headerElements.length; ++el) {
                var element = entry.headerElements[el];
                var subfileName = entry.name + "-el_" + el + "-id_" + element.id ;

                if (isPicCache || (el & 1) == 0) {
                    // elements come in pairs, the first looks like a texture
                    subfileName += ".tex";
                } else {
                    subfileName += ".vif";
                }
                Path outFilePath = entryPath.resolve(subfileName);

                // The name may contain path separators.
                Path parentPath = outFilePath.getParent();
                Files.createDirectories(parentPath);

                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outFilePath, CREATE, APPEND))) {
                    out.write(datData, element.startPosBytes, element.lenBytes);
                }
            }
        }
    }

    static class HeaderEntry
    {
        String name;
        HeaderElement[] headerElements;
    }

    static class HeaderElement
    {
        int id;
        int lenBytes;
        int startPosBytes;

        int width;
        int height;
    }

    private HeaderEntry[] readHeader(Path hdrPath, boolean isPicCache) throws IOException
    {
        byte[] headerFileData = Files.readAllBytes(hdrPath);

        int numEntries = DataUtil.getLEInt(headerFileData,  0);
        var headerEntries = new HeaderEntry[numEntries];

        int offset=4;
        for (int i=0; i<numEntries; ++i) {
            int elOffset = DataUtil.getLEInt(headerFileData, offset);
            int stringOffset = DataUtil.getLEInt(headerFileData, offset + 4);
            int numEls = DataUtil.getLEInt(headerFileData, offset + 8);
            String name = DataUtil.collectString(headerFileData, stringOffset);

            var entry = new HeaderEntry();
            entry.name = name;
            entry.headerElements = new HeaderElement[numEls];
            for (int el=0; el < numEls; ++el){
                var element = new HeaderElement();
                element.id = DataUtil.getLEShort(headerFileData, elOffset); elOffset += 2;
                if (isPicCache) {
                    // Start and len are reversed between EQCACHE and PICCACHE
                    element.startPosBytes = 2048 * DataUtil.getLEUShort(headerFileData, elOffset);
                    elOffset += 2;
                    element.lenBytes = 2048 * DataUtil.getLEUShort(headerFileData, elOffset);
                    elOffset += 2;
                    element.width = DataUtil.getLEShort(headerFileData, elOffset);
                    elOffset += 2;
                    element.height = DataUtil.getLEShort(headerFileData, elOffset);
                    elOffset += 2;
                } else {
                    element.lenBytes = 2048 * DataUtil.getLEShort(headerFileData, elOffset);
                    elOffset += 2;
                    element.startPosBytes = 2048 * DataUtil.getLEInt(headerFileData, elOffset);
                    elOffset += 4;
                }
                entry.headerElements[el] = element;
            }
            headerEntries[i] = entry;
            offset += 12;
        }

        return headerEntries;
    }


    private String disassemble(HeaderEntry[] headerEntries)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Num entries: ").append(headerEntries.length).append("\r\n");

        for (HeaderEntry entry : headerEntries) {
            sb.append("\r\n");
            sb.append("Name: ").append(entry.name).append("\r\n");
            sb.append("numEls: ").append(entry.headerElements.length).append("\r\n");
            sb.append("Els: ").append("\r\n");
            for (int el = 0; el < entry.headerElements.length; ++el) {
                var element = entry.headerElements[el];
                sb.append("ID: ").append(element.id).append(", Len: ").append(element.lenBytes).append(", Start: ").append(element.startPosBytes).append("\r\n");
            }
        }

        return sb.toString();
    }

}
