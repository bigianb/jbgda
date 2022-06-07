package net.ijbrown.jbgda.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;

public class FrameBuffer {

    private final Device device;
    private final long vkFrameBuffer;

    public FrameBuffer(Device device, int width, int height, LongBuffer pAttachments, long renderPass) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFramebufferCreateInfo fci = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .pAttachments(pAttachments)
                    .width(width)
                    .height(height)
                    .layers(1)
                    .renderPass(renderPass);

            LongBuffer lp = stack.mallocLong(1);
            VulkanUtils.vkCheck(vkCreateFramebuffer(device.getVkDevice(), fci, null, lp),
                    "Failed to create FrameBuffer");
            vkFrameBuffer = lp.get(0);
        }
    }

    public void cleanup() {
        vkDestroyFramebuffer(device.getVkDevice(), vkFrameBuffer, null);
    }

    public long getVkFrameBuffer() {
        return vkFrameBuffer;
    }

}