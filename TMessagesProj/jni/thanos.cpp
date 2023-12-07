//
// Created by YTKAB0BP on 29.11.2023.
//

#include <jni.h>
#include <android/bitmap.h>
#include <cstdlib>
#include <android/log.h>
#include "c_utils.h"

extern "C" {

static jmethodID timeInterpolatorMethod = nullptr;
static jmethodID rectSetMethod = nullptr;

JNIEXPORT jbyteArray Java_org_telegram_ui_Components_ThanosLayout_computeNonEmptyPoints(JNIEnv* env, jclass, jobject bitmap) {
    void *pixels;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) >= 0 && AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
        int size = info.width * info.height;
        uint32_t* colors = (uint32_t *) pixels;
        jbyteArray arr = env->NewByteArray(size);
        jbyte* bytes = env->GetByteArrayElements(arr, JNI_FALSE);

        for (int i = 0; i < size; i++) {
            bytes[i] = (colors[i] >> 24U) != 0 ? 1 : 0;
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        env->ReleaseByteArrayElements(arr, bytes, JNI_COMMIT);
        return arr;
    }
    return nullptr;
}

JNIEXPORT jint Java_org_telegram_ui_Components_ThanosLayout_countNonEmpty(JNIEnv* env, jclass, jbyteArray arr) {
    int count = 0;
    jbyte* bytes = env->GetByteArrayElements(arr, JNI_FALSE);
    int len = env->GetArrayLength(arr);
    for (int i = 0; i < len; i++) {
        if (bytes[i]) {
            count++;
        }
    }

    env->ReleaseByteArrayElements(arr, bytes, JNI_ABORT);
    return count;
}

float randf() {
    return (float) rand() / (float) RAND_MAX;
}

float rand2f(float from, float to) {
    return from + randf() * (to - from);
}

int randi(int from, int to) {
    return from + randf() * (to - from);
}

JNIEXPORT void Java_org_telegram_ui_Components_ThanosLayout_filter(JNIEnv* env, jclass, jbyteArray arr, jfloat percent) {
    int len = env->GetArrayLength(arr);
    jbyte* bytes = env->GetByteArrayElements(arr, JNI_FALSE);

    for (int i = 0; i < len; i++) {
        if (bytes[i]) {
            if (randf() > percent) {
                bytes[i] = 0;
                continue;
            }
        }
    }
    env->ReleaseByteArrayElements(arr, bytes, JNI_ABORT);
}

JNIEXPORT void Java_org_telegram_ui_Components_ThanosLayout_getVisibleBounds(JNIEnv* env, jclass, jobject bitmap, jobject rect) {
    if (rectSetMethod == nullptr) {
        jclass rectClass = env->FindClass("android/graphics/Rect");
        rectSetMethod = env->GetMethodID(rectClass, "set", "(IIII)V");
    }

    void *pixels;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) >= 0 && AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
        int size = info.width * info.height;
        uint32_t* colors = (uint32_t *) pixels;

        int minX = info.width, minY = info.height;
        int maxX = 0, maxY = 0;

        for (int i = 0; i < size; i++) {
            if ((colors[i] >> 24U) != 0) {
                int x = i % info.width;
                int y = i / info.width;

                if (x < minX) {
                    minX = x;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);

        env->CallVoidMethod(rect, rectSetMethod, minX, minY, maxX, maxY);
    }
}

JNIEXPORT jboolean Java_org_telegram_ui_Components_ThanosLayout_generateParticlesData(JNIEnv* env, jclass, jint pX, jint pY, jobject bitmap, jbyteArray arr, jobject byteBuf, jobject interpolator, jfloat velocityMax) {
    if (timeInterpolatorMethod == nullptr) {
        jclass timeInterpolatorClass = env->FindClass("android/animation/TimeInterpolator");
        timeInterpolatorMethod = env->GetMethodID(timeInterpolatorClass, "getInterpolation", "(F)F");
    }

    int len = env->GetArrayLength(arr);
    jbyte* bytes = env->GetByteArrayElements(arr, JNI_FALSE);

    void* dataBuf = env->GetDirectBufferAddress(byteBuf);
    if (dataBuf == nullptr) {
        return JNI_FALSE;
    }
    float* floatBuf = (float*) dataBuf;

    void *pixels;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) >= 0 && AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
        uint32_t* colors = (uint32_t *) pixels;

        int j = 0;
        for (int i = 0; i < len; i++) {
            if (bytes[i]) {
                int x = i % info.width;
                int y = i / info.width;

                float fraction = x / (float) info.width; // fraction X

                floatBuf[j + 0] = pX + x; // posX
                floatBuf[j + 1] = pY - y; // posY

                float direction = randf() * M_PI * 2.0;
                float velocity = (rand2f(0.1, 0.2) + (1.0 - env->CallFloatMethod(interpolator, timeInterpolatorMethod, (jfloat) fraction)) * 0.3) * velocityMax;
                floatBuf[j + 2] = cos(direction) * velocity; // velX
                floatBuf[j + 3] = sin(direction) * velocity; // velY

                floatBuf[j + 4] = env->CallFloatMethod(interpolator, timeInterpolatorMethod, (jfloat) fraction) * 0.3; // spawnTime
                floatBuf[j + 5] = 0; // phase
                floatBuf[j + 6] = rand2f(0.8, 0.9); // ttl

                int color = colors[i];
                floatBuf[j + 7] = (color & 0xFF) / 255.0; // blue
                floatBuf[j + 8] = ((color >> 8) & 0xFF) / 255.0; // green
                floatBuf[j + 9] = ((color >> 16) & 0xFF) / 255.0; // red
                floatBuf[j + 10] = ((color >> 24U) & 0xFF) / 255.0; // alpha

                floatBuf[j + 11] = fraction;
                j += 12;
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        env->ReleaseByteArrayElements(arr, bytes, JNI_ABORT);
        return JNI_TRUE;
    }
    env->ReleaseByteArrayElements(arr, bytes, JNI_ABORT);
    return JNI_FALSE;
}

JNIEXPORT jobject JNICALL Java_org_telegram_ui_Components_ThanosLayout_allocateBuffer(JNIEnv* env, jclass, jint size) {
    void* buffer = malloc(size);
    jobject directBuffer = env->NewDirectByteBuffer(buffer, size);
    return directBuffer;
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_ThanosLayout_releaseBuffer(JNIEnv* env, jclass, jobject bufferRef) {
    void *buffer = env->GetDirectBufferAddress(bufferRef);
    free(buffer);
}

}