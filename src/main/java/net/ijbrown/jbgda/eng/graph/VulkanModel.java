package net.ijbrown.jbgda.eng.graph;

import org.joml.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.VkBufferCopy;
import net.ijbrown.jbgda.eng.graph.vk.Queue;
import net.ijbrown.jbgda.eng.graph.vk.*;
import net.ijbrown.jbgda.eng.scene.ModelData;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class VulkanModel {

    private final String modelId;
    private final List<VulkanModel.VulkanMaterial> vulkanMaterialList;

    private List<VulkanModel.VulkanAnimation> animationList;

    public VulkanModel(String modelId) {
        this.modelId = modelId;
        vulkanMaterialList = new ArrayList<>();
    }

    private static TransferBuffers createIndicesBuffers(Device device, ModelData.MeshData meshData) {
        int[] indices = meshData.indices();
        int numIndices = indices.length;
        int bufferSize = numIndices * GraphConstants.INT_LENGTH;

        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        IntBuffer data = MemoryUtil.memIntBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        data.put(indices);
        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    private static TransferBuffers createJointMatricesBuffers(Device device, ModelData.AnimatedFrame frame) {
        Matrix4f[] matrices = frame.jointMatrices();
        int numMatrices = matrices.length;
        int bufferSize = numMatrices * GraphConstants.MAT4X4_SIZE;
        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        for (int i = 0; i < numMatrices; i++) {
            matrices[i].get(i * GraphConstants.MAT4X4_SIZE, matrixBuffer);
        }
        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    private static TransferBuffers createVerticesBuffers(Device device, ModelData.MeshData meshData) {
        float[] positions = meshData.positions();
        float[] normals = meshData.normals();
        float[] tangents = meshData.tangents();
        float[] biTangents = meshData.biTangents();
        float[] textCoords = meshData.textCoords();
        if (textCoords == null || textCoords.length == 0) {
            textCoords = new float[(positions.length / 3) * 2];
        }
        int numElements = positions.length + normals.length + tangents.length + biTangents.length + textCoords.length;
        int bufferSize = numElements * GraphConstants.FLOAT_LENGTH;

        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        FloatBuffer data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int rows = positions.length / 3;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 3;
            int startTextCoord = row * 2;
            data.put(positions[startPos]);
            data.put(positions[startPos + 1]);
            data.put(positions[startPos + 2]);
            data.put(normals[startPos]);
            data.put(normals[startPos + 1]);
            data.put(normals[startPos + 2]);
            data.put(tangents[startPos]);
            data.put(tangents[startPos + 1]);
            data.put(tangents[startPos + 2]);
            data.put(biTangents[startPos]);
            data.put(biTangents[startPos + 1]);
            data.put(biTangents[startPos + 2]);
            data.put(textCoords[startTextCoord]);
            data.put(textCoords[startTextCoord + 1]);
        }

        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    private static TransferBuffers createWeightsBuffers(Device device, ModelData.AnimMeshData animMeshData) {
        float[] weights = animMeshData.weights();
        int[] boneIds = animMeshData.boneIds();
        int bufferSize = weights.length * GraphConstants.FLOAT_LENGTH + boneIds.length * GraphConstants.INT_LENGTH;

        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        FloatBuffer data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int rows = weights.length / 4;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 4;
            data.put(weights[startPos]);
            data.put(weights[startPos + 1]);
            data.put(weights[startPos + 2]);
            data.put(weights[startPos + 3]);
            data.put(boneIds[startPos]);
            data.put(boneIds[startPos + 1]);
            data.put(boneIds[startPos + 2]);
            data.put(boneIds[startPos + 3]);
        }

        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    private static void recordTransferCommand(CommandBuffer cmd, TransferBuffers transferBuffers) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0).dstOffset(0).size(transferBuffers.srcBuffer().getRequestedSize());
            vkCmdCopyBuffer(cmd.getVkCommandBuffer(), transferBuffers.srcBuffer().getBuffer(),
                    transferBuffers.dstBuffer().getBuffer(), copyRegion);
        }
    }

    private static VulkanMaterial transformMaterial(ModelData.Material material, Device device, TextureCache textureCache,
                                                    CommandBuffer cmd, List<Texture> textureList) {
        Texture texture = textureCache.createTexture(device, material.texturePath(), VK_FORMAT_R8G8B8A8_SRGB);
        boolean hasTexture = material.texturePath() != null && material.texturePath().trim().length() > 0;
        Texture normalMapTexture = textureCache.createTexture(device, material.normalMapPath(), VK_FORMAT_R8G8B8A8_UNORM);
        boolean hasNormalMapTexture = material.normalMapPath() != null && material.normalMapPath().trim().length() > 0;
        Texture metalRoughTexture = textureCache.createTexture(device, material.metalRoughMap(), VK_FORMAT_R8G8B8A8_UNORM);
        boolean hasMetalRoughTexture = material.metalRoughMap() != null && material.metalRoughMap().trim().length() > 0;

        texture.recordTextureTransition(cmd);
        textureList.add(texture);
        normalMapTexture.recordTextureTransition(cmd);
        textureList.add(normalMapTexture);
        metalRoughTexture.recordTextureTransition(cmd);
        textureList.add(metalRoughTexture);

        return new VulkanModel.VulkanMaterial(material.diffuseColor(), texture, hasTexture, normalMapTexture,
                hasNormalMapTexture, metalRoughTexture, hasMetalRoughTexture, material.metallicFactor(),
                material.roughnessFactor(), new ArrayList<>());
    }

    public static List<VulkanModel> transformModels(List<ModelData> modelDataList, TextureCache textureCache,
                                                    CommandPool commandPool, Queue queue) {

        List<VulkanModel> vulkanModelList = new ArrayList<>();
        Device device = commandPool.getDevice();
        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
        List<VulkanBuffer> stagingBufferList = new ArrayList<>();
        List<Texture> textureList = new ArrayList<>();

        cmd.beginRecording();

        for (ModelData modelData : modelDataList) {
            VulkanModel vulkanModel = new VulkanModel(modelData.getModelId());
            vulkanModelList.add(vulkanModel);

            // Create textures defined for the materials
            VulkanMaterial defaultVulkanMaterial = null;
            for (ModelData.Material material : modelData.getMaterialList()) {
                VulkanMaterial vulkanMaterial = transformMaterial(material, device, textureCache, cmd, textureList);
                vulkanModel.vulkanMaterialList.add(vulkanMaterial);
            }

            List<ModelData.Animation> animationsList = modelData.getAnimationsList();
            boolean hasAnimation = animationsList != null && !animationsList.isEmpty();
            if (hasAnimation) {
                vulkanModel.animationList = new ArrayList<>();
                for (ModelData.Animation animation : animationsList) {
                    List<VulkanBuffer> vulkanFrameBufferList = new ArrayList<>();
                    VulkanAnimation vulkanAnimation = new VulkanAnimation(animation.name(), vulkanFrameBufferList);
                    vulkanModel.animationList.add(vulkanAnimation);
                    List<ModelData.AnimatedFrame> frameList = animation.frames();
                    for (ModelData.AnimatedFrame frame : frameList) {
                        TransferBuffers jointMatricesBuffers = createJointMatricesBuffers(device, frame);
                        stagingBufferList.add(jointMatricesBuffers.srcBuffer());
                        recordTransferCommand(cmd, jointMatricesBuffers);
                        vulkanFrameBufferList.add(jointMatricesBuffers.dstBuffer);
                    }
                }
            }

            // Transform meshes loading their data into GPU buffers
            int meshCount = 0;
            for (ModelData.MeshData meshData : modelData.getMeshDataList()) {
                TransferBuffers verticesBuffers = createVerticesBuffers(device, meshData);
                TransferBuffers indicesBuffers = createIndicesBuffers(device, meshData);
                stagingBufferList.add(verticesBuffers.srcBuffer());
                stagingBufferList.add(indicesBuffers.srcBuffer());
                recordTransferCommand(cmd, verticesBuffers);
                recordTransferCommand(cmd, indicesBuffers);

                TransferBuffers weightsBuffers = null;
                List<ModelData.AnimMeshData> animMeshDataList = modelData.getAnimMeshDataList();
                if (animMeshDataList != null && !animMeshDataList.isEmpty()) {
                    weightsBuffers = createWeightsBuffers(device, animMeshDataList.get(meshCount));
                    stagingBufferList.add(weightsBuffers.srcBuffer());
                    recordTransferCommand(cmd, weightsBuffers);
                }

                VulkanModel.VulkanMesh vulkanMesh = new VulkanModel.VulkanMesh(verticesBuffers.dstBuffer(),
                        indicesBuffers.dstBuffer(), meshData.indices().length,
                        weightsBuffers != null ? weightsBuffers.dstBuffer() : null);

                VulkanMaterial vulkanMaterial;
                int materialIdx = meshData.materialIdx();
                if (materialIdx >= 0 && materialIdx < vulkanModel.vulkanMaterialList.size()) {
                    vulkanMaterial = vulkanModel.vulkanMaterialList.get(materialIdx);
                } else {
                    if (defaultVulkanMaterial == null) {
                        defaultVulkanMaterial = transformMaterial(new ModelData.Material(), device, textureCache, cmd, textureList);
                    }
                    vulkanMaterial = defaultVulkanMaterial;
                }
                vulkanMaterial.vulkanMeshList.add(vulkanMesh);
                meshCount++;
            }
        }

        cmd.endRecording();
        cmd.submitAndWait(device, queue);
        cmd.cleanup();

        stagingBufferList.forEach(VulkanBuffer::cleanup);
        textureList.forEach(Texture::cleanupStgBuffer);

        return vulkanModelList;
    }

    public void cleanup() {
        vulkanMaterialList.forEach(m -> m.vulkanMeshList.forEach((VulkanMesh::cleanup)));
        if (animationList != null) {
            animationList.forEach(VulkanAnimation::cleanup);
        }
    }

    public List<VulkanModel.VulkanAnimation> getAnimationList() {
        return animationList;
    }

    public String getModelId() {
        return modelId;
    }

    public List<VulkanModel.VulkanMaterial> getVulkanMaterialList() {
        return vulkanMaterialList;
    }

    public boolean hasAnimations() {
        return animationList != null && !animationList.isEmpty();
    }

    private record TransferBuffers(VulkanBuffer srcBuffer, VulkanBuffer dstBuffer) {
    }

    public record VulkanAnimation(String name, List<VulkanBuffer> frameBufferList) {
        public void cleanup() {
            frameBufferList.forEach(VulkanBuffer::cleanup);
        }

    }

    public record VulkanMaterial(Vector4f diffuseColor, Texture texture, boolean hasTexture, Texture normalMap,
                                 boolean hasNormalMap, Texture metalRoughMap, boolean hasMetalRoughMap,
                                 float metallicFactor, float roughnessFactor, List<VulkanMesh> vulkanMeshList) {

        public boolean isTransparent() {
            return texture.hasTransparencies();
        }
    }

    public record VulkanMesh(VulkanBuffer verticesBuffer, VulkanBuffer indicesBuffer, int numIndices,
                             VulkanBuffer weightsBuffer) {
        public void cleanup() {
            verticesBuffer.cleanup();
            indicesBuffer.cleanup();
            if (weightsBuffer != null) {
                weightsBuffer.cleanup();
            }
        }
    }
}