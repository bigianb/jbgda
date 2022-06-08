package net.ijbrown.jbgda.loaders;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/*
  Log the contents of a .tex file for investigation.
 */
public class TexLogger {

    public void log(Path texPath, Writer writer) throws IOException
    {
        byte[] fileData = Files.readAllBytes(texPath);

        log(fileData, fileData.length, writer);
    }

    private void log(byte[] fileData, int length, Writer writer) throws IOException
    {
        int finalw = DataUtil.getLEShort(fileData, 0);
        int finalh = DataUtil.getLEShort(fileData,  2);

        writer.write("Width:  " + finalw + "\n");
        writer.write("Height: " + finalh + "\n");

        int dataLen = DataUtil.getLEShort(fileData,  6) * 16;
        int offsetToGIF = DataUtil.getLEInt(fileData,  16);

        writer.write("Data Len (from offset 6): " + dataLen + "\n");
        writer.write("End Index (from offset 6): " + (dataLen + offsetToGIF) + "\n");
        writer.write("File Len                : " + length + "\n");

        writer.write("Offset to GIF: " + offsetToGIF + "\n");

        int curIdx = offsetToGIF;
        while (curIdx < dataLen + offsetToGIF) {
            GIFTag gifTag = new GIFTag();
            gifTag.parse(fileData, curIdx);

            writer.write(""+curIdx + ":\n");
            writer.write("      GIFTAG: "+gifTag.toString() + "\n");
            logGifPayload("              ", gifTag, fileData, curIdx+0x10, writer);
            writer.write("\n");
            
            curIdx += gifTag.getLength();
        }
        writer.write(""+curIdx + ": end\n");
    }

    String[] regNames = {
            "PRIM",
            "RGBAQ",
            "ST",
            "UV",
            "XYZF2",
            "XYZ2",
            "TEX0_1",
            "TEX0_2",
            "CLAMP_1",
            "CLAMP_2",
            "FOG",
            "0x0B Unused",
            "XYZF3",
            "XYZ3",
            "A+D",
            "NOP"
    };

    private void logGifPayload(String prefix, GIFTag gifTag, byte[] data, int payloadIdx, Writer writer) throws IOException
    {
        if (gifTag.flg == 0)
        {
            // PACKED
            for (int i=0; i<gifTag.nloop; ++i){
                for (int r=0; r<gifTag.nreg; ++r){
                    int reg = gifTag.regs[r];
                    if (reg == 0x0E){
                        // A+D
                        int addr = data[payloadIdx + 8];
                        switch (addr){

                            case 0x50: {
                                int sbp = DataUtil.getLEShort(data, payloadIdx) & 0x3FFF;
                                int sbw = data[payloadIdx+2] & 0x3F;
                                int spsm = data[payloadIdx+3] & 0x3F;

                                int dbp = DataUtil.getLEShort(data, payloadIdx + 4) & 0x3FFF;
                                int dbw = data[payloadIdx+6] & 0x3F;
                                int dpsm = data[payloadIdx+7] & 0x3F;

                                writer.write(prefix + "A+D, addr: BITBLTBUF:\n");
                                writer.write(prefix + "                  SBP = " + sbp + ", SBW = " + sbw+  ", SPSM = " + spsm  + "\n");
                                writer.write(prefix + "                  DBP = " + dbp + ", DBW = " + dbw + ", DPSM = " + dpsm + "\n");
                            }
                            break;
                            case 0x51: {
                                int ssax = DataUtil.getLEShort(data, payloadIdx) & 0x7FF;
                                int ssay = DataUtil.getLEShort(data, payloadIdx+2) & 0x7FF;
                                int dsax = DataUtil.getLEShort(data, payloadIdx+4) & 0x7FF;
                                int dsay = DataUtil.getLEShort(data, payloadIdx+6) & 0x7FF;
                                writer.write(prefix + "A+D, addr: TRXPOS:\n");
                                writer.write(prefix + "                  SSAX = " + ssax + ", SSAY = " + ssay + "\n");
                                writer.write(prefix + "                  DSAX = " + dsax + ", DSAY = " + dsay + "\n");
                            }
                            break;
                            case 0x52: {
                                int rrw = DataUtil.getLEShort(data, payloadIdx) & 0xFFF;
                                int rrh = DataUtil.getLEShort(data, payloadIdx+4) & 0xFFF;
                                writer.write(prefix + "A+D, addr: TRXREG:\n");
                                writer.write(prefix + "                  RRH = " + rrh + ", RRW = " + rrw + "\n");
                            }
                            break;
                            case 0x53: {
                                int xdir = DataUtil.getLEShort(data, payloadIdx) & 3;
                                writer.write(prefix + "A+D, addr: TRXDIR: " + xdir + "\n");
                            }
                            break;
                            case 98:
                                writer.write(prefix + "A+D, addr: LABEL\n");
                                break;
                            default:
                                writer.write(prefix + "A+D, addr: "+ addr + "\n");
                        }

                    } else {
                        writer.write(prefix + regNames[reg] + "\n");
                    }
                    payloadIdx += 16;
                }
            }
        }
    }

}
