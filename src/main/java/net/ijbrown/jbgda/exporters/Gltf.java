package net.ijbrown.jbgda.exporters;

import net.ijbrown.jbgda.loaders.VifDecode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

// https://sandbox.babylonjs.com/ can view files

public class Gltf
{
    private final List<VifDecode.Mesh> meshes;
    private final String texName;
    private final int texW;
    private final int texH;

    private static class Node
    {
        public int id;
        public String name;
        public int mesh = -1;
        public int camera = -1;
        public List<Node> children;

        public Node(String name) {
            id = -1;
            this.name = name;
        }
    }

    private final List<Node> nodes = new ArrayList<>();

    private static class Buffer
    {
        public int id;
        public byte[] buffer;
    }

    private final int MATERIAL_ID = 0;

    public Gltf(List<VifDecode.Mesh> meshes, String texName, int texW, int texH) {
        this.meshes = meshes;
        this.texName = texName;
        this.texW = texW;
        this.texH = texH;
    }

    public void write(Path outPath) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        try (var writer = Files.newBufferedWriter(outPath, charset)) {
            JsonWriter jsonWriter = new JsonWriter(writer);
            write(jsonWriter);
        }
    }

    public void write(JsonWriter writer) throws IOException {
        writer.openObject();
        writeAsset(writer);

        writer.writeKey("meshes");
        writer.openArray();
        var meshIdx=0;
        for(var mesh: meshes) {
            writeMesh(writer, mesh);

            Node meshNode = new Node("mesh"+meshIdx);
            meshNode.id = nodes.size();
            meshNode.mesh = meshIdx;
            nodes.add(meshNode);

            ++meshIdx;
        }
        writer.closeArray();

        writeScene(writer, nodes);
        writeNodes(writer, "nodes", nodes);
        writeImages(writer);
        writeTextures(writer);
        writeMaterials(writer);
        writeBuffers(writer);
        writeAccessors(writer);
        writer.closeObject();
    }

    private final List<Buffer> buffers = new ArrayList<>();

    private Buffer createBuffer(int size)
    {
        Buffer b = new Buffer();
        b.id = buffers.size();
        b.buffer = new byte[size];
        buffers.add(b);
        return b;
    }


    private void writeBuffers(JsonWriter writer) throws IOException {
        // https://github.com/KhronosGroup/glTF/tree/master/specification/2.0#reference-buffer
        writer.writeKey("buffers");
        writer.openArray();
        for (var buffer : buffers){
            writer.openObject();
            writer.writeKeyValue("byteLength", buffer.buffer.length);
            // Embedded buffers
            writer.writeKey("uri");
            writeEmbeddedData(writer, buffer.buffer);
            writer.closeObject();
        }
        writer.closeArray();

        // For this implementation, each buffer has a single bufferview of the same index.
        writer.writeKey("bufferViews");
        writer.openArray();
        for (var buffer : buffers){
            writer.openObject();
            writer.writeKeyValue("buffer", buffer.id);
            writer.writeKeyValue("byteOffset", 0);
            writer.writeKeyValue("byteLength", buffer.buffer.length);
            writer.closeObject();
        }
        writer.closeArray();

    }

    private void writeEmbeddedData(JsonWriter writer, byte[] buffer) throws IOException {
        var encoder = Base64.getEncoder();
        String encodedData = encoder.encodeToString(buffer);
        writer.writeValue("data:application/octet-stream;base64,"+encodedData);
    }

    private void writeMesh(JsonWriter writer, VifDecode.Mesh mesh) throws IOException {
        /*
            In glTF, meshes are defined as arrays of primitives.
            Primitives correspond to the data required for GPU draw calls.
            Primitives specify one or more attributes, corresponding to the vertex attributes used in the draw calls.
            Indexed primitives also define an indices property.
            Attributes and indices are defined as references to accessors containing corresponding data.
            Each primitive also specifies a material and a primitive type that corresponds to the GPU primitive type
            (e.g., triangle set).
         */
        var accessors = buildAccessors(mesh);
        writer.openObject();
        writer.writeKey("primitives");
        writer.openArray();
            writer.openObject();
                writer.writeKeyValue("mode", 4);    // triangles
                writer.writeKey("attributes");
                writer.openObject();
                    writer.writeKeyValue("POSITION", accessors.positionAccessor.id);
                    writer.writeKeyValue("TEXCOORD_0", accessors.texcoord0Accessor.id);
                writer.closeObject();
                writer.writeKeyValue("indices", accessors.indicesAccessor.id);

                writer.writeKeyValue("material", MATERIAL_ID);
            writer.closeObject();
        writer.closeArray();
        writer.closeObject();
    }

    private enum ComponentType
    {
        UNSIGNED_SHORT(5123), FLOAT(5126);

        private final int id;

        ComponentType(int id)
        {
            this.id = id;
        }
    }

    private static class Accessor
    {
        public int id;
        public int bufferView;
        public int byteOffset;
        public int count;
        public String type;
        public float[] min_fa;
        public float[] max_fa;

        public ComponentType componentType;
    }

    private void writeAccessors(JsonWriter writer) throws IOException {
        // https://github.com/KhronosGroup/glTF/tree/master/specification/2.0#accessors

        writer.writeKey("accessors");
        writer.openArray();
        for (var accessor : accessors){
            writer.openObject();
            writer.writeKeyValue("bufferView", accessor.bufferView);
            writer.writeKeyValue("byteOffset", accessor.byteOffset);
            writer.writeKeyValue("count", accessor.count);
            writer.writeKeyValue("type", accessor.type);
            writer.writeKeyValue("componentType", accessor.componentType.id);
            if (accessor.min_fa != null){
                writer.writeKeyValue("min", accessor.min_fa);
            }
            if (accessor.max_fa != null){
                writer.writeKeyValue("max", accessor.max_fa);
            }
            writer.closeObject();
        }
        writer.closeArray();
    }

    private Accessor createAccessor(int bufferView, int byteOffset, int count, String type, ComponentType componentType)
    {
        Accessor accessor = new Accessor();
        accessor.id = accessors.size();
        accessor.bufferView = bufferView;
        accessor.byteOffset = byteOffset;
        accessor.count = count;
        accessor.type = type;
        accessor.componentType = componentType;
        accessors.add(accessor);
        return accessor;
    }

    private final List<Accessor> accessors = new ArrayList<>();

    private static class MeshPrimAccessors
    {
        public Accessor positionAccessor;
        public Accessor indicesAccessor;
        public Accessor texcoord0Accessor;
    }

    private MeshPrimAccessors buildAccessors(VifDecode.Mesh mesh) {
        int positionSize = mesh.vertices.size() * 12;
        int indicesSize = mesh.triangleIndices.size() * 2;
        int texCoordSize = mesh.uvCoords.size() * 2 * 4;
        var posBuffer = createBuffer(positionSize);
        var idxBuffer = createBuffer(indicesSize);
        var texCoordBuffer = createBuffer(texCoordSize);

        int i=0;
        VifDecode.Vec3F minPos = new VifDecode.Vec3F(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        VifDecode.Vec3F maxPos = new VifDecode.Vec3F(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

        for (var vec3 : mesh.vertices){
            accumulateMin(minPos, vec3);
            accumulateMax(maxPos, vec3);
            i = writeFloat(posBuffer.buffer, i, vec3.x);
            i = writeFloat(posBuffer.buffer, i, vec3.y);
            i = writeFloat(posBuffer.buffer, i, vec3.z);
        }
        i=0;
        for (var val : mesh.triangleIndices){
            i = writeShort(idxBuffer.buffer, i, val);
        }

        float sCoeff = 1.0f / (16.0f * texW);
        float tCoeff = 1.0f / (16.0f * texH);

        float maxS = 0.0f;
        float maxT = 0.0f;

        float minS = 1.0f;
        float minT = 1.0f;
        i=0;
        for (var uv : mesh.uvCoords){
            float s = (float)uv.u * sCoeff;
            float t = (float)uv.v * tCoeff;
            if (s > maxS){
                maxS = s;
            }
            if (t > maxT){
                maxT = t;
            }
            if (s < minS){
                minS = s;
            }
            if (t < minT){
                minT = s;
            }
            i = writeFloat(texCoordBuffer.buffer, i, s);
            i = writeFloat(texCoordBuffer.buffer, i, t);
        }

        var meshPrimAccessors = new MeshPrimAccessors();
        meshPrimAccessors.positionAccessor = createAccessor(posBuffer.id, 0, mesh.vertices.size(), "VEC3", ComponentType.FLOAT);
        meshPrimAccessors.positionAccessor.min_fa = new float[]{minPos.x, minPos.y, minPos.z};
        meshPrimAccessors.positionAccessor.max_fa = new float[]{maxPos.x, maxPos.y, maxPos.z};

        meshPrimAccessors.indicesAccessor = createAccessor(idxBuffer.id, 0, mesh.triangleIndices.size(), "SCALAR", ComponentType.UNSIGNED_SHORT);

        meshPrimAccessors.texcoord0Accessor = createAccessor(texCoordBuffer.id, 0, mesh.uvCoords.size(), "VEC2", ComponentType.FLOAT);
        meshPrimAccessors.texcoord0Accessor.min_fa = new float[]{minS, minT};
        meshPrimAccessors.texcoord0Accessor.max_fa = new float[]{maxS, maxT};
        return meshPrimAccessors;
    }

    private void accumulateMin(VifDecode.Vec3F minPos, VifDecode.Vec3F vec3) {
        minPos.x = Math.min(minPos.x, vec3.x);
        minPos.y = Math.min(minPos.y, vec3.y);
        minPos.z = Math.min(minPos.z, vec3.z);
    }

    private void accumulateMax(VifDecode.Vec3F maxPos, VifDecode.Vec3F vec3) {
        maxPos.x = Math.max(maxPos.x, vec3.x);
        maxPos.y = Math.max(maxPos.y, vec3.y);
        maxPos.z = Math.max(maxPos.z, vec3.z);
    }

    private int writeShort(byte[] buf, int idx, Integer val) {
        buf[idx++] = (byte)(val & 0xFF);
        buf[idx++] = (byte)((val >> 8) & 0xFF);
        return idx;
    }

    private int writeFloat(byte[] buf, int idx, float f)
    {
        int bits = Float.floatToRawIntBits(f);
        buf[idx++] = (byte)(bits & 0xFF);
        buf[idx++] = (byte)((bits >> 8) & 0xFF);
        buf[idx++] = (byte)((bits >> 16) & 0xFF);
        buf[idx++] = (byte)((bits >> 24) & 0xFF);
        return idx;
    }

    private void writeNodes(JsonWriter writer, String name, List<Node> nodes) throws IOException {
        writer.writeKey(name);
        writer.openArray();
        for (Node node : nodes) {
            writeNode(writer, node);
        }
        writer.closeArray();
    }

    private void writeImages(JsonWriter writer) throws IOException {
        if (texW > 0) {
            writer.writeKey("images");
            writer.openArray();
            writer.openObject();
            writer.writeKeyValue("uri", texName);
            writer.closeObject();
            writer.closeArray();
        }
    }

    private void writeTextures(JsonWriter writer) throws IOException {
        if (texW > 0) {
            writer.writeKey("textures");
            writer.openArray();
            writer.openObject();
            // index into the images array (which only has the one entry)
            writer.writeKeyValue("source", 0);
            writer.closeObject();
            writer.closeArray();
        }
    }

    private void writeMaterials(JsonWriter writer) throws IOException {
        writer.writeKey("materials");
        writer.openArray();
        writer.openObject();
            writer.writeKeyValue("name", "material0");
            writer.writeKeyValue("doubleSided", true);
            writer.writeKey("pbrMetallicRoughness");
            writer.openObject();
                writer.writeKey("baseColorTexture");
                writer.openObject();
                    writer.writeKeyValue("index", 0);
                writer.closeObject();
                writer.writeKeyValue("metallicFactor", 0.0);
            writer.closeObject();
        writer.closeObject();
        writer.closeArray();
    }

    private void writeNode(JsonWriter writer, Node node) throws IOException {
        writer.openObject();
        if (node.name != null && !node.name.isEmpty()){
            writer.writeKeyValue("name", node.name);
        }
        if (node.mesh >= 0){
            writer.writeKeyValue("mesh", node.mesh);
        }
        if (node.children != null && !node.children.isEmpty()){
            writeNodes(writer, "children", node.children);
        }
        writer.closeObject();
    }

    // write scene 0 which has a single node with id 0
    private void writeScene(JsonWriter writer, List<Node> nodes) throws IOException {
        writer.writeKeyValue("scene", 0);
        writer.writeKey("scenes");
        writer.openArray();
        writer.openObject();
        writer.writeKey("nodes");
        writer.openArray();
        for(var node : nodes) {
            writer.writeValue(node.id);
        }
        writer.closeArray();
        writer.closeObject();
        writer.closeArray();
    }

    private void writeAsset(JsonWriter writer) throws IOException {
        writer.writeKey("asset");
        writer.openObject();
        writer.writeKeyValue("version", "2.0");
        writer.writeKeyValue("generator", "jgbda");
        writer.closeObject();
    }
}