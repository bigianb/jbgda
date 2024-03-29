package net.ijbrown.jbgda.loaders;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class AnmDecoder {
    public AnmData decode(GameType gameType, byte[] data, int startOffset, int len) {
        if (gameType == GameType.DARK_ALLIANCE) {
            return decode(data, startOffset, len);
        } else {
            return decodeRTA(data, startOffset, len);
        }
    }

    public AnmData decode(byte[] data, int startOffset, int len) {
        var anmData = new AnmData();
        anmData.numJoints = DataUtil.getLEInt(data, startOffset);

        int framePoseOffset = startOffset + DataUtil.getLEInt(data, startOffset + 0x08);
        int bindingPoseOffset = startOffset + DataUtil.getLEInt(data, startOffset + 0x0C);
        int skeletonDefOffset = startOffset + DataUtil.getLEInt(data, startOffset + 0x10);

        anmData.bindingPose = new ArrayList<>();
        int elOffset = bindingPoseOffset;
        for (int i = 0; i < anmData.numJoints; ++i) {
            float x = (float) DataUtil.getLEShort(data, elOffset) / -64.0f;
            float y = (float) DataUtil.getLEShort(data, elOffset + 2) / -64.0f;
            float z = (float) DataUtil.getLEShort(data, elOffset + 4) / -64.0f;

            anmData.bindingPose.add(new Vector3f(x, y, z));
            elOffset += 8;
        }

        anmData.skeletonDef = new ArrayList<>();
        for (int i = 0; i < anmData.numJoints; ++i) {
            anmData.skeletonDef.add((int) data[skeletonDefOffset + i]);
        }

        var curPose = new AnmData.Pose[anmData.numJoints];

        anmData.poses = new ArrayList<>();
        for (int jointNo = 0; jointNo < anmData.numJoints; ++jointNo) {

            int frameOffset = framePoseOffset + jointNo * 14;

            float x = (float) DataUtil.getLEShort(data, frameOffset) / 64.0f;
            float y = (float) DataUtil.getLEShort(data, frameOffset + 2) / 64.0f;
            float z = (float) DataUtil.getLEShort(data, frameOffset + 4) / 64.0f;

            float a = (float) DataUtil.getLEShort(data, frameOffset + 6) / 4096.0f;
            float b = (float) DataUtil.getLEShort(data, frameOffset + 8) / 4096.0f;
            float c = (float) DataUtil.getLEShort(data, frameOffset + 10) / 4096.0f;
            float d = (float) DataUtil.getLEShort(data, frameOffset + 12) / 4096.0f;

            var pose = new AnmData.Pose();
            pose.rotation = new Quaternionf(b, c, d, a);
            pose.angularVelocity = new Quaternionf(0, 0, 0, 0);
            pose.position = new Vector3f(x, y, z);
            pose.velocity = new Vector3f(0.0f, 0.0f, 0.0f);
            pose.jointNo = jointNo;
            pose.frameNo = 0;
            anmData.poses.add(pose);

            curPose[jointNo] = pose;
        }

        var curAngVelFrame = new int[anmData.numJoints];
        var curVelFrame = new int[anmData.numJoints];

        var totalFrame = 0;

        AnmData.Pose pose = null;
        int thisFramePoseOffset = framePoseOffset + anmData.numJoints * 14;
        while (thisFramePoseOffset < startOffset + len) {
            int count = data[thisFramePoseOffset++];
            int byte2 = data[thisFramePoseOffset++];
            var jointNo = byte2 & 0x3f;
            if (jointNo == 0x3f) {
                break;
            }
            totalFrame += count;
            if (pose == null || pose.frameNo != totalFrame || pose.jointNo != jointNo) {
                if (pose != null) {
                    anmData.poses.add(pose);
                }
                pose = new AnmData.Pose();
                pose.frameNo = totalFrame;
                pose.jointNo = jointNo;
                pose.position = curPose[jointNo].position;
                pose.rotation = curPose[jointNo].rotation;
                pose.angularVelocity = curPose[jointNo].angularVelocity;
                pose.velocity = curPose[jointNo].velocity;
            }

            // bit 7 specifies whether to read 4 (set) or 3 elements following
            // bit 6 specifies whether they are shorts or bytes (set).
            if ((byte2 & 0x80) == 0x80) {
                int a, b, c, d;
                if ((byte2 & 0x40) == 0x40) {
                    a = data[thisFramePoseOffset++];
                    b = data[thisFramePoseOffset++];
                    c = data[thisFramePoseOffset++];
                    d = data[thisFramePoseOffset++];
                } else {
                    a = DataUtil.getLEShort(data, thisFramePoseOffset);
                    b = DataUtil.getLEShort(data, thisFramePoseOffset + 2);
                    c = DataUtil.getLEShort(data, thisFramePoseOffset + 4);
                    d = DataUtil.getLEShort(data, thisFramePoseOffset + 6);
                    thisFramePoseOffset += 8;
                }
                Quaternionf angVel = new Quaternionf(b, c, d, a);
                var prevAngVel = pose.angularVelocity;
                var coeff = (totalFrame - curAngVelFrame[jointNo]) / 131072.0f;
                Quaternionf angDelta = new Quaternionf(prevAngVel.x * coeff,
                        prevAngVel.y * coeff,
                        prevAngVel.z * coeff,
                        prevAngVel.w * coeff);
                pose.rotation = new Quaternionf(pose.rotation.x + angDelta.x,
                        pose.rotation.y + angDelta.y,
                        pose.rotation.z + angDelta.z,
                        pose.rotation.w + angDelta.w);
                pose.frameNo = totalFrame;
                pose.angularVelocity = angVel;

                curPose[jointNo].rotation = pose.rotation;
                curPose[jointNo].angularVelocity = pose.angularVelocity;
                curAngVelFrame[jointNo] = totalFrame;
            } else {
                int x, y, z;
                if ((byte2 & 0x40) == 0x40) {
                    x = data[thisFramePoseOffset++];
                    y = data[thisFramePoseOffset++];
                    z = data[thisFramePoseOffset++];
                } else {
                    x = DataUtil.getLEShort(data, thisFramePoseOffset);
                    y = DataUtil.getLEShort(data, thisFramePoseOffset + 2);
                    z = DataUtil.getLEShort(data, thisFramePoseOffset + 4);
                    thisFramePoseOffset += 6;
                }

                Vector3f vel = new Vector3f(x, y, z);
                var prevVel = pose.velocity;
                var coeff = (totalFrame - curVelFrame[jointNo]) / 512.0f;
                Vec3F posDelta = new Vec3F(prevVel.x * coeff, prevVel.y * coeff, prevVel.z * coeff);
                pose.position = new Vector3f(pose.position.x + posDelta.x, pose.position.y + posDelta.y, pose.position.z + posDelta.z);
                pose.frameNo = totalFrame;
                pose.velocity = vel;

                curPose[jointNo].position = pose.position;
                curPose[jointNo].velocity = pose.velocity;
                curVelFrame[jointNo] = totalFrame;
            }
        }
        if (pose != null) {
            anmData.poses.add(pose);
        }
        anmData.numFrames = totalFrame + 1;

        BuildPerFramePoses(anmData);
        BuildPerFrameFkPoses(anmData);

        buildKeyframePoses(anmData);

        return anmData;
    }

    public AnmData decodeRTA(byte[] data, int startOffset, int len) {
        var anmData = new AnmData();
        anmData.numJoints = DataUtil.getLEInt(data, startOffset);
        int maxFrames = startOffset + DataUtil.getLEInt(data, startOffset + 0x04);
        int framePoseOffset = startOffset + DataUtil.getLEInt(data, startOffset + 0x08);
        int bindingPoseOffset = startOffset + DataUtil.getLEInt(data, startOffset + 0x0C);
        int skeletonDefOffset = startOffset + DataUtil.getLEInt(data, startOffset + 0x10);

        anmData.bindingPose = new ArrayList<>();
        int elOffset = bindingPoseOffset;
        for (int i = 0; i < anmData.numJoints; ++i) {
            float x = (float) DataUtil.getLEShort(data, elOffset) / -64.0f;
            float y = (float) DataUtil.getLEShort(data, elOffset + 2) / -64.0f;
            float z = (float) DataUtil.getLEShort(data, elOffset + 4) / -64.0f;

            anmData.bindingPose.add(new Vector3f(x, y, z));
            elOffset += 8;
        }

        anmData.skeletonDef = new ArrayList<>();
        for (int i = 0; i < anmData.numJoints; ++i) {
            anmData.skeletonDef.add((int) data[skeletonDefOffset + i]);
        }

        var curPose = new AnmData.Pose[anmData.numJoints];

        // RTA reads the anim as a bit stream
        int frameBitpos = framePoseOffset * 8;
        anmData.poses = new ArrayList<>();
        for (int jointNo = 0; jointNo < anmData.numJoints; ++jointNo) {

            var posLen = DataUtil.getBits(data, frameBitpos, 4, true) + 1;
            frameBitpos += 4;
            float x = (float) DataUtil.getBits(data, frameBitpos, posLen, false) / 64.0f;
            frameBitpos += posLen;
            float y = (float) DataUtil.getBits(data, frameBitpos, posLen, false) / 64.0f;
            frameBitpos += posLen;
            float z = (float) DataUtil.getBits(data, frameBitpos, posLen, false) / 64.0f;
            frameBitpos += posLen;

            var rotLen = DataUtil.getBits(data, frameBitpos, 4, true) + 1;
            frameBitpos += 4;

            float a = (float) DataUtil.getBits(data, frameBitpos, rotLen, false) / 4096.0f;
            frameBitpos += rotLen;
            float b = (float) DataUtil.getBits(data, frameBitpos, rotLen, false) / 4096.0f;
            frameBitpos += rotLen;
            float c = (float) DataUtil.getBits(data, frameBitpos, rotLen, false) / 4096.0f;
            frameBitpos += rotLen;
            float d = (float) DataUtil.getBits(data, frameBitpos, rotLen, false) / 4096.0f;
            frameBitpos += rotLen;

            var pose = new AnmData.Pose();
            pose.rotation = new Quaternionf(b, c, d, a);
            pose.angularVelocity = new Quaternionf(0, 0, 0, 0);
            pose.position = new Vector3f(x, y, z);
            pose.velocity = new Vector3f(0.0f, 0.0f, 0.0f);
            pose.jointNo = jointNo;
            pose.frameNo = 0;
            anmData.poses.add(pose);

            curPose[jointNo] = pose;
        }

        var curAngVelFrame = new int[anmData.numJoints];
        var curVelFrame = new int[anmData.numJoints];

        var frameNumber = 0;

        AnmData.Pose pose = null;

        var maxbitpos = (startOffset + len) * 8;

        while (frameBitpos < (maxbitpos - 22) && frameNumber < maxFrames) {
            int count = DataUtil.getBits(data, frameBitpos, 8, true);
            frameBitpos += 8;
            if (count == 0xFF) {
                break;
            }
            int flag = DataUtil.getBits(data, frameBitpos, 1, true);
            frameBitpos += 1;
            int jointNo = DataUtil.getBits(data, frameBitpos, 6, true);
            frameBitpos += 6;

            if (jointNo >= anmData.numJoints) {
                break;
            }
            frameNumber += count;
            if (pose == null || pose.frameNo != frameNumber || pose.jointNo != jointNo) {
                if (pose != null) {
                    anmData.poses.add(pose);
                }
                pose = new AnmData.Pose();
                pose.frameNo = frameNumber;
                pose.jointNo = jointNo;
                pose.position = curPose[jointNo].position;
                pose.rotation = curPose[jointNo].rotation;
                pose.angularVelocity = curPose[jointNo].angularVelocity;
                pose.velocity = curPose[jointNo].velocity;
            }

            if (flag == 0) {
                var rotLen = DataUtil.getBits(data, frameBitpos, 4, true) + 1;
                frameBitpos += 4;

                int a = DataUtil.getBits(data, frameBitpos, rotLen, false);
                frameBitpos += rotLen;
                int b = DataUtil.getBits(data, frameBitpos, rotLen, false);
                frameBitpos += rotLen;
                int c = DataUtil.getBits(data, frameBitpos, rotLen, false);
                frameBitpos += rotLen;
                int d = DataUtil.getBits(data, frameBitpos, rotLen, false);
                frameBitpos += rotLen;

                Quaternionf angVel = new Quaternionf(b, c, d, a);
                var prevAngVel = pose.angularVelocity;
                var coeff = (frameNumber - curAngVelFrame[jointNo]) / 131072.0f;
                Quaternionf angDelta = new Quaternionf(prevAngVel.x * coeff,
                        prevAngVel.y * coeff,
                        prevAngVel.z * coeff,
                        prevAngVel.w * coeff);
                pose.rotation = new Quaternionf(pose.rotation.x + angDelta.x,
                        pose.rotation.y + angDelta.y,
                        pose.rotation.z + angDelta.z,
                        pose.rotation.w + angDelta.w);
                pose.frameNo = frameNumber;
                pose.angularVelocity = angVel;

                curPose[jointNo].rotation = pose.rotation;
                curPose[jointNo].angularVelocity = pose.angularVelocity;
                curAngVelFrame[jointNo] = frameNumber;
            } else {
                var posLen = DataUtil.getBits(data, frameBitpos, 4, true) + 1;
                frameBitpos += 4;

                int x = DataUtil.getBits(data, frameBitpos, posLen, false);
                frameBitpos += posLen;
                int y = DataUtil.getBits(data, frameBitpos, posLen, false);
                frameBitpos += posLen;
                int z = DataUtil.getBits(data, frameBitpos, posLen, false);
                frameBitpos += posLen;

                Vector3f vel = new Vector3f(x, y, z);
                var prevVel = pose.velocity;
                var coeff = (frameNumber - curVelFrame[jointNo]) / 256.0f;
                Vec3F posDelta = new Vec3F(prevVel.x * coeff, prevVel.y * coeff, prevVel.z * coeff);
                pose.position = new Vector3f(pose.position.x + posDelta.x, pose.position.y + posDelta.y, pose.position.z + posDelta.z);
                pose.frameNo = frameNumber;
                pose.velocity = vel;

                curPose[jointNo].position = pose.position;
                curPose[jointNo].velocity = pose.velocity;
                curVelFrame[jointNo] = frameNumber;
            }
        }
        if (pose != null) {
            anmData.poses.add(pose);
        }
        anmData.numFrames = maxFrames + 1;

        BuildPerFramePoses(anmData);
        BuildPerFrameFkPoses(anmData);

        buildKeyframePoses(anmData);

        return anmData;
    }

    private void BuildPerFramePoses(AnmData anmData) {
        var pendingFramePoses = new AnmData.Pose[anmData.numFrames][anmData.numJoints];
        for (var pose : anmData.poses) {
            if (pose != null && pose.frameNo <= anmData.numFrames) {
                pendingFramePoses[pose.frameNo][pose.jointNo] = pose;
            }
        }

        for (int joint = 0; joint < anmData.numJoints; joint++) {
            AnmData.Pose prevPose = null;
            for (var frame = 0; frame < anmData.numFrames; ++frame) {
                if (pendingFramePoses[frame][joint] == null && prevPose != null) {
                    var frameDiff = frame - prevPose.frameNo;
                    var avCoEff = frameDiff / 131072.0;
                    Quaternionf rotDelta = new Quaternionf(prevPose.angularVelocity.x * avCoEff,
                            prevPose.angularVelocity.y * avCoEff, prevPose.angularVelocity.z * avCoEff,
                            prevPose.angularVelocity.w * avCoEff);

                    var velCoEff = (float) frameDiff / 512.0f;
                    Vector3f posDelta = new Vector3f(prevPose.velocity.x * velCoEff, prevPose.velocity.y * velCoEff,
                            prevPose.velocity.z * velCoEff);

                    AnmData.Pose pose = new AnmData.Pose();
                    pose.jointNo = joint;
                    pose.frameNo = frame;
                    pose.position = new Vector3f(prevPose.position.x + posDelta.x, prevPose.position.y + posDelta.y,
                            prevPose.position.z + posDelta.z);
                    pose.rotation = new Quaternionf(prevPose.rotation.x + rotDelta.x,
                            prevPose.rotation.y + rotDelta.y, prevPose.rotation.z + rotDelta.z,
                            prevPose.rotation.w + rotDelta.w);
                    pose.angularVelocity = prevPose.angularVelocity;
                    pose.velocity = prevPose.velocity;

                    pendingFramePoses[frame][joint] = pose;
                }

                prevPose = pendingFramePoses[frame][joint];
            }
        }

        anmData.perFramePoses = pendingFramePoses;
    }

    private void BuildPerFrameFkPoses(AnmData anmData) {
        if (anmData.perFramePoses == null) {
            throw new IllegalArgumentException("Can't build FK poses until frame poses have been built.");
        }

        // Probably not the best place for it.
        buildLocalBindingPose(anmData);

        var pendingPerFrameFkPoses = new AnmData.Pose[anmData.numFrames][anmData.numJoints];
        var parentPoints = new Vector3f[64];
        var parentRotations = new Quaternionf[64];
        parentPoints[0] = new Vector3f(0, 0, 0);
        parentRotations[0] = new Quaternionf(0, 0, 0, 1);
        for (var frame = 0; frame < anmData.numFrames; ++frame)
        {
            for (var jointNum = 0; jointNum < anmData.skeletonDef.size(); ++jointNum)
            {
                var parentIndex = anmData.skeletonDef.get(jointNum);
                var parentPos = parentPoints[parentIndex];
                var parentRot = parentRotations[parentIndex];

                // The world position of the child joint is the local position of the child joint rotated by the
                // world rotation of the parent and then offset by the world position of the parent.
                var pose = anmData.perFramePoses[frame][jointNum];

                var m = new Matrix3f();
                m.rotation(parentRot);
                var thisPos = m.transform(pose.position, new Vector3f());
                thisPos.add(parentPos.x, parentPos.y, parentPos.z);

                // The world rotation of the child joint is the world rotation of the parent rotated by the local rotation of the child.
                var poseRot = pose.rotation.normalize(new Quaternionf());
                var thisRot = parentRot.mul(poseRot, new Quaternionf());
                thisRot.normalize();

                var fkPose = new AnmData.Pose();
                fkPose.position = thisPos;
                fkPose.rotation = thisRot;
                pendingPerFrameFkPoses[frame][jointNum] = fkPose;

                parentPoints[parentIndex + 1] = fkPose.position;
                parentRotations[parentIndex + 1] = fkPose.rotation;
            }
        }

        anmData.perFrameFkPoses = pendingPerFrameFkPoses;
    }

    private void buildLocalBindingPose(AnmData anmData) {
        anmData.jointParents = transformSkeletondef(anmData.skeletonDef);
        anmData.bindingPoseLocal = new ArrayList<>();
        for(int joint=0; joint< anmData.numJoints; ++joint){
            // Vector3f is mutable and sub mutates it.
            var jointPos = new Vector3f(anmData.bindingPose.get(joint));
            var parentJoint = anmData.jointParents.get(joint);
            if(parentJoint >= 0){
                var parentPos = anmData.bindingPose.get(parentJoint);
                jointPos.sub(parentPos);
            }
            anmData.bindingPoseLocal.add(jointPos);
        }
    }

    private List<Integer> transformSkeletondef(List<Integer> skeletonDef) {
        List<Integer> parents = new ArrayList<>();
        int[] path = new int[skeletonDef.size()+1];
        path[0] = -1;
        for (int jointNo=0; jointNo < skeletonDef.size(); ++jointNo)
        {
            int skelEntry = skeletonDef.get(jointNo);
            parents.add(path[skelEntry]);
            path[skelEntry+1] = jointNo;
        }
        return parents;
    }

    private void buildKeyframePoses(AnmData anmData)
    {
        anmData.keyFrames = new ArrayList<>();
        int thisFrame = anmData.poses.get(0).frameNo;
        var thisKeyframe = new AnmData.KeyFrame();
        thisKeyframe.timestamp = thisFrame / 30.0f;
        thisKeyframe.jointPositions = new ArrayList<>();
        thisKeyframe.jointRotations = new ArrayList<>();
        int numJoints = anmData.numJoints;
        for (int joint=0; joint<numJoints; joint++){
            thisKeyframe.jointPositions.add(new Vector3f(0.0f, 0.0f, 0.0f));
            thisKeyframe.jointRotations.add(new Quaternionf(0.0f, 0.0f, 0.0f, 1.0f));
        }
        for (var pose : anmData.poses){
            if (pose.frameNo > thisFrame){
                thisFrame = pose.frameNo;
                anmData.keyFrames.add(thisKeyframe);
                var nextKeyframe = new AnmData.KeyFrame();
                nextKeyframe.timestamp = thisFrame / 30.0f;
                nextKeyframe.jointPositions = new ArrayList<>(thisKeyframe.jointPositions);
                nextKeyframe.jointRotations = new ArrayList<>(thisKeyframe.jointRotations);
                thisKeyframe = nextKeyframe;
            }
            pose.rotation.normalize();
            thisKeyframe.jointRotations.set(pose.jointNo, pose.rotation);
            thisKeyframe.jointPositions.set(pose.jointNo, pose.position);
        }
        anmData.keyFrames.add(thisKeyframe);
    }

}
