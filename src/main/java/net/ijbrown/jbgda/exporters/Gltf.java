package net.ijbrown.jbgda.exporters;

import net.ijbrown.jbgda.loaders.AnmData;
import net.ijbrown.jbgda.loaders.Vec3F;
import net.ijbrown.jbgda.loaders.VifDecode;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
    private final List<AnmData> animations;

    private static class Node
    {
        public int id;
        public String name;
        public int mesh = -1;

        public int skin=-1;

        public int camera = -1;
        public List<Node> children;
        public Vector3f translation;

        public Node(String name) {
            id = -1;
            this.name = name;
            this.children = new ArrayList<>();
        }
    }

    private final List<Node> nodes = new ArrayList<>();

    private final List<Node> sceneNodes = new ArrayList<>();

    private static class Buffer
    {
        public int id;
        public byte[] buffer;
    }

    private final int MATERIAL_ID = 0;

    public Gltf(List<VifDecode.Mesh> meshes, String texName, int texW, int texH, List<AnmData> animations) {
        this.meshes = meshes;
        this.texName = texName;
        this.texW = texW;
        this.texH = texH;
        this.animations = animations;
    }

    public boolean isAnimated()
    {
        return this.animations != null && this.animations.size() > 0;
    }

    public void write(Path outPath) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        try (var writer = Files.newBufferedWriter(outPath, charset)) {
            JsonWriter jsonWriter = new JsonWriter(writer);
            write(jsonWriter);
        }
    }

    private static class Skeleton
    {
        public Skeleton(){
        }

        public Node[] joints;
    }


    private void addNode(Gltf.Node node)
    {
        node.id = nodes.size();
        nodes.add(node);
    }

    private Skeleton buildSkeleton()
    {
        if (animations.size() > 0) {
            var skeleton = new Skeleton();
            var rootNode = new Node("skel_root_"+nodes.size());
            addNode(rootNode);

            var anim = animations.get(0);
            skeleton.joints = new Node[anim.numJoints+1];
            skeleton.joints[0] = rootNode;
            for (int joint=0; joint < anim.numJoints; ++joint) {
                var node = new Node("skel_" + joint + '_' + nodes.size());
                node.translation = anim.bindingPoseLocal.get(joint);
                addNode(node);
                skeleton.joints[joint+1] = node;
            }

            for (int joint=0; joint < anim.numJoints; ++joint){
                var parentJoint = anim.jointParents.get(joint);
                var pj = skeleton.joints[parentJoint+1];
                pj.children.add(skeleton.joints[joint+1]);
            }
            return skeleton;
        }
        return null;
    }



    public void write(JsonWriter writer) throws IOException {
        writer.openObject();
        writeAsset(writer);

        boolean animated = isAnimated();

        writer.writeKey("meshes");
        writer.openArray();
        var meshIdx=0;
        for(var mesh: meshes) {
            writeMesh(writer, mesh);

            Node meshNode = new Node("mesh"+meshIdx);
            addNode(meshNode);
            meshNode.mesh = meshIdx;
            if (animated && mesh.vertexWeights.size() > 0){
                meshNode.skin=0;
            }
            sceneNodes.add(meshNode);
            ++meshIdx;
        }
        writer.closeArray();

        var skeleton = buildSkeleton();
        if (skeleton != null) {
            writeSkeleton(writer, skeleton, animations);
            // The root of the skeleton is part of the scene.
            sceneNodes.add(skeleton.joints[0]);
        }
        writeScene(writer, sceneNodes);

        writeNodes(writer, "nodes", nodes);
        writeImages(writer);
        writeTextures(writer);
        writeMaterials(writer);
        writeBuffers(writer);
        writeAccessors(writer);
        writer.closeObject();
    }

    private void writeSkeleton(JsonWriter writer, Skeleton skeleton, List<AnmData> animations) throws IOException {
        writer.writeKey("skins");
        writer.openArray();
        writer.openObject();

        var invBindAccessor = buildInverseBindingMatrixAccessor(animations.get(0));
        writer.writeKeyValue("inverseBindMatrices", invBindAccessor.id);
        writer.writeKey("joints");
        writer.openArray();
        for (var sj: skeleton.joints){
            writer.writeValue(sj.id);
        }
        writer.closeArray();

        writer.closeObject();
        writer.closeArray();
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
                    writer.writeKeyValue("NORMAL", accessors.normalsAccessor.id);
                    writer.writeKeyValue("TEXCOORD_0", accessors.texcoord0Accessor.id);
                    if (mesh.vertexWeights.size() > 0) {
                        writer.writeKeyValue("JOINTS_0", accessors.joints0Accessor.id);
                        writer.writeKeyValue("WEIGHTS_0", accessors.weights0Accessor.id);
                    }
                writer.closeObject();
                writer.writeKeyValue("indices", accessors.indicesAccessor.id);

                writer.writeKeyValue("material", MATERIAL_ID);
            writer.closeObject();
        writer.closeArray();
        writer.closeObject();
    }

    private enum ComponentType
    {
        UNSIGNED_SHORT(5123), FLOAT(5126), SBYTE(5120), BYTE(5121);

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

        public boolean normalised;

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
            writer.writeKeyValue("normalized", accessor.normalised);
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

        public Accessor normalsAccessor;
        public Accessor indicesAccessor;
        public Accessor texcoord0Accessor;

        public Accessor joints0Accessor;

        public Accessor weights0Accessor;
    }

    private Accessor buildInverseBindingMatrixAccessor(AnmData anmData)
    {
        List<Matrix4f> matrices = new ArrayList<>();
        matrices.add(new Matrix4f());       // Root is at 0
        for (var jointPos : anmData.bindingPose){
            matrices.add(new Matrix4f().translation(-jointPos.x(), -jointPos.y(), -jointPos.z()));
        }

        return buildMat4Accessor(matrices);
    }


    private Accessor buildMat4Accessor(List<Matrix4f> matrices) {
        // mat4 is column major
        int matricesSize = matrices.size() * 16 * 4;
        var matBuffer = createBuffer(matricesSize);
        int i=0;
        for (var matrix : matrices){
            i = writeMatrix(matBuffer.buffer, i, matrix);
        }
        return createAccessor(matBuffer.id, 0, matrices.size(), "MAT4", ComponentType.FLOAT);
    }

    private MeshPrimAccessors buildAccessors(VifDecode.Mesh mesh) {

        // https://www.khronos.org/registry/glTF/specs/2.0/glTF-2.0.html#skinned-mesh-attributes
        
        int positionSize = mesh.vertices.size() * 12;
        int jointsSize = mesh.vertices.size() * 4;          // 4 bytes
        int weightsSize = mesh.vertices.size() * 4;        // 4 unsigned bytes
        int indicesSize = mesh.triangleIndices.size() * 2;
        int texCoordSize = mesh.uvCoords.size() * 2 * 4;
        int normalsSize = mesh.normals.size() * 12;

        var posBuffer = createBuffer(positionSize);
        var idxBuffer = createBuffer(indicesSize);
        var texCoordBuffer = createBuffer(texCoordSize);
        var normalsBuffer = createBuffer(normalsSize);
        var weightsBuffer = createBuffer(weightsSize);
        var jointsBuffer = createBuffer(jointsSize);

        populateWeightsAndJoints(weightsBuffer, jointsBuffer, mesh);

        int i=0;
        Vec3F minPos = new Vec3F(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vec3F maxPos = new Vec3F(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

        for (var vec3 : mesh.vertices){
            minPos = min(minPos, vec3);
            maxPos = max(maxPos, vec3);
            i = writeFloat(posBuffer.buffer, i, vec3.x);
            i = writeFloat(posBuffer.buffer, i, vec3.y);
            i = writeFloat(posBuffer.buffer, i, vec3.z);
        }

        i=0;
        Vec3F minNormal = new Vec3F(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vec3F maxNormal = new Vec3F(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

        for (var vec3 : mesh.normals){
            minNormal = min(minNormal, vec3);
            maxNormal = max(maxNormal, vec3);
            i = writeFloat(normalsBuffer.buffer, i, vec3.x);
            i = writeFloat(normalsBuffer.buffer, i, vec3.y);
            i = writeFloat(normalsBuffer.buffer, i, vec3.z);
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
                minT = t;
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

        meshPrimAccessors.normalsAccessor = createAccessor(normalsBuffer.id, 0, mesh.normals.size(), "VEC3", ComponentType.FLOAT);
        meshPrimAccessors.normalsAccessor.min_fa = new float[]{minNormal.x, minNormal.y, minNormal.z};
        meshPrimAccessors.normalsAccessor.max_fa = new float[]{maxNormal.x, maxNormal.y, maxNormal.z};

        meshPrimAccessors.joints0Accessor = createAccessor(jointsBuffer.id, 0, mesh.vertices.size(), "VEC4", ComponentType.BYTE);
        meshPrimAccessors.weights0Accessor = createAccessor(weightsBuffer.id, 0, mesh.vertices.size(), "VEC4", ComponentType.BYTE);
        meshPrimAccessors.weights0Accessor.normalised=true;
        return meshPrimAccessors;
    }

    private void populateWeightsAndJoints(Buffer weightsBuffer, Buffer jointsBuffer, VifDecode.Mesh mesh) {
        /*
            The JOINTS_0 attribute data contains the indices of the
            joints that should affect the vertex.
            The WEIGHTS_0 attribute data defines the weights indicating
            how strongly the joint should influence the vertex
         */

        int numVertices = mesh.vertices.size();
        for (var i=0; i<numVertices*4; ++i){
            jointsBuffer.buffer[i] = 0;
            weightsBuffer.buffer[i] = 0;
        }

        for (var vw : mesh.vertexWeights){
            int bone1 = vw.bone1 == 255 ? 0 : vw.bone1+1;
            int bone2 = vw.bone2 == 255 ? 0 : vw.bone2+1;
            int bone3 = vw.bone3 == 255 ? 0 : vw.bone3+1;
            int bone4 = vw.bone4 == 255 ? 0 : vw.bone4+1;
            for (var vnum=vw.startVertex; vnum <= vw.endVertex; ++vnum){
                int idx = vnum*4;
                jointsBuffer.buffer[idx] = (byte)bone1;
                jointsBuffer.buffer[idx+1] = (byte)bone2;
                jointsBuffer.buffer[idx+2] = (byte)bone3;
                jointsBuffer.buffer[idx+3] = (byte)bone4;

                weightsBuffer.buffer[idx] = (byte)vw.boneWeight1;
                weightsBuffer.buffer[idx+1] = (byte)vw.boneWeight2;
                weightsBuffer.buffer[idx+2] = (byte)vw.boneWeight3;
                weightsBuffer.buffer[idx+3] = (byte)vw.boneWeight4;
            }
        }
    }

    private Vec3F min(Vec3F minPos, Vec3F vec3) {
        return new Vec3F(Math.min(minPos.x, vec3.x),
                Math.min(minPos.y, vec3.y),
                Math.min(minPos.z, vec3.z));
    }

    private Vec3F max(Vec3F minPos, Vec3F vec3) {
        return new Vec3F(Math.max(minPos.x, vec3.x),
                Math.max(minPos.y, vec3.y),
                Math.max(minPos.z, vec3.z));
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

    private int writeMatrix(byte[] buffer, int iIn, Matrix4f matrix) {
        int i=iIn;
        for (int c=0; c<4; ++c){
            for (int r=0; r<4; ++r){
                float f = matrix.get(c, r);
                i = writeFloat(buffer, i, f);
            }
        }
        return i;
    }

    private void writeNodes(JsonWriter writer, String name, List<Node> nodes) throws IOException {
        writer.writeKey(name);
        writer.openArray();
        for (Node node : nodes) {
            writeNode(writer, node);
        }
        writer.closeArray();
    }

    private void writeNodeRefs(JsonWriter writer, String name, List<Node> nodes) throws IOException {
        writer.writeKey(name);
        writer.openArray();
        for (Node node : nodes) {
            writer.writeValue(node.id);
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
        if (node.skin >= 0){
            writer.writeKeyValue("skin", node.skin);
        }
        if (node.translation != null){
            writer.writeKeyValue("translation", node.translation);
        }
        if (node.children != null && !node.children.isEmpty()){
            writeNodeRefs(writer, "children", node.children);
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
