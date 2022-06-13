package net.ijbrown.jbgda.loaders;

import java.util.List;

public class AnmData
{
    public int numBones;

    public int numFrames;

    public List<Vec3F> bindingPose;

    public List<Integer> skeletonDef;

    static class Pose {
        int boneNo;
        int frameNo;
        public Quaternion rotation;
        public Quaternion angularVelocity;
        public Vec3F position;

        public Vec3F velocity;
    }
}
