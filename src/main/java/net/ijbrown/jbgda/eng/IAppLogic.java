package net.ijbrown.jbgda.eng;

import net.ijbrown.jbgda.eng.graph.Render;
import net.ijbrown.jbgda.eng.scene.Scene;

public interface IAppLogic {

    void cleanup();

    void handleInput(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed);

    void init(Window window, Scene scene, Render render);
}
