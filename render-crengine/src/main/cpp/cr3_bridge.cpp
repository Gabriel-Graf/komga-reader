/***************************************************************************
 *   Komga E-Ink Reader — render-crengine JNI bridge.                       *
 *                                                                          *
 *   Drives crengine-ng's LVDocView to reflow an EPUB and rasterise pages   *
 *   into Android Bitmaps, plus the ReflowableDocument seam: applyLayout     *
 *   (font + props + CSS + hyphenation + Render), page count, TOC, stable    *
 *   xpointer anchors, percent navigation and full-text search.             *
 *                                                                          *
 *   Bitmap locking + BGRX→RGBA conversion and the TOC/xpointer/findText     *
 *   access patterns are lifted from the LxReader project (jnigraphicslib,   *
 *   lvcolordrawbufex, lvdocview_wrapper.cpp, GPL-3.0-or-later).             *
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
#include <crprops.h>
#include <lvtocitem.h>
#include <ldomdocument.h>
#include <ldomxpointer.h>
#include <ldomxrange.h>
#include <ldomword.h>
#include <lvarray.h>

#include <vector>

#include "jnigraphicslib.h"

#define LOG_TAG "cr3bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Serialisierungs-Trennzeichen: ASCII Unit-/Record-Separator. Kommen in
// natürlichem Text praktisch nicht vor, daher kollisionsfrei zu Titeln/Snippets.
static const lChar32 FIELD_SEP = U'\x1F';
static const lChar32 RECORD_SEP = U'\x1E';

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

    // Hyphenation manager with no dictionaries; per-document hyphenation is
    // switched on later via the @algorithm pseudo-dictionary (no pattern file).
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
 * Apply typography + layout: register the font face, set the base font size in
 * pixels, apply the ReflowCss engine properties (keys+vals parallel arrays),
 * set the user stylesheet (text-align), then Render at (width,height). After
 * this the page count reflects the new layout.
 */
JNIEXPORT void JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeApplyLayout(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint width, jint height,
        jint fontSizePx, jobjectArray jKeys, jobjectArray jVals, jstring jUserCss) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return;

    view->Resize(width, height);

    // Build the property container from the parallel key/value string arrays.
    CRPropRef props = LVCreatePropsContainer();
    jsize n = env->GetArrayLength(jKeys);
    for (jsize i = 0; i < n; i++) {
        auto jk = (jstring) env->GetObjectArrayElement(jKeys, i);
        auto jv = (jstring) env->GetObjectArrayElement(jVals, i);
        const char* k = env->GetStringUTFChars(jk, nullptr);
        const char* v = env->GetStringUTFChars(jv, nullptr);
        props->setString(k, lString8(v));
        env->ReleaseStringUTFChars(jk, k);
        env->ReleaseStringUTFChars(jv, v);
        env->DeleteLocalRef(jk);
        env->DeleteLocalRef(jv);
    }

    view->propsUpdateDefaults(props);
    view->propsApply(props);

    // Font size is set directly (it is scaled for DPI internally).
    if (fontSizePx > 0)
        view->setFontSize(fontSizePx);

    // text-align is CSS, not a property: append as user stylesheet.
    const char* css = env->GetStringUTFChars(jUserCss, nullptr);
    view->setStyleSheet(lString8(css));
    env->ReleaseStringUTFChars(jUserCss, css);

    view->Render(width, height);
}

/* Page count of the currently laid-out document. */
JNIEXPORT jint JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativePageCount(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return 0;
    view->checkRender();
    return view->getPageCount();
}

/*
 * Go to page [pageIndex] of the current layout and rasterise it into dst, an
 * ARGB_8888 Bitmap of exactly width x height (the layout viewport).
 */
JNIEXPORT void JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeRenderPage(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint pageIndex, jint width,
        jint height, jobject dst) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return;

    view->goToPage(pageIndex);

    LVDrawBuf* drawbuf = BitmapAccessorInterface::getInstance()->lock(env, dst);
    if (drawbuf == nullptr) {
        LOGE("bitmap lock failed");
        return;
    }
    view->Draw(*drawbuf, false);
    BitmapAccessorInterface::getInstance()->unlock(env, dst, drawbuf);
}

