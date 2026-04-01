package net.ijbrown.jbgda.exporters;

import net.ijbrown.jbgda.loaders.Vec3F;
import net.ijbrown.jbgda.loaders.VifDecode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjExporterTest {

    @Test
    void getObjText_exportsObjWithFormattedFloatsAndMaterialReference() {
        VifDecode.Mesh mesh = new VifDecode.Mesh();
        mesh.vertices.add(new Vec3F(1.0f, 2.5f, -0.125f));
        mesh.vertices.add(new Vec3F(0.33333334f, -0.0f, 10.0f));
        mesh.vertices.add(new Vec3F(4.25f, 5.5f, 6.75f));

        mesh.uvCoords.add(new VifDecode.UV((short) 16, (short) 32));
        mesh.uvCoords.add(new VifDecode.UV((short) 48, (short) 64));
        mesh.uvCoords.add(new VifDecode.UV((short) 80, (short) 96));

        mesh.normals.add(new Vec3F(0.0f, 1.0f, 0.5f));
        mesh.normals.add(new Vec3F(-1.0f, 0.0f, 0.25f));
        mesh.normals.add(new Vec3F(0.125f, -0.5f, 1.0f));

        mesh.triangleIndices.add(0);
        mesh.triangleIndices.add(1);
        mesh.triangleIndices.add(2);

        String actual = ObjExporter.getObjText(mesh);

        String expected = """
                mtllib material.mtl
                usemtl material0
                v 1 2.5 -0.125
                v 0.333333 0 10
                v 4.25 5.5 6.75
                vt 16 32
                vt 48 64
                vt 80 96
                vn 0 1 0.5
                vn -1 0 0.25
                vn 0.125 -0.5 1
                f 1/1/1 2/2/2 3/3/3
                """;

        assertEquals(expected, actual);
    }
}
