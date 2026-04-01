package net.ijbrown.jbgda.exporters;

import net.ijbrown.jbgda.loaders.VifDecode;
import net.ijbrown.jbgda.loaders.Vec3F;

import java.util.Locale;

public class ObjExporter {
    // Create the text for an OBJ file given a mesh
    public static String getObjText(VifDecode.Mesh mesh, String texName, int width, int height) {
        StringBuilder sb = new StringBuilder();

        sb.append("mtllib material.mtl\n");
        sb.append("usemtl ").append(texName).append("\n");

        for (Vec3F v : mesh.vertices) {
            sb.append("v ")
              .append(formatFloat(v.x)).append(' ')
              .append(formatFloat(v.y)).append(' ')
              .append(formatFloat(v.z))
              .append('\n');
        }

        for (VifDecode.UV uv : mesh.uvCoords) {
            if (uv != null) {
                float u = (float) uv.u / width;
                float v = (float) uv.v / height;

                sb.append("vt ")
                  .append(formatFloat(u % 1.0f)).append(' ')
                  .append(formatFloat(v % 1.0f))
                  .append('\n');
            }
        }

        for (Vec3F n : mesh.normals) {
            if (n != null) {
                sb.append("vn ")
                  .append(formatFloat(n.x)).append(' ')
                  .append(formatFloat(n.y)).append(' ')
                  .append(formatFloat(n.z))
                  .append('\n');
            }
        }

        for (int i = 0; i + 2 < mesh.triangleIndices.size(); i += 3) {
            int i1 = mesh.triangleIndices.get(i) + 1;
            int i2 = mesh.triangleIndices.get(i + 1) + 1;
            int i3 = mesh.triangleIndices.get(i + 2) + 1;

            boolean hasUv = mesh.uvCoords.size() >= mesh.vertices.size();
            boolean hasNormals = mesh.normals.size() >= mesh.vertices.size();

            sb.append("f ");
            if (hasUv || hasNormals) {
                sb.append(formatFaceVertex(i1, hasUv, hasNormals));
                sb.append(' ');
                sb.append(formatFaceVertex(i2, hasUv, hasNormals));
                sb.append(' ');
                sb.append(formatFaceVertex(i3, hasUv, hasNormals));
            } else {
                sb.append(i1).append(' ').append(i2).append(' ').append(i3);
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static String formatFaceVertex(int index1Based, boolean hasUv, boolean hasNormals) {
        if (hasUv && hasNormals) {
            return index1Based + "/" + index1Based + "/" + index1Based;
        } else if (hasUv) {
            return index1Based + "/" + index1Based;
        } else if (hasNormals) {
            return index1Based + "//" + index1Based;
        }
        return Integer.toString(index1Based);
    }

    private static String formatFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "0";
        }

        String text = String.format(Locale.ROOT, "%.6f", value);
        int end = text.length();

        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }

        text = text.substring(0, end);
        if (text.isEmpty() || text.equals("-0")) {
            return "0";
        }
        return text;
    }
}
