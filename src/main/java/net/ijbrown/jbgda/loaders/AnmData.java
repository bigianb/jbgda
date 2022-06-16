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

    /*
    This is a sanitised version of skeletonDef. The example above would transform to:

    -1, 0, 1, 2, 0, 4, 5, 4, 7

    Each entry is the joint number of the parent with -1 indicating the root.

     */
    public List<Integer> jointParents;

    public String name;

    static class Pose {
        int jointNo;
        int frameNo;
        public Quaternion rotation;
        public Quaternion angularVelocity;
        public Vector3f position;

        public Vector3f velocity;
    }
}
