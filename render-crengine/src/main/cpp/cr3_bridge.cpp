/***************************************************************************
 *   Komga E-Ink Reader — render-crengine JNI bridge (Phase 1c spike).      *
 *                                                                          *
 *   Drives crengine-ng's LVDocView to reflow an EPUB and rasterise one     *
 *   page into an Android Bitmap. Bitmap locking + BGRX→RGBA conversion is  *
 *   lifted from the LxReader project (jnigraphicslib.cpp /                  *
 *   lvcolordrawbufex.cpp, GPL-3.0-or-later).                                *
 *                                                                          *
 *   This program is free software: you can redistribute it and/or modify   *
 *   it under the terms of the GNU Affero General Public License as          *
 *   published by the Free Software Foundation, either version 3 of the      *
 *   License, or (at your option) any later version.                        *
 ***************************************************************************/

#include <jni.h>
#include <android/log.h>

#include <lvdocview.h>
#include <lvstreamutils.h>
#include <lvfntman.h>
#include <crhyphman.h>
#include <crlog.h>

#include "jnigraphicslib.h"

#define LOG_TAG "cr3bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/*
 * Initialise the crengine-ng font manager and register one TTF font. With
 * USE_FONTCONFIG=OFF crengine has no fonts and renders blank text, so at least
 * one registered font is mandatory.
 */
JNIEXPORT jboolean JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jstring jFontPath) {
    CRLog::setLogLevel(CRLog::LL_ERROR);

    // Don't enumerate system fonts; we register exactly one ourselves.
    InitFontManager(lString8::empty_str, false);

    const char* fontPath = env->GetStringUTFChars(jFontPath, nullptr);
    bool registered = fontMan->RegisterFont(lString8(fontPath));
    LOGI("RegisterFont(%s) -> %d, fontCount=%d", fontPath, registered, fontMan->GetFontCount());
    env->ReleaseStringUTFChars(jFontPath, fontPath);

    // Hyphenation manager with no dictionaries (no hyphenation).
    HyphMan::initDictionaries(lString32::empty_str);
    HyphMan::activateDictionary(lString32(HYPH_DICT_ID_NONE));

    return (registered && fontMan->GetFontCount() > 0) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Open a document from an in-memory byte buffer. Returns an opaque handle to a
 * heap-allocated LVDocView, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeOpen(
        JNIEnv* env, jobject /*thiz*/, jbyteArray jBytes, jstring jFormatHint) {
    jsize len = env->GetArrayLength(jBytes);
    jbyte* raw = env->GetByteArrayElements(jBytes, nullptr);

    // createCopy=true: crengine owns the buffer for the document's lifetime.
    LVStreamRef stream = LVCreateMemoryStream(raw, (int) len, true, LVOM_READ);
    env->ReleaseByteArrayElements(jBytes, raw, JNI_ABORT);

    const char* hint = env->GetStringUTFChars(jFormatHint, nullptr);
    lString32 contentPath = Utf8ToUnicode(lString8(hint));
    env->ReleaseStringUTFChars(jFormatHint, hint);

    auto* view = new LVDocView(32, false);
    bool ok = view->LoadDocument(stream, contentPath.c_str(), false);
    if (!ok) {
        LOGE("LoadDocument failed");
        delete view;
        return 0;
    }
    LOGI("Document loaded");
    return reinterpret_cast<jlong>(view);
}

/*
 * Reflow at (width,height), go to pageIndex and rasterise it into dst. dst must
 * be an ARGB_8888 Bitmap of exactly width x height.
 */
JNIEXPORT void JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeRenderPage(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint pageIndex, jint width,
        jint height, jobject dst) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return;

    view->Resize(width, height);
    view->goToPage(pageIndex);

    LVDrawBuf* drawbuf = BitmapAccessorInterface::getInstance()->lock(env, dst);
    if (drawbuf == nullptr) {
        LOGE("bitmap lock failed");
        return;
    }
    view->Draw(*drawbuf, false);
    BitmapAccessorInterface::getInstance()->unlock(env, dst, drawbuf);
}

JNIEXPORT void JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeClose(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    delete view;
}

}  // extern "C"
