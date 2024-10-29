package org.telegram.ui.Components;

import static android.opengl.GLES20.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.GLShader;
import org.telegram.ui.GLShadersManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLGooeyView extends GLSurfaceView {
    public static boolean drawingBlur;

    private final static int COORDINATES_PER_VERTEX = 2;
    private final static float[] QUADRANT_COORDINATES = {
            -1f, -1f,
            1f, -1f,
            1f, 1f,
            -1f, 1f
    };
    private final static float[] TEXTURE_COORDINATES = {
            0, 1,
            1, 1,
            1, 0,
            0, 0
    };
    private final static short[] DRAW_ORDER = {
            0, 1, 2, 0, 2, 3
    };

    private FloatBuffer quadrantCoordinatesBuffer = (FloatBuffer) ByteBuffer.allocateDirect(QUADRANT_COORDINATES.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(QUADRANT_COORDINATES)
            .position(0);

    private FloatBuffer textureCoordinatesBuffer = (FloatBuffer) ByteBuffer.allocateDirect(TEXTURE_COORDINATES.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(TEXTURE_COORDINATES)
            .position(0);

    private ShortBuffer drawOrderBuffer = (ShortBuffer) ByteBuffer.allocateDirect(DRAW_ORDER.length * 4)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(DRAW_ORDER)
            .position(0);

    private GLShadersManager shadersManager = new GLShadersManager();

    private boolean created;

    private int[] fbo = new int[3];
    private int[] fboTex = new int[fbo.length];

    private View containerView;
    private Bitmap containerBitmap;
    private Canvas containerCanvas;

    private float[] fade = {-1, -1};

    public GLGooeyView(Context context) {
        super(context);

        setEGLContextClientVersion(3);
        setZOrderOnTop(true);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.RGBA_8888);
        setRenderer(new Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {}

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                if (created) {
                    onDestroy();
                }
                glViewport(0, 0, width, height);
                onCreate(width, height);
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                if (containerBitmap == null || containerBitmap.getWidth() != containerView.getWidth() || containerBitmap.getHeight() != containerView.getHeight()) {
                    if (containerBitmap != null) {
                        containerBitmap.recycle();
                    }
                    containerBitmap = Bitmap.createBitmap(containerView.getWidth(), containerView.getHeight(), Bitmap.Config.ARGB_8888);
                    containerCanvas = new Canvas(containerBitmap);
                }

                if (containerBitmap == null || containerBitmap.isRecycled() || !isAttachedToWindow()) {
                    return;
                }

                drawingBlur = true;
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, fboTex[2]);
                containerBitmap.eraseColor(0);
                try {
                    if (containerView.isLaidOut() && containerView.isAttachedToWindow()) {
                        containerView.draw(containerCanvas);
                    }
                } catch (Throwable ignored) {}
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, containerBitmap, 0);
                drawingBlur = false;

                int writeBuffer = 0;
                int readBuffer = 1;
                int iterations = 10;

                GLShader shader = shadersManager.get(GLShadersManager.KEY_GAUSSIAN);
                shader.startUsing();
                shader.uniform2f("u_resolution", getWidth(), getHeight());
                shader.uniform4f("u_rect", 0, 0, getWidth(), getHeight());
                int clr = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
                shader.uniform4f("u_bgcolor", Color.red(clr) / (float) 0xFF, Color.green(clr) / (float) 0xFF, Color.blue(clr) / (float) 0xFF, Color.alpha(clr) / (float) 0xFF);
                for (int i = 0; i < iterations; i++) { // iterations
                    float radius = iterations - i - 1;
                    glBindFramebuffer(GL_FRAMEBUFFER, fbo[writeBuffer]);

                    glActiveTexture(GL_TEXTURE0);
                    if (i == 0) {
                        glBindTexture(GL_TEXTURE_2D, fboTex[2]);
                    } else {
                        glBindTexture(GL_TEXTURE_2D, fboTex[readBuffer]);
                    }

                    shader.uniform1i("u_texture", 0);
                    if (i % 2 == 0) {
                        shader.uniform2f("u_direction", radius, 0);
                    } else {
                        shader.uniform2f("u_direction", 0, radius);
                    }

                    glClearColor(0, 0, 0, 0);
                    glClear(GL_COLOR_BUFFER_BIT);

                    drawTexture();

                    int t = writeBuffer;
                    writeBuffer = readBuffer;
                    readBuffer = t;
                }
                shader.stopUsing();

                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glClearColor(0, 0, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                glEnable(GL_BLEND);
                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

                shader = shadersManager.get(GLShadersManager.KEY_GOO);
                shader.startUsing();
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, fboTex[readBuffer]);
                shader.uniform1i("u_texture", 0);
                shader.uniform2f("u_resolution", getWidth(), getHeight());
                drawTexture();
                shader.stopUsing();

                // Draw over everything
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, fboTex[2]);
                containerBitmap.eraseColor(0);
                containerView.draw(containerCanvas);
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, containerBitmap, 0);

                shader = shadersManager.get(GLShadersManager.KEY_TEXTURE);
                shader.startUsing();
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, fboTex[2]);
                shader.uniform1i("u_texture", 0);
                shader.uniform2f("u_resolution", getWidth(), getHeight());
                shader.uniform1i("u_fade", 1);
                shader.uniform1f("u_fade_from", fade[0]);
                shader.uniform1f("u_fade_to", fade[1]);
                drawTexture();
                shader.stopUsing();

                glDisable(GL_BLEND);
            }
        });
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void setFade(float from, float to) {
        fade[0] = from;
        fade[1] = to;
        requestRender();
    }

    private void drawTexture() {
        GLShader shader = shadersManager.getCurrent();
        int posHandle = shader.getAttribLocation("v_position");
        if (posHandle != -1) {
            glVertexAttribPointer(posHandle, COORDINATES_PER_VERTEX, GL_FLOAT, false, COORDINATES_PER_VERTEX * 4, quadrantCoordinatesBuffer);
            glEnableVertexAttribArray(posHandle);
        }
        int texHandle = shader.getAttribLocation("v_tex_coord");
        if (texHandle != -1) {
            glVertexAttribPointer(texHandle, COORDINATES_PER_VERTEX, GL_FLOAT, false, COORDINATES_PER_VERTEX * 4, textureCoordinatesBuffer);
            glEnableVertexAttribArray(texHandle);
        }

        glDrawElements(GL_TRIANGLES, DRAW_ORDER.length, GL_UNSIGNED_SHORT, drawOrderBuffer);
        if (posHandle != -1) {
            glDisableVertexAttribArray(posHandle);
        }
        if (texHandle != -1) {
            glDisableVertexAttribArray(texHandle);
        }
    }

    public void setContainerView(View v) {
        containerView = v;
    }

    private void onCreate(int width, int height) {
        created = true;

        glGenFramebuffers(fbo.length, fbo, 0);
        glGenTextures(fboTex.length, fboTex, 0);
        for (int i = 0; i < fbo.length; i++) {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fboTex[i]);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);

            glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex[i], 0);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void onDestroy() {
        created = false;

        glDeleteFramebuffers(fbo.length, fbo, 0);
        glDeleteTextures(fboTex.length, fboTex, 0);
        shadersManager.release();

        if (containerBitmap != null) {
            containerBitmap.recycle();
            containerBitmap = null;
            containerCanvas = null;
        }
    }
}
