package net.ijbrown.jbgda.loaders;

import org.joml.Vector3f;

import java.util.ArrayList;

public class AnmDecoder
{
    public AnmData decode(byte[] data, int startOffset, int len)
    {
        var anmData = new AnmData();
        anmData.numJoints = DataUtil.getLEInt(data, startOffset);

        int framePoseOffset = startOffset + DataUtil.getLEInt(data, startOffset+0x08);
        int bindingPoseOffset = startOffset + DataUtil.getLEInt(data, startOffset+0x0C);
        int skeletonDefOffset = startOffset + DataUtil.getLEInt(data, startOffset+0x10);

        anmData.bindingPose = new ArrayList<>();
        int elOffset = bindingPoseOffset;
        for (int i = 0; i < anmData.numJoints; ++i){
            float x = (float)DataUtil.getLEShort(data, elOffset) / 64.0f;
            float y = (float)DataUtil.getLEShort(data, elOffset+2) / 64.0f;
            float z = (float)DataUtil.getLEShort(data, elOffset+4) / 64.0f;

            anmData.bindingPose.add(new Vector3f(x, y, z));
            elOffset += 8;
        }

        anmData.jointParents = new ArrayList<>();
        for (int i = 0; i < anmData.numJoints; ++i) {
            anmData.jointParents.add((int) data[skeletonDefOffset+i]);
        }

        var curPose = new AnmData.Pose[anmData.numJoints];

        var poses = new ArrayList<AnmData.Pose>();
        for (int jointNo = 0; jointNo < anmData.numJoints; ++jointNo) {

            int frameOffset = framePoseOffset + jointNo * 14;

            float x = (float)DataUtil.getLEShort(data, frameOffset) / 64.0f;
            float y = (float)DataUtil.getLEShort(data, frameOffset+2) / 64.0f;
            float z = (float)DataUtil.getLEShort(data, frameOffset+4) / 64.0f;

            float a = (float)DataUtil.getLEShort(data, frameOffset + 6) / 4096.0f;
            float b = (float)DataUtil.getLEShort(data, frameOffset + 8) / 4096.0f;
            float c = (float)DataUtil.getLEShort(data, frameOffset + 10) / 4096.0f;
            float d = (float)DataUtil.getLEShort(data, frameOffset + 12) / 4096.0f;

            var pose = new AnmData.Pose();
            pose.rotation = new Quaternion(b, c, d, a);
            pose.angularVelocity = new Quaternion(0, 0, 0, 0);
            pose.position = new Vector3f(x, y, z);
            pose.velocity = new Vector3f(0.0f, 0.0f, 0.0f);
            pose.jointNo = jointNo;
            pose.frameNo = 0;
            poses.add(pose);

            curPose[jointNo] = pose;
        }

        var curAngVelFrame = new int[anmData.numJoints];
        var curVelFrame = new int[anmData.numJoints];

        var totalFrame = 0;

        AnmData.Pose pose = null;
        int thisFramePoseOffset = framePoseOffset + anmData.numJoints * 14;
        while (thisFramePoseOffset < startOffset+len){
            int count = data[thisFramePoseOffset++];
            int byte2 = data[thisFramePoseOffset++];
            var jointNo = byte2 & 0x3f;
            if (jointNo == 0x3f) {
                break;
            }
            totalFrame += count;
            if (pose == null || pose.frameNo != totalFrame || pose.jointNo != jointNo){
                if (pose != null)
                {
                    poses.add(pose);
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
                Quaternion angVel = new Quaternion(b, c, d, a);
                var prevAngVel = pose.angularVelocity;
                var coeff = (totalFrame - curAngVelFrame[jointNo]) / 131072.0f;
                Quaternion angDelta = new Quaternion(prevAngVel.a * coeff,
                        prevAngVel.b * coeff,
                        prevAngVel.c * coeff,
                        prevAngVel.w * coeff);
                pose.rotation = new Quaternion(pose.rotation.a + angDelta.a,
                        pose.rotation.b + angDelta.b,
                        pose.rotation.c + angDelta.c,
                        pose.rotation.w + angDelta.w);
                pose.frameNo = totalFrame;
                pose.angularVelocity = angVel;

                curPose[jointNo].rotation = pose.rotation;
                curPose[jointNo].angularVelocity = pose.angularVelocity;
                curAngVelFrame[jointNo] = totalFrame;
            } else {
                int x,y,z;
                if ((byte2 & 0x40) == 0x40)
                {
                    x = data[thisFramePoseOffset++];
                    y = data[thisFramePoseOffset++];
                    z = data[thisFramePoseOffset++];
                }
                else
                {
                    x = DataUtil.getLEShort(data, thisFramePoseOffset);
                    y = DataUtil.getLEShort(data, thisFramePoseOffset + 2);
                    z = DataUtil.getLEShort(data, thisFramePoseOffset + 4);
                    thisFramePoseOffset += 6;
                }

                Vector3f vel = new Vector3f(x, y, z);
                var prevVel = pose.velocity;
                var coeff = (totalFrame - curVelFrame[jointNo]) / 512.0f;
                Vec3F posDelta = new Vec3F(prevVel.x * coeff, prevVel.y * coeff, prevVel.z * coeff);
                pose.position = new Vector3f(pose.position.x + posDelta.x, pose.position.y + posDelta.y,pose.position.z + posDelta.z);
                pose.frameNo = totalFrame;
                pose.velocity = vel;

                curPose[jointNo].position = pose.position;
                curPose[jointNo].velocity = pose.velocity;
                curVelFrame[jointNo] = totalFrame;
            }
        }
        if (pose != null){
            poses.add(pose);
        }
        anmData.numFrames = totalFrame+1;

        //animData.BuildPerFramePoses();
        //animData.BuildPerFrameFKPoses();

        return anmData;
    }
}
