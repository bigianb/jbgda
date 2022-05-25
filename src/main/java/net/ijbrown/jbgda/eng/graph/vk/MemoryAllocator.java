package net.ijbrown.jbgda.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.util.vma.Vma.*;

public class MemoryAllocator {

    private final long vmaAllocator;

    public MemoryAllocator(Instance instance, PhysicalDevice physicalDevice, VkDevice vkDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pAllocator = stack.mallocPointer(1);

            VmaVulkanFunctions vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                    .set(instance.getVkInstance(), vkDevice);

            VmaAllocatorCreateInfo createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance.getVkInstance())
                    .device(vkDevice)
                    .physicalDevice(physicalDevice.getVkPhysicalDevice())
                    .pVulkanFunctions(vmaVulkanFunctions);
            VulkanUtils.vkCheck(vmaCreateAllocator(createInfo, pAllocator),
                    "Failed to create VMA allocator");

            vmaAllocator = pAllocator.get(0);
        }
    }

    public void cleanUp() {
        vmaDestroyAllocator(vmaAllocator);
    }

    public long getVmaAllocator() {
        return vmaAllocator;
    }
}
