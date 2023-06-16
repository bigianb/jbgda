package net.ijbrown.jbgda.config;

public class GameConfig {

    public ModelDef[] models;

    public ModelDef getModelDef(String vifName){
        if (models != null) {
            for (var modelDef : models) {
                if (modelDef.vifName.equals(vifName)) {
                    return modelDef;
                }
            }
        }
        return new ModelDef();
    }
}
