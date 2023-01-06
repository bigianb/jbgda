package net.ijbrown.jbgda.eng;

import imgui.*;
import org.joml.Vector2f;
import net.ijbrown.jbgda.eng.graph.Render;
import net.ijbrown.jbgda.eng.scene.Scene;

public class Engine {

    private IAppLogic appLogic;
    private Render render;
    private boolean running;
    private Scene scene;
    private Window window;

    public Engine(String windowTitle, IAppLogic appLogic) {
        this.appLogic = appLogic;
        window = new Window(windowTitle);
        scene = new Scene(window);
        render = new Render(window, scene);
        appLogic.init(window, scene, render);
    }

    private void cleanup() {
        appLogic.cleanup();
        render.cleanup();
        window.cleanup();
    }

    private boolean handleInputGui() {
        ImGuiIO imGuiIO = ImGui.getIO();
        MouseInput mouseInput = window.getMouseInput();
        Vector2f mousePos = mouseInput.getCurrentPos();
        imGuiIO.setMousePos(mousePos.x, mousePos.y);
        imGuiIO.setMouseDown(0, mouseInput.isLeftButtonPressed());
        imGuiIO.setMouseDown(1, mouseInput.isRightButtonPressed());

        return imGuiIO.getWantCaptureMouse() || imGuiIO.getWantCaptureKeyboard();
    }

    public void run() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        long initialTime = System.nanoTime();
        double timeU = 1000000000d / engineProperties.getUps();
        double deltaU = 0;

        long updateTime = initialTime;
        while (running && !window.shouldClose()) {

            scene.getCamera().setHasMoved(false);
            window.pollEvents();

            long currentTime = System.nanoTime();
            deltaU += (currentTime - initialTime) / timeU;
            initialTime = currentTime;

            if (deltaU >= 1) {
                long diffTimeNanos = currentTime - updateTime;
                boolean inputConsumed = handleInputGui();
                appLogic.handleInput(window, scene, diffTimeNanos, inputConsumed);
                updateTime = currentTime;
                deltaU--;
            }

            render.render(window, scene);
        }

        cleanup();
    }

    public void start() {
        running = true;
        run();
    }

    public void stop() {
        running = false;
    }
}