/*
 * Flatten the TOC (LVTocItem tree) into a serialized string:
 *   title<US>xpointer<US>level<RS>title<US>xpointer<US>level<RS>...
 * Empty string if there is no TOC.
 */
JNIEXPORT jstring JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeChapters(
        JNIEnv* env, jobject /*thiz*/, jlong handle) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return env->NewStringUTF("");
    view->checkRender();

    lString32 out;
    // Depth-first pre-order over the TOC tree using an explicit stack.
    std::vector<LVTocItem*> stack;
    LVTocItem* root = view->getToc();
    if (root != nullptr) {
        for (int i = root->getChildCount() - 1; i >= 0; i--)
            stack.push_back(root->getChild(i));
    }
    while (!stack.empty()) {
        LVTocItem* item = stack.back();
        stack.pop_back();
        if (!out.empty())
            out.append(1, RECORD_SEP);
        out.append(item->getName());
        out.append(1, FIELD_SEP);
        out.append(item->getXPointer().toString());
        out.append(1, FIELD_SEP);
        out.append(lString32::itoa(item->getLevel()));
        for (int i = item->getChildCount() - 1; i >= 0; i--)
            stack.push_back(item->getChild(i));
    }
    return env->NewStringUTF(UnicodeToUtf8(out).c_str());
}

/* Stable xpointer of the top of the current page ("" if unavailable). */
JNIEXPORT jstring JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeCurrentAnchor(
        JNIEnv* env, jobject /*thiz*/, jlong handle) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return env->NewStringUTF("");
    view->checkRender();
    ldomXPointer bm = view->getBookmark();
    if (bm.isNull())
        return env->NewStringUTF("");
    return env->NewStringUTF(UnicodeToUtf8(bm.toString()).c_str());
}

/* Navigate to the position named by [jXPointer] (no-op if it doesn't resolve). */
JNIEXPORT void JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeSeekToAnchor(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jXPointer) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return;
    view->checkRender();
    const char* xp = env->GetStringUTFChars(jXPointer, nullptr);
    lString32 xpStr = Utf8ToUnicode(lString8(xp));
    env->ReleaseStringUTFChars(jXPointer, xp);
    ldomXPointer bm = view->getDocument()->createXPointer(xpStr);
    if (!bm.isNull())
        view->goToBookmark(bm);
}

/* Navigate to relative position [fraction] (0..1) via the permille percent API. */
JNIEXPORT void JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeSeekToProgress(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat fraction) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return;
    view->checkRender();
    float f = fraction;
    if (f < 0.0f) f = 0.0f;
    if (f > 1.0f) f = 1.0f;
    // crengine positions in 1/100 of a percent (0..10000): percent = f * 10000.
    int permille = (int) (f * 10000.0f);
    int fullHeight = view->GetFullHeight();
    int y = (int) ((lInt64) fullHeight * permille / 10000);
    view->SetPos(y);
}

/*
 * Full-text search. Serialized as: xpointer<US>snippet<RS>... ("" if no hits).
 */
JNIEXPORT jstring JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeSearch(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jQuery, jint maxCount) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr)
        return env->NewStringUTF("");
    view->checkRender();
    const char* q = env->GetStringUTFChars(jQuery, nullptr);
    lString32 pattern = Utf8ToUnicode(lString8(q));
    env->ReleaseStringUTFChars(jQuery, q);

    ldomDocument* doc = view->getDocument();
    LVArray<ldomWord> words;
    bool found = doc->findText(pattern, true /*caseInsensitive*/, false /*reverse*/,
                               -1, -1, words, maxCount, 0);
    if (!found)
        return env->NewStringUTF("");

    lString32 out;
    for (int i = 0; i < words.length(); i++) {
        const ldomWord& word = words[i];
        ldomXPointer start = word.getStartXPointer();
        ldomXRange range(word);
        lString32 snippet = range.getRangeText();
        if (!out.empty())
            out.append(1, RECORD_SEP);
        out.append(start.toString());
        out.append(1, FIELD_SEP);
        out.append(snippet);
    }
    return env->NewStringUTF(UnicodeToUtf8(out).c_str());
}

JNIEXPORT void JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeClose(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    delete view;
}

}  // extern "C"
