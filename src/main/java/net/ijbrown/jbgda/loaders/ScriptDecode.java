/*  Copyright (C) 2012-2022 Ian Brown

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes a script file.
 */
public class ScriptDecode
{
    private byte[] fileData;

    public void read(Path scrPath) throws IOException
    {
        fileData = Files.readAllBytes(scrPath);

        externalsMap.clear();
        internalsMap.clear();
        stringTable.clear();
        stack.clear();
    }

    // There is a 0x60 byte header followed by the body.
    private final int bodyOffset = 0x60;

    public String disassemble()
    {
        StringBuilder sb = new StringBuilder();

        int offset0 = DataUtil.getLEInt(fileData, bodyOffset);
        int hw1 = DataUtil.getLEUShort(fileData, bodyOffset + 0x04);
        int hw2 = DataUtil.getLEUShort(fileData, bodyOffset + 0x06);
        int hw3 = DataUtil.getLEUShort(fileData, bodyOffset + 0x08);
        int hw4 = DataUtil.getLEUShort(fileData, bodyOffset + 0x0A);

        int instructionsOffset = DataUtil.getLEInt(fileData, bodyOffset + 0x0C);
        int stringsOffset = DataUtil.getLEInt(fileData, bodyOffset + 0x10);
        int offset3 = DataUtil.getLEInt(fileData, bodyOffset + 0x14);
        int offset4 = DataUtil.getLEInt(fileData, bodyOffset + 0x18);
        int offset5 = DataUtil.getLEInt(fileData, bodyOffset + 0x1C);

        sb.append("Header:\r\n");
        sb.append("~~~~~~\r\n");
        sb.append("address  0: ").append(HexUtil.formatHex(offset0)).append("\r\n");
        sb.append("address  4: ").append(HexUtil.formatHex(hw1)).append("\r\n");
        sb.append("address  6: ").append(HexUtil.formatHex(hw2)).append("\r\n");
        sb.append("address  8: ").append(HexUtil.formatHex(hw3)).append("\r\n");
        sb.append("address  A: ").append(HexUtil.formatHex(hw4)).append("\r\n");
        sb.append("address  C (inst table): ").append(HexUtil.formatHex(instructionsOffset)).append("\r\n");
        sb.append("address 10 (string table): ").append(HexUtil.formatHex(stringsOffset)).append("\r\n");
        sb.append("address 14: ").append(HexUtil.formatHex(offset3)).append("\r\n");
        sb.append("address 18: ").append(HexUtil.formatHex(offset4)).append("\r\n");
        sb.append("address 1C: ").append(HexUtil.formatHex(offset5)).append("\r\n");

        int numInternals = DataUtil.getLEUShort(fileData, bodyOffset + 0x20);
        int offsetInternals = DataUtil.getLEInt(fileData, bodyOffset + 0x24);

        int numExternals = DataUtil.getLEUShort(fileData, bodyOffset + 0x28);
        int offsetExternals = DataUtil.getLEInt(fileData, bodyOffset + 0x2C);

        sb.append(numInternals).append(" Internals:\r\n");
        sb.append("~~~~~~~~~~~~\r\n");
        for (int i = 0; i < numInternals; ++i) {
            printInternal(sb, offsetInternals + 0x18 * i);
        }
        sb.append("\r\n");

        sb.append(numExternals).append(" Externals:\r\n");
        sb.append("~~~~~~~~~~~~\r\n");
        for (int i = 0; i < numExternals; ++i) {
            printExternal(sb, i, offsetExternals + 0x18 * i);
        }
        sb.append("\r\n");

        StringBuilder sb3 = new StringBuilder();
        dumpStrings(sb3, stringsOffset, offset3 - stringsOffset);

        sb.append("Instructions\r\n");
        sb.append("~~~~~~~~~~~~\r\n\r\n");
        dumpInstructions(sb, instructionsOffset, stringsOffset - instructionsOffset);
        sb.append("\r\n");

        sb.append("Strings\r\n");
        sb.append("~~~~~~~\r\n\r\n");
        sb.append(sb3);
        sb.append("\r\n");
        return sb.toString();
    }

