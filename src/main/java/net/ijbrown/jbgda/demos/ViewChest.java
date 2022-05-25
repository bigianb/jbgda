package net.ijbrown.jbgda.demos;

import net.ijbrown.jbgda.eng.*;
import net.ijbrown.jbgda.eng.graph.Render;
import net.ijbrown.jbgda.eng.graph.vk.Device;
import net.ijbrown.jbgda.eng.graph.vk.Texture;
import net.ijbrown.jbgda.eng.scene.*;
import net.ijbrown.jbgda.loaders.*;
import org.joml.*;
import org.tinylog.Logger;

import java.io.IOException;
import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB;

public class ViewChest implements IAppLogic {

    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MOVEMENT_SPEED = 10.0f / 1E9f;

    private float angleInc;
    private Light directionalLight;
    private float lightAngle = 90.1f;

    private final Config config;
    private final Path gameDataPath;

    public static void main(String[] args) {
        Logger.info("Starting application");

        Engine engine = new Engine("View Chest", new ViewChest());
        engine.start();
    }

    public ViewChest()
    {
        this.config = new Config(GameType.DARK_ALLIANCE);
        this.gameDataPath = FileSystems.getDefault().getPath(config.getDataDir());
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public void handleInput(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed) {
        float move = diffTimeMillis * MOVEMENT_SPEED;
        Camera camera = scene.getCamera();
        if (window.isKeyPressed(GLFW_KEY_W)) {
            camera.moveForward(move);
        } else if (window.isKeyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(move);
        }
        if (window.isKeyPressed(GLFW_KEY_A)) {
            camera.moveLeft(move);
        } else if (window.isKeyPressed(GLFW_KEY_D)) {
            camera.moveRight(move);
        }
        if (window.isKeyPressed(GLFW_KEY_UP)) {
            camera.moveUp(move);
        } else if (window.isKeyPressed(GLFW_KEY_DOWN)) {
            camera.moveDown(move);
        }
        if (window.isKeyPressed(GLFW_KEY_LEFT)) {
            angleInc -= 0.05f;
            scene.setLightChanged(true);
        } else if (window.isKeyPressed(GLFW_KEY_RIGHT)) {
            angleInc += 0.05f;
            scene.setLightChanged(true);
        } else {
            angleInc = 0;
            scene.setLightChanged(false);
        }

        MouseInput mouseInput = window.getMouseInput();
        if (mouseInput.isRightButtonPressed()) {
            Vector2f displVec = mouseInput.getDisplVec();
            camera.addPostRotation((float) Math.toRadians(-displVec.x * MOUSE_SENSITIVITY),
                    (float) Math.toRadians(-displVec.y * MOUSE_SENSITIVITY));
        }

        lightAngle += angleInc;
        if (lightAngle < 0) {
            lightAngle = 0;
        } else if (lightAngle > 180) {
            lightAngle = 180;
        }
        updateDirectionalLight();
    }

    private static List<Float> vec3ToFloats(List<VifDecode.Vec3F> vec3s)
    {
        List<Float> floats = new ArrayList<>();
        for (var v3 : vec3s){
            floats.add(v3.x);
            floats.add(v3.y);
            floats.add(v3.z);
        }
        return floats;
    }

    private static ModelData.MeshData processMesh(VifDecode.Mesh mesh, int texW, int texH)
    {
        List<Float> vertices = vec3ToFloats(mesh.vertices);
        List<Float> normals  = vec3ToFloats(mesh.normals);

        float texWf = (float)texW * 16.0f;
        float texHf = (float)texH * 16.0f;

        List<Float> textCoords = new ArrayList<>();
        for (var uv : mesh.uvCoords){
            textCoords.add((float)uv.u / texWf);
            textCoords.add((float)uv.v / texHf);
        }

        List<Integer> indices = mesh.triangleIndices;
        int materialIdx = 0; //aiMesh.mMaterialIndex();
        return new ModelData.MeshData(EngineUtils.listFloatToArray(vertices), EngineUtils.listFloatToArray(normals),
                 EngineUtils.listFloatToArray(textCoords), EngineUtils.listIntToArray(indices), materialIdx);

    }

    private Texture buildTexture(Device device, TexDecode.DecodedTex texData)
    {
        int size = texData.pixels.length * 4;
        var buf = ByteBuffer.allocate(size);
        buf.clear();
        for(var pe : texData.pixels){
            buf.put(pe.r);
            buf.put(pe.g);
            buf.put(pe.b);
            //buf.put(pe.a);
            buf.put((byte)0xff);
        }
        buf.rewind();
        return new Texture(device, buf, texData.pixelsWidth, texData.pixelsHeight, VK_FORMAT_R8G8B8A8_SRGB);
    }

    private ModelData meshToModelData(String modelId, List<VifDecode.Mesh> meshes, TexDecode.DecodedTex texData, Render render)
    {
        var device = render.getDevice();
        var texture = buildTexture(device, texData);

        List<ModelData.Material> materialList = new ArrayList<>();
        ModelData.Material material = new ModelData.Material(texture, ModelData.Material.DEFAULT_COLOR);
        materialList.add(material);

        List<ModelData.MeshData> meshDataList = new ArrayList<>();
        for (VifDecode.Mesh mesh : meshes) {
            ModelData.MeshData meshData = processMesh(mesh, texData.pixelsWidth, texData.pixelsHeight);
            meshDataList.add(meshData);
        }

        ModelData modelData = new ModelData(modelId, meshDataList, materialList);
        return modelData;
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        List<ModelData> modelDataList = new ArrayList<>();

        Path ChestLmpPath = gameDataPath.resolve("CHEST.LMP");
        Lmp chestLmp = new Lmp(GameType.DARK_ALLIANCE);
        try {
            chestLmp.readLmpFile(ChestLmpPath);
            Lmp.Entry chestVif = chestLmp.findEntry("chest_large.vif");
            Lmp.Entry chestTex = chestLmp.findEntry("chest_large.tex");

            var texData = TexDecode.decodeTex(chestTex.data, chestTex.offset, chestTex.length);

            VifDecode vifDecoder = new VifDecode();
            List<VifDecode.Mesh> meshes = vifDecoder.decode(chestVif.data, chestVif.offset);

            String chestLargeId = "chestLargeModel";
            ModelData chestLargeModelData = meshToModelData(chestLargeId, meshes, texData, render);
            modelDataList.add(chestLargeModelData);

            Entity ChestLargeEntity = new Entity("ChestLargeEntity", chestLargeId, new Vector3f(0.0f, 0.0f, 0.0f));
            scene.addEntity(ChestLargeEntity);

            render.loadModels(modelDataList);

        } catch (IOException ioe){

        }

        // In Vulkan RHS, +ve x right, +ve y down, +ve z in
        // We want +ve z up

        Camera camera = scene.getCamera();
        camera.setPosition(0.0f, -60.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(-90.0f), (float) Math.toRadians(0.f));

        scene.getAmbientLight().set(0.2f, 0.2f, 0.2f, 1.0f);
        List<Light> lights = new ArrayList<>();
        directionalLight = new Light();
        directionalLight.getColor().set(1.0f, 1.0f, 1.0f, 1.0f);
        lights.add(directionalLight);
        updateDirectionalLight();

        Light[] lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        scene.setLights(lightArr);
    }

    private void updateDirectionalLight() {
        float zValue = (float) Math.cos(Math.toRadians(lightAngle));
        float yValue = (float) Math.sin(Math.toRadians(lightAngle));
        Vector4f lightDirection = directionalLight.getPosition();
        lightDirection.x = 0;
        lightDirection.y = yValue;
        lightDirection.z = zValue;
        lightDirection.normalize();
        lightDirection.w = 0.0f;
    }

}
