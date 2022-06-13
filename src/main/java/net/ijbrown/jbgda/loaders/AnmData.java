package net.ijbrown.jbgda.loaders;

import java.util.List;

public class AnmData
{
    public int numJoints;

    public int numFrames;

    public List<Vec3F> bindingPose;

    /*
    Skeleton structure.
    Each element in the array is a joint - element 0 is joint 0 etc.
    The entry for a joint is the joint's parent + 1 (joint -1 is the origin).
    An array of [0, 1] then defines 2 joints with parents [-1, 0]
     */
    public List<Integer> jointParents;
    public String name;

    static class Pose {
        int jointNo;
        int frameNo;
        public Quaternion rotation;
        public Quaternion angularVelocity;
        public Vec3F position;

        public Vec3F velocity;
    }
}
