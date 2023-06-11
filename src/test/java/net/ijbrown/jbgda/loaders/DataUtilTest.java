package net.ijbrown.jbgda.loaders;

import static org.junit.jupiter.api.Assertions.*;

class DataUtilTest {

    @org.junit.jupiter.api.Test
    void getBits() {
        byte[] data={0x34, 0x12, 0x78, 0x56};
        assertEquals(1, DataUtil.getBits(data, 0, 4, true));
        assertEquals(1, DataUtil.getBits(data, 0, 4, false));
        assertEquals(0x23, DataUtil.getBits(data, 4, 8, true));
        assertEquals(8, DataUtil.getBits(data, 12, 5, true));
        assertEquals(8, DataUtil.getBits(data, 13, 4, true));
        assertEquals(-8, DataUtil.getBits(data, 13, 4, false));
        assertEquals(0x15, DataUtil.getBits(data, 17, 5, true));

    }
}