/***************************************************************************
 *   book reader based on crengine-ng                                      *
 *   Copyright (C) 2024 by Aleksey Chernov <valexlin@gmail.com>            *
 *                                                                         *
 *   This program is free software: you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation, either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.*
 ***************************************************************************/

/***************************************************************************
 *   Based on CoolReader project code at                                   *
 *   https://github.com/buggins/coolreader                                 *
 *   Copyright (C) 2010-2021 by Vadim Lopatin <coolreader.org@gmail.com>   *
 ***************************************************************************/

#include "jnigraphicslib.h"
#include "lvcolordrawbufex.h"

#include <crlog.h>

#include <dlfcn.h>

static BitmapAccessorInterface *s_bitmapAccessorInstance = NULL;

/// BitmapAccessorInterface that use libjnigraphics.so system library.
class JNIGraphicsLib : public BitmapAccessorInterface {
private:
    void *_lib;

    int (*AndroidBitmap_getInfo)(JNIEnv *env, jobject jbitmap, AndroidBitmapInfo *info);

    int (*AndroidBitmap_lockPixels)(JNIEnv *env, jobject jbitmap, void **addrPtr);

    int (*AndroidBitmap_unlockPixels)(JNIEnv *env, jobject jbitmap);

    void *getProc(const char *procName) {
        return dlsym(_lib, procName);
    }

public:
    virtual LVDrawBuf *lock(JNIEnv *env, jobject jbitmap) {
        //CRLog::trace("JNIGraphicsLib::lock entered");
        AndroidBitmapInfo info;
        if (ANDROID_BITMAP_RESUT_SUCCESS != AndroidBitmap_getInfo(env, jbitmap, &info)) {
            CRLog::error("BitmapAccessor : cannot get bitmap info");
            return NULL;
        }
        int width = (int) info.width;
        int height = (int) info.height;
        int stride = (int) info.stride;
        int format = (int) info.format;
        if (format != ANDROID_BITMAP_FORMAT_RGBA_8888 && format != ANDROID_BITMAP_FORMAT_RGB_565 &&
            format != ANDROID_BITMAP_FORMAT_A_8) {
            CRLog::error("BitmapAccessor : bitmap format %d is not yet supported", format);
            return NULL;
        }
        int bpp = (format == ANDROID_BITMAP_FORMAT_RGBA_8888) ? 32 : 16;
        //CRLog::trace("JNIGraphicsLib::lock info: %d (%d) x %d", width, stride, height);
        lUInt8 *pixels = NULL;
        if (ANDROID_BITMAP_RESUT_SUCCESS !=
            AndroidBitmap_lockPixels(env, jbitmap, (void **) &pixels)) {
            CRLog::error("AndroidBitmap_lockPixels failed");
            pixels = NULL;
        }
        //CRLog::trace("JNIGraphicsLib::lock pixels locked!" );
        return new LVColorDrawBufEx(width, height, pixels, bpp);
    }

    virtual void unlock(JNIEnv *env, jobject jbitmap, LVDrawBuf *buf) {
        auto *bmp = (LVColorDrawBufEx *) buf;
        bmp->convert();
        AndroidBitmap_unlockPixels(env, jbitmap);
        delete buf;
    }

    bool load(const char *libName) {
        if (!_lib)
            _lib = dlopen(libName, RTLD_NOW | RTLD_LOCAL);
        if (_lib) {
            CRLog::info("Will use libjnigraphics for bitmap access");
            AndroidBitmap_getInfo = (int (*)(JNIEnv *env, jobject jbitmap, AndroidBitmapInfo *info))
                    getProc("AndroidBitmap_getInfo");
            AndroidBitmap_lockPixels = (int (*)(JNIEnv *env, jobject jbitmap, void **addrPtr))
                    getProc("AndroidBitmap_lockPixels");
            AndroidBitmap_unlockPixels = (int (*)(JNIEnv *env, jobject jbitmap))
                    getProc("AndroidBitmap_unlockPixels");
            if (!AndroidBitmap_getInfo || !AndroidBitmap_lockPixels || !AndroidBitmap_unlockPixels)
                unload(); // not all functions found in library, fail
        }
        return _lib != NULL;
    }

    bool unload() {
        bool res = false;
        if (_lib) {
            dlclose(_lib);
            _lib = NULL;
            res = true;
        }
        return res;
    }

    JNIGraphicsLib()
            : _lib(NULL) {
    }

    virtual ~JNIGraphicsLib() {
        unload();
    }
};

