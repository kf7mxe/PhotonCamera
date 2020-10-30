package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLConst;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.GLUtils;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import javax.microedition.khronos.opengles.GL;

public class ExposureFusion extends Node {

    public ExposureFusion(String name) {
        super(0, name);
    }
    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        previousNode.WorkingTexture.close();
    }

    GLTexture expose(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        //GLTexture out = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,GLConst.WorkDim));
        if(basePipeline.main1 == null) basePipeline.main1 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,GLConst.WorkDim));
        glProg.drawBlocks(basePipeline.main1);
        //glProg.drawBlocks(out);
        glProg.close();
        return basePipeline.main1;
        //return out;
    }
    GLTexture expose2(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        if(basePipeline.main2 == null) basePipeline.main2 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,GLConst.WorkDim));
        glProg.drawBlocks(basePipeline.main2);
        glProg.close();
        return basePipeline.main2;
    }
    GLTexture unexpose(GLTexture in){
        glProg.useProgram(R.raw.unexpose);
        glProg.setTexture("InputBuffer",in);
        //glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        //if(basePipeline.main2 != null) basePipeline.main2.close();
        basePipeline.main2 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,GLConst.WorkDim),null);
        //GLTexture out = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim),null);
        glProg.drawBlocks(basePipeline.main2);
        glProg.close();
        return basePipeline.main2;
    }

    @Override
    public void Run() {
        GLTexture in = previousNode.WorkingTexture;
        double compressor = (PhotonCamera.getSettings().compressor);
        if(PhotonCamera.getManualMode().getCurrentExposureValue() != 0 && PhotonCamera.getManualMode().getCurrentISOValue() != 0) compressor = 1.f;

        GLUtils.Pyramid highExpo = glUtils.createPyramid(6,2, expose(in,(float)(1.0/compressor)*2.0f));
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(6,2, expose2(in,(float)(1.0/compressor)/5.f));
        //in.close();
        glProg.useProgram(R.raw.fusion);
        glProg.setVar("useUpsampled",0);
        int ind = normalExpo.gauss.length - 1;
        GLTexture wip = new GLTexture(normalExpo.gauss[ind]);
        glProg.setTexture("normalExpo",normalExpo.gauss[ind]);
        glProg.setTexture("highExpo",highExpo.gauss[ind]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[ind]);
        glProg.setTexture("highExpoDiff",highExpo.gauss[ind]);
        //normalExpo.gauss[ind].close();
        //highExpo.gauss[ind].close();
        glProg.drawBlocks(wip);
        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
                //GLTexture upsampleWip = (glUtils.interpolate(wip,normalExpo.sizes[i]));
                //Log.d("ExposureFusion","Before:"+upsampleWip.mSize+" point:"+normalExpo.sizes[i]);
                GLTexture upsampleWip = wip;
                Log.d(Name,"upsampleWip:"+upsampleWip.mSize);
                glProg.useProgram(R.raw.fusion);

                glProg.setTexture("upsampled", upsampleWip);
                glProg.setVar("useUpsampled", 1);
                glProg.setVar("upscaleIn",normalExpo.sizes[i]);
                // We can discard the previous work in progress merge.
                //wip.close();
                if(normalExpo.laplace[i].mSize.equals(basePipeline.main1.mSize)){
                wip = basePipeline.main1;
                } else {
                    wip = new GLTexture(normalExpo.laplace[i]);
                }
                //wip = new GLTexture(normalExpo.laplace[i]);

                // Weigh full image.
                glProg.setTexture("normalExpo", normalExpo.gauss[i]);
                glProg.setTexture("highExpo", highExpo.gauss[i]);

                // Blend feature level.
                glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);
                glProg.setTexture("highExpoDiff", highExpo.laplace[i]);

                glProg.drawBlocks(wip);
                upsampleWip.close();
                if(!normalExpo.gauss[i].mSize.equals(basePipeline.main1.mSize)) {
                    normalExpo.gauss[i].close();
                    highExpo.gauss[i].close();
                }
                normalExpo.laplace[i].close();
                highExpo.laplace[i].close();

        }
        //previousNode.WorkingTexture.close();
        WorkingTexture = unexpose(wip);
        Log.d(Name,"Output Size:"+wip.mSize);
        //wip.close();
        glProg.closed = true;
        //highExpo.releasePyramid();
        //normalExpo.releasePyramid();
    }
}
