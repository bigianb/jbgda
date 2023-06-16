package net.ijbrown.jbgda.config;

public class ModelDef {
    public String vifName;

    public String[] anmNames;

    public boolean hasAnimation(String anmName)
    {
        if (anmNames == null){
            // nothing defined, so anything goes. Note this differs from an empty list where nothing goes.
            return true;
        }
        for (var candidate: anmNames){
            if (candidate.equals(anmName)){
                return true;
            }
        }
        return false;
    }
}
