package org.telegram.ui.Components;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

import javax.microedition.khronos.egl.EGL;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class ThanosLayout extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final static int STRIDE = 48;
    private final static float VISIBLE_PERCENT = 0.05f;
    private final static TimeInterpolator INTERPOLATOR = input -> input;

    private int[] pos = new int[2];

    private List<Entity> entities = new ArrayList<>();
    private TextureView textureView;
    private View overlayView;

    private HandlerThread eglThread;
    private Handler eglHandler;
    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLConfig eglConfig;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private int drawProgram;
    private int deltaTimeHandle;
    private int sizeHandle;
    private int radiusHandle;

    private static Integer maxPoints;
    private static Integer maxPointsTotal;
    private static Integer radius;

    private long lastDraw;
    private Runnable frameCallback = ()->{
        if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            FileLog.e("Failed to draw thanos frame: failed to make surface current");
            return;
        }

        long dt = Math.min(16, System.currentTimeMillis() - lastDraw);
        lastDraw = System.currentTimeMillis();

        drawFrame(dt / 1000f);

        if (eglHandler != null && eglThread != null && eglThread.isAlive() && !entities.isEmpty()) {
            eglHandler.post(this.frameCallback);
        }
    };

    private float t;

    private void drawFrame(float dT) {
        t += dT;

        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);
        try {
            for (Entity e : entities) {
                if (e == null) continue;
                GLES31.glUseProgram(drawProgram);
                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, e.particlesArray[e.currentBuffer]);
                GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, STRIDE, 0); // Position (vec2)
                GLES31.glEnableVertexAttribArray(0);
                GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, STRIDE, 8); // Velocity (vec2)
                GLES31.glEnableVertexAttribArray(1);
                GLES31.glVertexAttribPointer(2, 1, GLES31.GL_FLOAT, false, STRIDE, 16); // Spawn time (float)
                GLES31.glEnableVertexAttribArray(2);
                GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, STRIDE, 20); // Time Lived (float)
                GLES31.glEnableVertexAttribArray(3);
                GLES31.glVertexAttribPointer(4, 1, GLES31.GL_FLOAT, false, STRIDE, 24); // Duration (float)
                GLES31.glEnableVertexAttribArray(4);
                GLES31.glVertexAttribPointer(5, 4, GLES31.GL_FLOAT, false, STRIDE, 28); // Color (vec4)
                GLES31.glEnableVertexAttribArray(5);
                GLES31.glVertexAttribPointer(6, 1, GLES31.GL_FLOAT, false, STRIDE, 44); // Fraction X (float)
                GLES31.glEnableVertexAttribArray(6);

                GLES31.glBindBufferBase(GLES31.GL_TRANSFORM_FEEDBACK_BUFFER, 0, e.particlesArray[1 - e.currentBuffer]);
                GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, STRIDE, 0); // Position (vec2)
                GLES31.glEnableVertexAttribArray(0);
                GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, STRIDE, 8); // Velocity (vec2)
                GLES31.glEnableVertexAttribArray(1);
                GLES31.glVertexAttribPointer(2, 1, GLES31.GL_FLOAT, false, STRIDE, 16); // Spawn time (float)
                GLES31.glEnableVertexAttribArray(2);
                GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, STRIDE, 20); // Time Lived (float)
                GLES31.glEnableVertexAttribArray(3);
                GLES31.glVertexAttribPointer(4, 1, GLES31.GL_FLOAT, false, STRIDE, 24); // Duration (float)
                GLES31.glEnableVertexAttribArray(4);
                GLES31.glVertexAttribPointer(5, 4, GLES31.GL_FLOAT, false, STRIDE, 28); // Color (vec4)
                GLES31.glEnableVertexAttribArray(5);
                GLES31.glVertexAttribPointer(6, 1, GLES31.GL_FLOAT, false, STRIDE, 44); // Fraction X (float)
                GLES31.glEnableVertexAttribArray(6);

                GLES31.glUniform1f(deltaTimeHandle, dT);
                GLES31.glBeginTransformFeedback(GLES31.GL_POINTS);
                GLES31.glDrawArrays(GLES31.GL_POINTS, 0, e.particlesCount);
                GLES31.glEndTransformFeedback();

                e.currentBuffer = 1 - e.currentBuffer;
            }
        } catch (ConcurrentModificationException ignored) {}

        egl.eglSwapBuffers(eglDisplay, eglSurface);

        checkGlErrors();
    }

    public ThanosLayout(Context context) {
        super(context);

        textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(this);
        textureView.setOpaque(false);
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        addView(overlayView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                try {
                    int particlesTotal = 0;

                    for (Iterator<Entity> iterator = entities.iterator(); iterator.hasNext(); ) {
                        Entity e = iterator.next();
                        int right = e.x + e.snapshot.getWidth(), bottom = e.y + e.snapshot.getHeight();
                        float progress = Math.min(1, INTERPOLATOR.getInterpolation(Math.min(System.currentTimeMillis() - e.spawnTime, 300) / 300f));
                        AndroidUtilities.rectTmp.set(AndroidUtilities.lerp(e.x, right, progress), e.y, right, bottom);
                        canvas.save();
                        canvas.clipRect(AndroidUtilities.rectTmp);
                        canvas.drawBitmap(e.snapshot, e.x, e.y, null);
                        canvas.restore();

                        if (System.currentTimeMillis() - e.spawnTime > e.ttl) {
                            iterator.remove();
                            e.dispose(false);
                        } else {
                            particlesTotal += e.particlesCount;
                        }
                        invalidate();
                    }

                    while (particlesTotal > determineMaxPointsTotal()) {
                        particlesTotal -= entities.remove(0).particlesCount;
                    }
                } catch (ConcurrentModificationException ignored) {}
            }
        });
    }

    private static native byte[] computeNonEmptyPoints(Bitmap bitmap);
    private static native int countNonEmpty(byte[] data);
    private static native boolean generateParticlesData(int x, int y, Bitmap bitmap, byte[] data, ByteBuffer buffer, TimeInterpolator interpolator, float velocityMax);
    private static native void filter(byte[] data, float percent);
    private static native void getVisibleBounds(Bitmap bitmap, Rect outRect);
    private static native ByteBuffer allocateBuffer(int size);
    private static native void releaseBuffer(ByteBuffer buffer);

    private static float determineRadius() {
        if (radius == null) {
            switch (SharedConfig.getDevicePerformanceClass()) {
                case SharedConfig.PERFORMANCE_CLASS_HIGH:
                case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                    radius = AndroidUtilities.dp(1.25f);
                    break;
                case SharedConfig.PERFORMANCE_CLASS_LOW:
                    radius = AndroidUtilities.dp(1.75f);
                    break;
            }
        }
        return radius;
    }

    private static int determineMaxPoints() {
        if (maxPoints == null) {
            switch (SharedConfig.getDevicePerformanceClass()) {
                case SharedConfig.PERFORMANCE_CLASS_HIGH:
                    maxPoints = 7500;
                    break;
                case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                    maxPoints = 5000;
                    break;
                case SharedConfig.PERFORMANCE_CLASS_LOW:
                    maxPoints = 2500;
                    break;
            }
        }
        return maxPoints;
    }

    private static int determineMaxPointsTotal() {
        if (maxPointsTotal == null) {
            switch (SharedConfig.getDevicePerformanceClass()) {
                case SharedConfig.PERFORMANCE_CLASS_HIGH:
                    maxPointsTotal = 30000;
                    break;
                case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                    maxPointsTotal = 15000;
                    break;
                case SharedConfig.PERFORMANCE_CLASS_LOW:
                    maxPointsTotal = 5000;
                    break;
            }
        }
        return maxPointsTotal;
    }

    public int getOffsetY() {
        return 0;
    }

    public Entity disappear(View view, Runnable releaseCallback) {
        if (view.getWidth() == 0 || view.getHeight() == 0) {
            AndroidUtilities.runOnUIThread(releaseCallback);
            return null;
        }

        view.setVisibility(VISIBLE);
        int x = 0, y = 0;
        View v = view;
        do {
            v.getLocationInWindow(pos);
            x += pos[0];
            y += pos[1];
            v = (View) v.getParent();
        } while (v.getParent() != getParent());

        getLocationInWindow(pos);
        int dX = pos[0], dY = pos[1];

        Entity entity = new Entity();
        if (view instanceof ChatMessageCell) {
            ChatMessageCell cell = (ChatMessageCell) view;
            cell.setAnimationRunning(true, true);
            cell.setForceHideCheckBox(true);
            Bitmap bm = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            c.save();
            c.scale(cell.getScaleX(), cell.getScaleY(), cell.getWidth() / 2f, cell.getHeight() / 2f);
            cell.drawBackgroundInternal(c, true);

            c.translate(-cell.getX(), -cell.getY());
            ((ChatActivity.RecyclerListViewInternal) cell.getParent()).drawChildInternal(c, cell, SystemClock.uptimeMillis(), true);

            c.restore();
            entity.snapshot = bm;

            cell.setForceHideCheckBox(false);
        } else {
            entity.snapshot = AndroidUtilities.snapshotView(view);
        }

        Rect rect = new Rect();
        getVisibleBounds(entity.snapshot, rect);
        if (rect.width() <= 0 || rect.height() <= 0) {
            AndroidUtilities.runOnUIThread(releaseCallback);
            return null;
        }
        if (rect.left != 0 || rect.top != 0 || rect.width() != entity.snapshot.getWidth() || rect.height() != entity.snapshot.getHeight()) {
            Bitmap bm = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            c.save();
            c.translate(-rect.left, -rect.top);
            c.drawBitmap(entity.snapshot, 0, 0, null);
            c.restore();

            entity.snapshot.recycle();
            entity.snapshot = bm;

            x += rect.left;
            y += rect.top;
        }

        entity.x = x - dX;
        entity.y = y - dY + getOffsetY();
        entity.callback = releaseCallback;

        eglHandler.post(()->{
            if (entity.particlesArray != null) {
                GLES31.glDeleteBuffers(2, entity.particlesArray, 0);
            }

            Utilities.stageQueue.postRunnable(()->{
                byte[] points = computeNonEmptyPoints(entity.snapshot);
                int countBefore = countNonEmpty(points);
                int maxPoints = determineMaxPoints();
                if (countBefore * VISIBLE_PERCENT > maxPoints) {
                    filter(points, maxPoints / (float) countBefore);
                } else {
                    filter(points, VISIBLE_PERCENT);
                }
                int particlesCount = countNonEmpty(points);
                entity.particlesCount = particlesCount;

                if (points == null || entity.particlesCount == 0) {
                    entity.dispose(false);
                    return;
                }

                ByteBuffer buf = allocateBuffer(particlesCount * STRIDE).order(ByteOrder.nativeOrder());
                if (generateParticlesData(entity.x, (int) (textureView.getHeight() - entity.y - determineRadius() / 2), entity.snapshot, points, buf, INTERPOLATOR, AndroidUtilities.dp(66))) {
                    eglHandler.post(()->{
                        entity.particlesArray = new int[2];
                        GLES31.glGenBuffers(2, entity.particlesArray, 0);
                        for (int i = 0; i < 2; i++) {
                            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, entity.particlesArray[i]);
                            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, particlesCount * STRIDE, buf, GLES31.GL_DYNAMIC_DRAW);
                        }
                        entity.buffer = buf;

                        entity.spawnTime = System.currentTimeMillis();
                        entities.add(entity);
                        eglHandler.removeCallbacks(frameCallback);
                        eglHandler.post(frameCallback);
                        checkGlErrors();

                        post(() -> {
                            overlayView.invalidate();

                            view.setAlpha(0f);
                            if (view instanceof ChatMessageCell) {
                                ChatMessageCell cell = (ChatMessageCell) view;
                                cell.setCheckBoxSuppressed(false);
                                cell.setCheckBoxVisible(false, true);
                                cell.setChecked(false, false, true);
                            }
                        });
                    });
                } else {
                    FileLog.e("Failed to generate thanos particles data");
                }
            });
        });
        return entity;
    }

    private void checkGlErrors() {
        int err;
        while ((err = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
            FileLog.e("Thanos GLES error: " + err);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        eglThread = new HandlerThread("thanos");
        eglThread.start();
        eglHandler = new Handler(eglThread.getLooper());

        eglHandler.post(()->{
            EGL mEgl = EGLContext.getEGL();
            if (!(mEgl instanceof EGL10)) {
                FileLog.w("Can't initialize thanos layout: EGL not supported (" + mEgl + ")");
                return;
            }
            egl = (EGL10) EGLContext.getEGL();
            eglDisplay = egl.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                FileLog.w("Can't initialize thanos layout: no EGL display");
                return;
            }
            int[] version = new int[2];
            if (!egl.eglInitialize(eglDisplay, version)) {
                FileLog.w("Can't initialize thanos layout: display failed to initialize");
                return;
            }

            int[] configAttributes = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                    EGL14.EGL_NONE
            };
            EGLConfig[] eglConfigs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(eglDisplay, configAttributes, eglConfigs, 1, numConfigs)) {
                FileLog.w("Can't initialize thanos layout: failed to choose config");
                return;
            }
            eglConfig = eglConfigs[0];
            int[] contextAttributes = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            eglContext = egl.eglCreateContext(eglDisplay, eglConfig, egl.EGL_NO_CONTEXT, contextAttributes);
            if (eglContext == null) {
                FileLog.w("Can't initialize thanos layout: failed to create context");
                return;
            }
            eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);
            if (eglSurface == null) {
                FileLog.w("Can't initialize thanos layout: failed to create window surface");
                return;
            }

            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                FileLog.w("Can't initialize thanos layout: failed to make surface current");
                return;
            }

            int vertexShader = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER);
            int fragmentShader = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
            if (vertexShader == 0 || fragmentShader == 0) {
                FileLog.w("Can't initialize thanos layout: failed to create shaders");
                return;
            }

            GLES31.glShaderSource(vertexShader, RLottieDrawable.readRes(null, R.raw.thanos_vertex) + "\n// " + Math.random());
            GLES31.glCompileShader(vertexShader);
            int[] status = new int[1];
            GLES31.glGetShaderiv(vertexShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.w("Can't initialize thanos layout: failed to compile vertex shader: " + GLES31.glGetShaderInfoLog(vertexShader));
                GLES31.glDeleteShader(vertexShader);
                return;
            }
            GLES31.glShaderSource(fragmentShader, RLottieDrawable.readRes(null, R.raw.thanos_fragment) + "\n// " + Math.random());
            GLES31.glCompileShader(fragmentShader);
            GLES31.glGetShaderiv(fragmentShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.w("Can't initialize thanos layout: failed to compile fragment shader: " + GLES31.glGetShaderInfoLog(fragmentShader));
                GLES31.glDeleteShader(fragmentShader);
                return;
            }

            drawProgram = GLES31.glCreateProgram();
            if (drawProgram == 0) {
                FileLog.w("Can't initialize thanos layout: failed to create program");
                return;
            }
            GLES31.glAttachShader(drawProgram, vertexShader);
            GLES31.glAttachShader(drawProgram, fragmentShader);
            String[] feedbackVaryings = {"outPosition", "outVelocity", "outSpawnTime", "outPhase", "outLifetime", "outColor", "outFractionX"};
            GLES31.glTransformFeedbackVaryings(drawProgram, feedbackVaryings, GLES31.GL_INTERLEAVED_ATTRIBS);

            GLES31.glLinkProgram(drawProgram);
            GLES31.glGetProgramiv(drawProgram, GLES31.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.w("Can't initialize thanos layout: failed to link program: " + GLES31.glGetProgramInfoLog(drawProgram));
                return;
            }

            deltaTimeHandle = GLES31.glGetUniformLocation(drawProgram, "deltaTime");
            sizeHandle = GLES31.glGetUniformLocation(drawProgram, "size");
            radiusHandle = GLES31.glGetUniformLocation(drawProgram, "r");

            GLES31.glViewport(0, 0, width, height);
            GLES31.glEnable(GLES31.GL_BLEND);
            GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            GLES31.glUseProgram(drawProgram);
            GLES31.glUniform2f(sizeHandle, width, height);
            GLES31.glUniform1f(radiusHandle, determineRadius());

            GLES31.glUniform1i(GLES31.glGetUniformLocation(drawProgram, "increaseVelocity"), SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW ? 1 : 0);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "maxVelocity"), AndroidUtilities.dp(130));

            changeShaderSize(width, height);
        });
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        changeShaderSize(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            for (Entity entity : entities) {
                if (entity == null) continue;
                entity.dispose(true);
            }
            entities.clear();

            try {
                if (drawProgram != 0) {
                    GLES31.glDeleteProgram(drawProgram);
                    drawProgram = 0;
                }

                if (egl != null) {
                    egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                    egl.eglDestroySurface(eglDisplay, eglSurface);
                    egl.eglDestroyContext(eglDisplay, eglContext);
                }
                surface.release();
            } catch (Exception e) {
                FileLog.e(e);
            }

            checkGlErrors();
        }

        eglHandler = null;
        boolean quit = eglThread.quitSafely();
        eglThread = null;
        return quit;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}

    private void changeShaderSize(int width, int height) {
        eglHandler.post(()->{
            GLES31.glViewport(0, 0, width, height);
            GLES31.glEnable(GLES31.GL_BLEND);
            GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            GLES31.glUniform2f(sizeHandle, width, height);

            egl.eglSwapBuffers(eglDisplay, eglSurface);
        });
    }

    public class Entity {
        Bitmap snapshot;
        long spawnTime;
        long ttl = 1600;
        int x, y;
        int[] particlesArray;
        int particlesCount;
        ByteBuffer buffer;
        int currentBuffer;
        Runnable callback;
        boolean disposed;

        void dispose(boolean immediate) {
            disposed = true;
            snapshot.recycle();

            if (immediate) {
                if (particlesArray != null) {
                    GLES31.glDeleteBuffers(2, particlesArray, 0);
                }
                if (buffer != null) {
                    releaseBuffer(buffer);
                }
                AndroidUtilities.runOnUIThread(() -> callback.run());
            } else {
                eglHandler.post(() -> {
                    if (particlesArray != null) {
                        GLES31.glDeleteBuffers(2, particlesArray, 0);
                    }
                    if (buffer != null) {
                        releaseBuffer(buffer);
                    }
                    AndroidUtilities.runOnUIThread(() -> callback.run());
                });
            }
        }

        public void cancel() {
            if (disposed) return;

            dispose(false);
            entities.remove(this);
            overlayView.invalidate();
        }
    }
}
