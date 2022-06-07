package net.ijbrown.jbgda.eng.graph.vk;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.vulkan.VK11.*;

public class Texture {

    private final int height;
    private final int mipLevels;
    private final int width;
    private String fileName;
    private boolean hasTransparencies;
    private Image image;
    private ImageView imageView;
    private boolean recordedTransition;
    private VulkanBuffer stgBuffer;

    public Texture(Device device, String fileName, int imageFormat) {
        Logger.debug("Creating texture [{}]", fileName);
        recordedTransition = false;
        this.fileName = fileName;
        ByteBuffer buf;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            buf = stbi_load(fileName, w, h, channels, 4);
            if (buf == null) {
                throw new RuntimeException("Image file [" + fileName + "] not loaded: " + stbi_failure_reason());
            }
            setHasTransparencies(buf);

            width = w.get();
            height = h.get();
            mipLevels = (int) Math.floor(log2(Math.min(width, height))) + 1;

            createTextureResources(device, buf, imageFormat);
        }

        stbi_image_free(buf);
    }

    public Texture(Device device, ByteBuffer buf, int width, int height, int imageFormat) {
        this.width = width;
        this.height = height;
        this.fileName = "x"+width+"_"+height;
        mipLevels = 1;

        createTextureResources(device, buf, imageFormat);
    }

    public void cleanup() {
        cleanupStgBuffer();
        imageView.cleanup();
        image.cleanup();
    }

    public void cleanupStgBuffer() {
        if (stgBuffer != null) {
            stgBuffer.cleanup();
            stgBuffer = null;
        }
    }

    private void createStgBuffer(Device device, ByteBuffer data) {
        int size = data.remaining();
        stgBuffer = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        long mappedMemory = stgBuffer.map();
        ByteBuffer buffer = MemoryUtil.memByteBuffer(mappedMemory, (int) stgBuffer.getRequestedSize());
        buffer.put(data);
        data.flip();

        stgBuffer.unMap();
    }

    private void createTextureResources(Device device, ByteBuffer buf, int imageFormat) {
        createStgBuffer(device, buf);
        Image.ImageData imageData = new Image.ImageData().width(width).height(height).
                usage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(imageFormat).mipLevels(mipLevels);
        image = new Image(device, imageData);
        ImageView.ImageViewData imageViewData = new ImageView.ImageViewData().format(image.getFormat()).
                aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevels(mipLevels);
        imageView = new ImageView(device, image.getVkImage(), imageViewData);
    }

    public String getFileName() {
        return fileName;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public boolean hasTransparencies() {
        return hasTransparencies;
    }

    private double log2(int n) {
        return Math.log(n) / Math.log(2);
    }

    private void recordCopyBuffer(MemoryStack stack, CommandBuffer cmd, VulkanBuffer bufferData) {

        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                .bufferOffset(0)
                .bufferRowLength(0)
                .bufferImageHeight(0)
                .imageSubresource(it ->
                        it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0)
                                .baseArrayLayer(0)
                                .layerCount(1)
                )
                .imageOffset(it -> it.x(0).y(0).z(0))
                .imageExtent(it -> it.width(width).height(height).depth(1));

        vkCmdCopyBufferToImage(cmd.getVkCommandBuffer(), bufferData.getBuffer(), image.getVkImage(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    }

    private void recordImageTransition(MemoryStack stack, CommandBuffer cmd, int oldLayout, int newLayout) {

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image.getVkImage())
                .subresourceRange(it -> it
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(0)
                        .layerCount(1));

        int srcStage;
        int srcAccessMask;
        int dstAccessMask;
        int dstStage;

        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            srcAccessMask = 0;
            dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        } else {
            throw new RuntimeException("Unsupported layout transition");
        }

        barrier.srcAccessMask(srcAccessMask);
        barrier.dstAccessMask(dstAccessMask);

        vkCmdPipelineBarrier(cmd.getVkCommandBuffer(), srcStage, dstStage, 0, null, null, barrier);
    }

    public void recordTextureTransition(CommandBuffer cmd) {
        if (stgBuffer != null && !recordedTransition) {
            Logger.debug("Recording transition for texture [{}]", fileName);
            recordedTransition = true;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                recordImageTransition(stack, cmd, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                recordCopyBuffer(stack, cmd, stgBuffer);
                recordImageTransition(stack, cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        } else {
            Logger.debug("Texture [{}] has already been transitioned", fileName);
        }
    }

    private void setHasTransparencies(ByteBuffer buf) {
        int numPixels = buf.capacity() / 4;
        int offset = 0;
        hasTransparencies = false;
        for (int i = 0; i < numPixels; i++) {
            int a = (0xFF & buf.get(offset + 3));
            if (a < 255) {
                hasTransparencies = true;
                break;
            }
            offset += 4;
        }
    }
}
