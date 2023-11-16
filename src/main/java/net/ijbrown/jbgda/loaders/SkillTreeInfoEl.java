package net.ijbrown.jbgda.loaders;

public class SkillTreeInfoEl {
    // 0x28 bytes long
    // Array terminates when x has a value of -1
    public boolean newStyle;
    public byte unknownOff1;    // offset 1

    public long skillId;       // offset 8

    public int unkInt10;       // offset 0x10

    public long unkLong18;     // offset 0x18
    public int x;              // offset 0x20
    public int y;              // offset 0x24

    public static SkillTreeInfoEl read(byte[] data, int address) {
        var dataUtil = new DataUtil(data, address);
        var el = new SkillTreeInfoEl();
        el.newStyle = dataUtil.getBool(0);
        el.unknownOff1 = dataUtil.getByte(1);
        el.skillId = dataUtil.getLELong(8);
        el.unkInt10 = dataUtil.getLEInt(0x10);
        el.unkLong18 = dataUtil.getLELong(0x18);
        el.x = dataUtil.getLEInt(0x20);
        el.y = dataUtil.getLEInt(0x24);
        return el;
    }
}
