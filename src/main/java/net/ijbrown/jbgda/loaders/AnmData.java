package net.ijbrown.jbgda.loaders;

import java.util.List;
import org.joml.*;

public class AnmData
{
    public int numJoints;

    public int numFrames;

    public List<Vector3f> bindingPose;

    /* The local position of each joint relative to the parent. */
    public List<Vector3f> bindingPoseLocal;

    /*
    Skeleton structure.
    This is an odd structure. Consider the entries 0,1,2,3,1,2,3,2,3

    That defines the skeletal relationship. Backtracking effectively starts the count again.

    root - 0 - 1 - 2 - 3
           |
           + - 4 - 5 - 6
               |
               + - 7 - 8

     */
    public List<Integer> skeletonDef;

    public String name;
    public List<Pose> poses;

    public Pose[][] perFramePoses;
    public Pose[][] perFrameFkPoses;

    public static class Pose {
        public int jointNo;
        public int frameNo;
        public Quaternionf rotation;
        public Quaternionf angularVelocity;

        public Vector3f position;

        public Vector3f velocity;
    }
}