/// BitmapAccessorInterface where libjnigraphics.so is unavailable (old Android?) that used reflections.
class JNIGraphicsReplacement : public BitmapAccessorInterface {
    jintArray _array;
public:
    virtual int getInfo(JNIEnv *env, jobject jbitmap, AndroidBitmapInfo *info) {
        //CRLog::trace("JNIGraphicsReplacement::getInfo entered");
        jclass cls = env->GetObjectClass(jbitmap);
        jmethodID mid;
        mid = env->GetMethodID(cls, "getHeight", "()I");
        info->height = env->CallIntMethod(jbitmap, mid);
        //CRLog::debug("Bitmap height: %d", info->height);
        mid = env->GetMethodID(cls, "getWidth", "()I");
        info->width = env->CallIntMethod(jbitmap, mid);
        //CRLog::debug("Bitmap width: %d", info->width);
        mid = env->GetMethodID(cls, "getRowBytes", "()I");
        info->stride = env->CallIntMethod(jbitmap, mid);
        //CRLog::debug("Bitmap stride: %d", info->stride);
        mid = env->GetMethodID(cls, "getConfig", "()Landroid/graphics/Bitmap$Config;");
        jobject configObj = env->CallObjectMethod(jbitmap, mid);
        jclass configCls = env->GetObjectClass(configObj);
        mid = env->GetMethodID(configCls, "ordinal", "()I");
        int ord = env->CallIntMethod(configObj, mid);
        switch (ord) {
            case 1:
                info->format = ANDROID_BITMAP_FORMAT_A_8;
                break;
            case 2:
                info->format = ANDROID_BITMAP_FORMAT_RGBA_4444;
                break;
            case 3:
                info->format = ANDROID_BITMAP_FORMAT_RGBA_8888;
                break;
            case 4:
            case 8:
                info->format = ANDROID_BITMAP_FORMAT_RGB_565;
                break;
            default:
                info->format = ANDROID_BITMAP_FORMAT_NONE;
                break;
        }
        jfieldID fid;
        fid = env->GetFieldID(configCls, "nativeInt", "I");
        //info->format
        int ni = env->GetIntField(configObj, fid);
        //CRLog::debug("Bitmap format: %d (ord=%d, nativeInt=%d)", info->format, ord, ni);
        return ANDROID_BITMAP_RESUT_SUCCESS;
    }

    virtual LVDrawBuf *lock(JNIEnv *env, jobject jbitmap) {
        //CRLog::trace("JNIGraphicsReplacement::lock entered");
        AndroidBitmapInfo info;
        if (ANDROID_BITMAP_RESUT_SUCCESS != getInfo(env, jbitmap, &info))
            return NULL;
        int width = (int) info.width;
        int height = (int) info.height;
        int stride = (int) info.stride;
        int format = (int) info.format;
        //CRLog::trace("JNIGraphicsReplacement::lock info: %d (%d) x %d", width, stride, height);
        if (format != ANDROID_BITMAP_FORMAT_RGBA_8888 && format != ANDROID_BITMAP_FORMAT_RGB_565 &&
            format != ANDROID_BITMAP_FORMAT_A_8) {
            CRLog::error("BitmapAccessor : bitmap format %d is not yet supported", format);
            return NULL;
        }
        int bpp = (format == ANDROID_BITMAP_FORMAT_RGBA_8888) ? 32 : 16;
        //int size = stride * height;
        //CRLog::trace("lock: %d x %d stride = %d, width*4 = %d", width, height, stride, width*4 );
        int size = width * height;
        if (bpp == 16)
            size = (size + 1) >> 1;
        reallocArray(env, size);
        //CRLog::trace("JNIGraphicsReplacement::lock getting pixels");
        auto *pixels = (lUInt8 *) env->GetIntArrayElements(_array, 0);
        //CRLog::trace("Pixels address %08x", (int)(pixels));
        //CRLog::trace("JNIGraphicsReplacement::lock exiting");
        LVDrawBuf *buf = new LVColorDrawBufEx(width, height, pixels, bpp);
        //CRLog::trace("Last row address %08x", (int)buf->GetScanLine(height-1));
        //pixels[0] = 0x12;
        //pixels[width*height*4-1] = 0x34;
        //CRLog::trace("Write access ok");
        return buf;
    }

    void reallocArray(JNIEnv *env, int len) {
        if (_array == NULL || env->GetArrayLength(_array) < len) {
            //CRLog::trace("JNIGraphicsReplacement::reallocArray( %d )", len);
            freeArray(env);
            jobject lref = env->NewIntArray(len);
            _array = (jintArray) env->NewGlobalRef(lref);
            env->DeleteLocalRef(lref);
        }
    }

    void freeArray(JNIEnv *env) {
        if (_array != NULL) {
            env->DeleteGlobalRef(_array);
            _array = NULL;
        }
    }

    virtual void unlock(JNIEnv *env, jobject jbitmap, LVDrawBuf *buf) {
        //CRLog::trace("JNIGraphicsReplacement::unlock entering");
        if (!buf)
            return;
        auto *bmp = (LVColorDrawBufEx *) buf;
        bmp->convert();
        lUInt8 *pixels = bmp->getData();
        env->ReleaseIntArrayElements(_array, (jint *) pixels, 0);
        // IntBuffer testBuf = IntBuffer.wrap(pixels);
        jclass cls = env->FindClass("java/nio/IntBuffer");
        jmethodID mid = env->GetStaticMethodID(cls, "wrap", "([I)Ljava/nio/IntBuffer;");
        jobject jbuf = env->CallStaticObjectMethod(cls, mid, _array);
        // mBitmap.copyPixelsFromBuffer(testBuf);
        cls = env->GetObjectClass(jbitmap);
        mid = env->GetMethodID(cls, "copyPixelsFromBuffer", "(Ljava/nio/Buffer;)V");
        env->CallVoidMethod(jbitmap, mid, jbuf);
        env->DeleteLocalRef(jbuf);
        //CRLog::trace("JNIGraphicsReplacement::unlock exiting");
        delete buf;
    }

    JNIGraphicsReplacement() : _array(NULL) {
    }

    virtual ~JNIGraphicsReplacement() {
    }
};

BitmapAccessorInterface *BitmapAccessorInterface::getInstance() {
    if (s_bitmapAccessorInstance == NULL) {
        auto *lib = new JNIGraphicsLib();
        if (!lib->load("libjnigraphics.so")) {
            delete lib;
            CRLog::error("Cannot load libjnigraphics.so : will use slower replacement instead");
            s_bitmapAccessorInstance = new JNIGraphicsReplacement();
        } else {
            s_bitmapAccessorInstance = lib;
        }
    }
    return s_bitmapAccessorInstance;
}
