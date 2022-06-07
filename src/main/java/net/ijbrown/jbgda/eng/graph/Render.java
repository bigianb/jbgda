package net.ijbrown.jbgda.eng.graph;

import net.ijbrown.jbgda.eng.EngineProperties;
import net.ijbrown.jbgda.eng.Window;
import net.ijbrown.jbgda.eng.graph.vk.*;
import net.ijbrown.jbgda.eng.scene.ModelData;
import net.ijbrown.jbgda.eng.scene.Scene;
import org.tinylog.Logger;
import net.ijbrown.jbgda.eng.graph.vk.Queue;


import java.util.*;
public class Render {

    private final CommandPool commandPool;
    private final Device device;
    public Device getDevice() {
        return device;
    }
    private final ForwardRenderActivity fwdRenderActivity;
    private final Queue.GraphicsQueue graphQueue;
    private final Instance instance;
    private final PhysicalDevice physicalDevice;
    private final PipelineCache pipelineCache;
    private final Queue.PresentQueue presentQueue;
    private final Surface surface;
    private final TextureCache textureCache;
    private final List<VulkanModel> vulkanModels;

    private SwapChain swapChain;

    public Render(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.getPhysDeviceName());
        device = new Device(instance, physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
        presentQueue = new Queue.PresentQueue(device, surface, 0);
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        commandPool = new CommandPool(device, graphQueue.getQueueFamilyIndex());
        pipelineCache = new PipelineCache(device);
        fwdRenderActivity = new ForwardRenderActivity(swapChain, commandPool, pipelineCache, scene);
        vulkanModels = new ArrayList<>();
        textureCache = new TextureCache();
    }

    public void cleanup() {
        presentQueue.waitIdle();
        graphQueue.waitIdle();
        device.waitIdle();
        textureCache.cleanup();
        vulkanModels.forEach(VulkanModel::cleanup);
        pipelineCache.cleanup();
        fwdRenderActivity.cleanup();
        commandPool.cleanup();
        swapChain.cleanup();
        surface.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void loadModels(List<ModelData> modelDataList) {
        Logger.debug("Loading {} model(s)", modelDataList.size());
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, textureCache, commandPool, graphQueue));
        Logger.debug("Loaded {} model(s)", modelDataList.size());
/*
        // Reorder materials inside models
        vulkanModels.forEach(m -> {
            Collections.sort(m.getVulkanMaterialList(), (a, b) -> Boolean.compare(a.isTransparent(), b.isTransparent()));
        });

        // Reorder models
        Collections.sort(vulkanModels, (a, b) -> {
            boolean aHasTransparentMt = a.getVulkanMaterialList().stream().filter(m -> m.isTransparent()).findAny().isPresent();
            boolean bHasTransparentMt = b.getVulkanMaterialList().stream().filter(m -> m.isTransparent()).findAny().isPresent();

            return Boolean.compare(aHasTransparentMt, bHasTransparentMt);
        });
*/
        fwdRenderActivity.registerModels(vulkanModels);
    }

    public void render(Window window, Scene scene) {
        if (window.getWidth() <= 0 && window.getHeight() <= 0) {
            return;
        }
        if (window.isResized() || swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window);
            scene.getProjection().resize(window.getWidth(), window.getHeight());
            swapChain.acquireNextImage();
        }

        fwdRenderActivity.recordCommandBuffer(vulkanModels);
        fwdRenderActivity.submit(presentQueue);

        if (swapChain.presentImage(graphQueue)) {
            window.setResized(true);
        }
    }

    private void resize(Window window) {
        EngineProperties engProps = EngineProperties.getInstance();

        device.waitIdle();
        graphQueue.waitIdle();

        swapChain.cleanup();

        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        fwdRenderActivity.resize(swapChain);
    }
}