#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

namespace {

const int BLOCK = 8;
const int MIN_DYNAMIC_RANGE = 24;
const int MIN_DIMENSION = BLOCK * 5;

inline int imin(int a, int b) { return a < b ? a : b; }
inline int imax(int a, int b) { return a > b ? a : b; }

void globalThreshold(const uint8_t* luma, int width, int height, uint8_t* bits) {
    const int total = width * height;
    int histogram[256];
    memset(histogram, 0, sizeof(histogram));
    for (int i = 0; i < total; i++) {
        histogram[luma[i]]++;
    }
    long long sumAll = 0;
    for (int i = 0; i < 256; i++) {
        sumAll += (long long) i * histogram[i];
    }
    long long sumBack = 0;
    int weightBack = 0;
    double best = -1;
    int threshold = 127;
    for (int t = 0; t < 256; t++) {
        weightBack += histogram[t];
        if (weightBack == 0) {
            continue;
        }
        int weightFore = total - weightBack;
        if (weightFore == 0) {
            break;
        }
        sumBack += (long long) t * histogram[t];
        double meanBack = (double) sumBack / weightBack;
        double meanFore = (double) (sumAll - sumBack) / weightFore;
        double diff = meanBack - meanFore;
        double between = (double) weightBack * weightFore * diff * diff;
        if (between > best) {
            best = between;
            threshold = t;
        }
    }
    for (int i = 0; i < total; i++) {
        bits[i] = luma[i] <= threshold ? 1 : 0;
    }
}

bool localThreshold(const uint8_t* luma, int width, int height, uint8_t* bits) {
    const int subWidth = (width + BLOCK - 1) / BLOCK;
    const int subHeight = (height + BLOCK - 1) / BLOCK;
    int* black = (int*) malloc(sizeof(int) * (size_t) subWidth * (size_t) subHeight);
    if (black == NULL) {
        return false;
    }
    const int maxX = width - BLOCK;
    const int maxY = height - BLOCK;
    for (int y = 0; y < subHeight; y++) {
        const int yOffset = imin(y * BLOCK, maxY);
        for (int x = 0; x < subWidth; x++) {
            const int xOffset = imin(x * BLOCK, maxX);
            int sum = 0;
            int mn = 0xFF;
            int mx = 0;
            const uint8_t* row = luma + (size_t) yOffset * width + xOffset;
            for (int yy = 0; yy < BLOCK; yy++, row += width) {
                for (int xx = 0; xx < BLOCK; xx++) {
                    int pixel = row[xx];
                    sum += pixel;
                    if (pixel < mn) mn = pixel;
                    if (pixel > mx) mx = pixel;
                }
            }
            int average;
            if (mx - mn > MIN_DYNAMIC_RANGE) {
                average = sum >> 6;
            } else {
                average = mn / 2;
                if (y > 0 && x > 0) {
                    int neighbor = (black[(y - 1) * subWidth + x] + 2 * black[y * subWidth + x - 1]
                            + black[(y - 1) * subWidth + x - 1]) >> 2;
                    if (mn < neighbor) {
                        average = neighbor;
                    }
                }
            }
            black[y * subWidth + x] = average;
        }
    }
    memset(bits, 0, (size_t) width * (size_t) height);
    for (int y = 0; y < subHeight; y++) {
        const int yOffset = imin(y * BLOCK, maxY);
        const int top = imax(2, imin(y, subHeight - 3));
        for (int x = 0; x < subWidth; x++) {
            const int xOffset = imin(x * BLOCK, maxX);
            const int left = imax(2, imin(x, subWidth - 3));
            int sum = 0;
            for (int dy = -2; dy <= 2; dy++) {
                const int* r = black + (top + dy) * subWidth + left;
                for (int dx = -2; dx <= 2; dx++) {
                    sum += r[dx];
                }
            }
            const int threshold = sum / 25;
            size_t offset = (size_t) yOffset * width + xOffset;
            for (int yy = 0; yy < BLOCK; yy++, offset += width) {
                for (int xx = 0; xx < BLOCK; xx++) {
                    if (luma[offset + xx] <= threshold) {
                        bits[offset + xx] = 1;
                    }
                }
            }
        }
    }
    free(black);
    return true;
}

bool validDims(JNIEnv* env, jbyteArray luma, jint width, jint height, jsize* total) {
    if (luma == NULL || width <= 0 || height <= 0) {
        return false;
    }
    if ((long long) width * (long long) height > 0x3FFFFFFFLL) {
        return false;
    }
    jsize t = (jsize) (width * height);
    if (env->GetArrayLength(luma) < t) {
        return false;
    }
    *total = t;
    return true;
}

}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_coara_oldbrowser_qr_QrNative_nativeBinarize(JNIEnv* env, jclass, jbyteArray luma, jint width,
                                                      jint height, jbooleanArray out) {
    jsize total;
    if (out == NULL || !validDims(env, luma, width, height, &total) || env->GetArrayLength(out) < total) {
        return JNI_FALSE;
    }
    jbyte* src = (jbyte*) env->GetPrimitiveArrayCritical(luma, NULL);
    if (src == NULL) {
        return JNI_FALSE;
    }
    jboolean* dst = (jboolean*) env->GetPrimitiveArrayCritical(out, NULL);
    if (dst == NULL) {
        env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
        return JNI_FALSE;
    }
    bool ok = true;
    if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
        globalThreshold((const uint8_t*) src, width, height, (uint8_t*) dst);
    } else {
        ok = localThreshold((const uint8_t*) src, width, height, (uint8_t*) dst);
    }
    env->ReleasePrimitiveArrayCritical(out, dst, ok ? 0 : JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_coara_oldbrowser_qr_QrNative_nativeGlobal(JNIEnv* env, jclass, jbyteArray luma, jint width,
                                                    jint height, jbooleanArray out) {
    jsize total;
    if (out == NULL || !validDims(env, luma, width, height, &total) || env->GetArrayLength(out) < total) {
        return JNI_FALSE;
    }
    jbyte* src = (jbyte*) env->GetPrimitiveArrayCritical(luma, NULL);
    if (src == NULL) {
        return JNI_FALSE;
    }
    jboolean* dst = (jboolean*) env->GetPrimitiveArrayCritical(out, NULL);
    if (dst == NULL) {
        env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
        return JNI_FALSE;
    }
    globalThreshold((const uint8_t*) src, width, height, (uint8_t*) dst);
    env->ReleasePrimitiveArrayCritical(out, dst, 0);
    env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_coara_oldbrowser_qr_QrNative_nativeSmooth(JNIEnv* env, jclass, jbyteArray luma, jint width,
                                                    jint height, jbyteArray out) {
    jsize total;
    if (out == NULL || !validDims(env, luma, width, height, &total) || env->GetArrayLength(out) < total) {
        return JNI_FALSE;
    }
    uint8_t* tmp = (uint8_t*) malloc((size_t) total);
    if (tmp == NULL) {
        return JNI_FALSE;
    }
    jbyte* src = (jbyte*) env->GetPrimitiveArrayCritical(luma, NULL);
    if (src == NULL) {
        free(tmp);
        return JNI_FALSE;
    }
    jbyte* dstRaw = (jbyte*) env->GetPrimitiveArrayCritical(out, NULL);
    if (dstRaw == NULL) {
        env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
        free(tmp);
        return JNI_FALSE;
    }
    const uint8_t* in = (const uint8_t*) src;
    uint8_t* dst = (uint8_t*) dstRaw;
    for (int y = 0; y < height; y++) {
        const uint8_t* row = in + (size_t) y * width;
        uint8_t* trow = tmp + (size_t) y * width;
        for (int x = 0; x < width; x++) {
            int l = x > 0 ? x - 1 : x;
            int r = x < width - 1 ? x + 1 : x;
            trow[x] = (uint8_t) ((row[l] + 2 * row[x] + row[r] + 2) >> 2);
        }
    }
    for (int y = 0; y < height; y++) {
        const uint8_t* up = tmp + (size_t) (y > 0 ? y - 1 : y) * width;
        const uint8_t* mid = tmp + (size_t) y * width;
        const uint8_t* down = tmp + (size_t) (y < height - 1 ? y + 1 : y) * width;
        uint8_t* orow = dst + (size_t) y * width;
        for (int x = 0; x < width; x++) {
            orow[x] = (uint8_t) ((up[x] + 2 * mid[x] + down[x] + 2) >> 2);
        }
    }
    env->ReleasePrimitiveArrayCritical(out, dstRaw, 0);
    env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
    free(tmp);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_coara_oldbrowser_qr_QrNative_nativeInvert(JNIEnv* env, jclass, jbyteArray luma, jint width,
                                                    jint height, jbyteArray out) {
    jsize total;
    if (out == NULL || !validDims(env, luma, width, height, &total) || env->GetArrayLength(out) < total) {
        return JNI_FALSE;
    }
    jbyte* src = (jbyte*) env->GetPrimitiveArrayCritical(luma, NULL);
    if (src == NULL) {
        return JNI_FALSE;
    }
    jbyte* dst = (jbyte*) env->GetPrimitiveArrayCritical(out, NULL);
    if (dst == NULL) {
        env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
        return JNI_FALSE;
    }
    const uint8_t* in = (const uint8_t*) src;
    uint8_t* o = (uint8_t*) dst;
    for (jsize i = 0; i < total; i++) {
        o[i] = (uint8_t) (255 - in[i]);
    }
    env->ReleasePrimitiveArrayCritical(out, dst, 0);
    env->ReleasePrimitiveArrayCritical(luma, src, JNI_ABORT);
    return JNI_TRUE;
}

}