    private void dumpStrings(StringBuilder sb, int stringsOffset, int len)
    {
        boolean needsOffset = true;
        int startOffset = 0;
        StringBuilder sb2 = new StringBuilder(64);
        for (int i = 0; i < len; i += 4) {
            if (needsOffset) {
                sb.append(HexUtil.formatHex(i)).append(": ");
                needsOffset = false;
                startOffset = i;
            }
            int ival = DataUtil.getLEInt(fileData, stringsOffset + i + bodyOffset);
            for (int b = 3; b >= 0; --b) {
                int c = (ival >> (b * 8)) & 0xff;
                if (0 == c) {
                    stringTable.put(startOffset, sb2.toString());
                    sb.append(sb2);
                    sb.append("\r\n");
                    sb2 = new StringBuilder(64);
                    needsOffset = true;
                    break;
                }
                sb2.append((char) c);
            }

        }
    }

    private final Map<Integer, String> stringTable = new HashMap<>();

    private void dumpInstructions(StringBuilder sb, int instructionsOffset, int len)
    {
        for (int i = 0; i < len; i += 4) {
            int opcode = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
            String label = internalsMap.get(i);
            if (label != null) {
                sb.append("\r\n").append(label).append(":\r\n");
            }
            sb.append(HexUtil.formatHex(i)).append(": ");

            int bytesConsumed = disassembleInstruction(sb, opcode, i, instructionsOffset);
            if (bytesConsumed >= 0) {
                i += bytesConsumed;
            } else {
                System.out.println("Unknown opcode " + HexUtil.formatHex(opcode));
                sb.append(HexUtil.formatHex(opcode));
                if (opcode < opCodeArgs.length && opcode >= 0) {
                    ARGS_TYPE type = opCodeArgs[opcode];
                    switch (type) {
                        case NO_ARGS:
                            break;
                        case ONE_ARG: {
                            i += 4;
                            int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                            sb.append(" ").append(HexUtil.formatHex(arg1));
                        }
                        break;
                        case ONE_ARG_INSTR: {
                            i += 4;
                            int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                            sb.append(" inst ").append(HexUtil.formatHex(arg1));
                        }
                        break;
                        case TWO_ARGS: {
                            i += 4;
                            int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                            sb.append(" ").append(HexUtil.formatHex(arg1));
                            i += 4;
                            int arg2 = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                            sb.append(" ").append(HexUtil.formatHex(arg2));
                        }
                        break;
                        case VAR_ARGS: {
                            i += 4;
                            int num = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                            sb.append(" numArgs=").append(HexUtil.formatHex(num));
                            for (int j = 0; j < num - 1; ++j) {
                                i += 4;
                                int arg = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                                sb.append(" ").append(HexUtil.formatHex(arg));
                            }
                        }
                        break;
                        case ARGS_130: {
                            i += 4;
                            int num = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                            sb.append(" num=").append(HexUtil.formatHex(num));
                            for (int j = 0; j < num; ++j) {
                                i += 4;
                                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                                i += 4;
                                int arg2 = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                                sb.append("\r\n            ").append(HexUtil.formatHex(arg1)).append(", ").append(arg2);
                            }
                            i += 4;
                            int arg3 = DataUtil.getLEInt(fileData, instructionsOffset + i + bodyOffset);
                            sb.append("\r\n            ").append(HexUtil.formatHex(arg3));
                        }
                        break;
                    }
                } else {
                    sb.append(" *** Instruction op code out of range");
                }
            }
            sb.append("\r\n");
        }
    }

    private final List<Integer> stack = new ArrayList<>(20);

    private int disassembleInstruction(StringBuilder sb, int opcode, int i, int instructionsOffset)
    {
        int bytesConsumed = -1;
        switch (opcode) {
            case 0x1 -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("acc = var ").append(arg1);
            }
            case 0xb -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("acc = ").append(arg1);
            }
            case 0xf -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("var ").append(arg1).append(" = acc");
            }
            case 0x11 -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("t4 var ").append(arg1).append(" = acc");
            }
            case 0x24 -> {
                bytesConsumed = 0;
                stack.add(0);
                sb.append("push a");
            }
            case 0x25 -> {
                bytesConsumed = 0;
                stack.add(0);
                sb.append("push s3");
            }
            case 0x27 -> {
                // pushes a number onto the stack
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                stack.add(arg1);
                sb.append("push ").append(HexUtil.formatHex(arg1));
            }
            case 0x29 -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                stack.add(arg1);
                sb.append("push t4 var ").append(HexUtil.formatHex(arg1));
            }
            case 0x2C -> {
                // pops a number of bytes off the stack
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                int numInts = arg1 / 4;
                for (int idx = 0; idx < numInts && stack.size() > 0; ++idx) {
                    stack.remove(stack.size() - 1);
                }
                sb.append("pop bytes ").append(arg1);
            }
            case 0x2E -> {
                // enters a routine
                bytesConsumed = 0;
                stack.clear();
                sb.append("enter");
            }
            case 0x30 -> {
                bytesConsumed = 0;
                sb.append("return");
            }
            case 0x33 -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("Jump to ").append(HexUtil.formatHexUShort(arg1));
            }
            case 0x35 -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("Jump if acc == 0 to ").append(HexUtil.formatHexUShort(arg1));
            }
            case 0x36 -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("Jump if acc != 0 to ").append(HexUtil.formatHexUShort(arg1));
            }
            case 0x4A -> {
                bytesConsumed = 0;
                sb.append("a = s3 / a, s3 = remainder");
            }
            case 0x54 -> {
                bytesConsumed = 0;
                sb.append("acc <= 0");
            }
            case 0x59 -> {
                bytesConsumed = 0;
                sb.append("acc = 0");
            }
            case 0x5B -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("clear local var ").append(HexUtil.formatHex(arg1));
            }
            case 0x7B -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                String externalName = externalsMap.get(arg1);
                decodeExternalCall(sb, externalName);
            }
            case 0x7D -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                bytesConsumed += 4;
                int arg2 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("Debug line no: ").append(arg1).append("  [").append(arg2).append("]");
            }
            case 0x81 -> {
                bytesConsumed = 4;
                int arg1 = DataUtil.getLEInt(fileData, instructionsOffset + i + bytesConsumed + bodyOffset);
                sb.append("switch(acc) ... case statement defs as ").append(HexUtil.formatHex(arg1));
            }
        }
        return bytesConsumed;
    }

    private void printExternal(StringBuilder sb, String name, char[] argTypes)
    {
        sb.append(name);
        int numArgs = argTypes.length;
        int actualNumArgs = stack.get(stack.size() - 1) / 4;
        if ( actualNumArgs != numArgs){
            sb.append("*** Expected  ").append(numArgs).append(" args but received ").append(actualNumArgs).append(" ***\r\n");
        } else {
            sb.append("(");
            for (int argNo=0; argNo < numArgs; ++argNo){
                int stackIdx = stack.size() - 2 - argNo;
                if (stackIdx < 0){
                    sb.append(" ***** stack underflow! ********");
                    stackIdx=0;
                }
                int iArg = stack.get(stackIdx);
                if (argNo != 0){
                    sb.append(", ");
                }
                switch (argTypes[argNo]) {
                    case 'S' -> printStringArg(sb, iArg);
                    case 'I' -> sb.append(iArg);

                    default -> sb.append(iArg).append(" ** unknown type ** ");
                }
            }
            sb.append(")");
        }
    }

    private void decodeExternalCall(StringBuilder sb, String name)
    {
        switch (name) {
            case "addQuest", "addHelpMessage", "soundSequence" -> printExternal(sb, name, new char[]{'S', 'S'});
            case "callScript", "cutScene", "getv", "postAction", "removeQuest" -> printExternal(sb, name, new char[]{'S'});
            case "setv", "givePlayerItem" -> printExternal(sb, name, new char[]{'S', 'I'});
            case "startDialog" -> printExternal(sb, name, new char[]{'S', 'S', 'I'});
            case "givePlayerExp", "givePlayerGold", "grantMission", "hideMonster" -> printExternal(sb, name, new char[]{'I'});
            case "activateStore", "getScriptState", "checkNewState", "acquireCamera", "releaseCamera", "getRand", "dialogRunning" -> printExternal(sb, name, new char[]{});
            case "loadMonsterSlot" -> printExternal(sb, name, new char[]{'I', 'S', 'I'});
            case "setMissionAvailable" -> printExternal(sb, name, new char[]{'I', 'I'});
            case "moveTalkTarget" -> printExternal(sb, name, new char[]{'I', 'I', 'I'});
            case "setTalkTarget" -> printExternal(sb, name, new char[]{'S', 'I', 'I', 'I', 'I', 'I'});
            default -> sb.append(name).append("( ** unknown args ** )");
        }
    }

    private void printStringArg(StringBuilder sb, int iarg)
    {
        String sArg = stringTable.get(iarg);
        sb.append(sArg != null ? sArg : "?");
    }

    /**
     * Maps an instruction address to a label.
     */
    private final HashMap<Integer, String> internalsMap = new HashMap<>(64);

    private void printInternal(StringBuilder sb, int offset)
    {
        sb.append(HexUtil.formatHexUShort(offset)).append(": ");
        int address = DataUtil.getLEInt(fileData, offset + bodyOffset);
        sb.append(HexUtil.formatHex(address)).append(" - ");
        String label = DataUtil.collectString(fileData, offset + bodyOffset + 4);
        sb.append(label).append("\r\n");
        internalsMap.put(address, label);
    }

    /**
     * Maps an external id to a label.
     */
    private final HashMap<Integer, String> externalsMap = new HashMap<>(64);

    private void printExternal(StringBuilder sb, int id, int offset)
    {
        sb.append(HexUtil.formatHexUShort(id)).append(": ");
        String label = DataUtil.collectString(fileData, offset + bodyOffset + 4);
        sb.append(label).append("\r\n");
        externalsMap.put(id, label);
    }

    private enum ARGS_TYPE
    {
        NO_ARGS, ONE_ARG, ONE_ARG_INSTR, TWO_ARGS, VAR_ARGS, ARGS_130
    }

    // Decoding routine is at 0x001000c8

    private final ARGS_TYPE[] opCodeArgs = new ARGS_TYPE[]
            {
                    ARGS_TYPE.NO_ARGS,      // 0x00
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,

                    ARGS_TYPE.ONE_ARG,      // 0x10
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,

                    ARGS_TYPE.ONE_ARG,      // 0x20
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,

                    ARGS_TYPE.NO_ARGS,          // 0x30
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ONE_ARG_INSTR,

                    ARGS_TYPE.ONE_ARG_INSTR,    // 0x40
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,

                    ARGS_TYPE.NO_ARGS,          //0x50
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,

                    ARGS_TYPE.NO_ARGS,      // 0x60
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,

                    ARGS_TYPE.NO_ARGS,      // 0x70
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.NO_ARGS,
                    ARGS_TYPE.ONE_ARG,
                    ARGS_TYPE.VAR_ARGS,     // 0x7c
                    ARGS_TYPE.TWO_ARGS,     // 0x7d
                    ARGS_TYPE.VAR_ARGS,     // 0x7e
                    ARGS_TYPE.TWO_ARGS,

                    ARGS_TYPE.NO_ARGS,      // 0x80
                    ARGS_TYPE.ONE_ARG_INSTR,
                    ARGS_TYPE.ARGS_130
            };
}

/*
    Notes:

    s0 = pc
    a1 = 0(sp) = accumulator
    s1
    s2 = var base (also stack bottom)
    s3
    s6 = stack size (from s2)
    t4 = var base + s8
    s8 = 0
 */
