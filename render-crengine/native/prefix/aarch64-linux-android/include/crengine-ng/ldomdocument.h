/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2012 Vadim Lopatin <coolreader.org@gmail.com>      *
 *   Copyright (C) 2013,2020 Konstantin Potapov <pkbo@users.sourceforge.net>
 *   Copyright (C) 2017-2021 poire-z <poire-z@users.noreply.github.com>    *
 *   Copyright (C) 2020,2021,2023 Aleksey Chernov <valexlin@gmail.com>     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or         *
 *   modify it under the terms of the GNU General Public License           *
 *   as published by the Free Software Foundation; either version 2        *
 *   of the License, or (at your option) any later version.                *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the Free Software           *
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,            *
 *   MA 02110-1301, USA.                                                   *
 ***************************************************************************/

#ifndef __LDOMDOCUMENT_H_INCLUDED__
#define __LDOMDOCUMENT_H_INCLUDED__

#include <lxmldocbase.h>
#include <lvtocitem.h>
#include <lvpagemap.h>
#include <ldomxrange.h>
#include <ldomxrangelist.h>
#include <lvembeddedfont.h>

class LVRendPageList;

class ListNumberingProps
{
public:
    int maxCounter;
    int maxWidth;
    ListNumberingProps(int c, int w)
            : maxCounter(c)
            , maxWidth(w) {
    }
};
typedef LVRef<ListNumberingProps> ListNumberingPropsRef;

class ldomDocument: public lxmlDocBase
{
    friend class ldomDocumentWriter;
    friend class ldomDocumentWriterFilter;
private:
    LVTocItem m_toc;
    LVPageMap m_pagemap;
    font_ref_t _def_font; // default font
    css_style_ref_t _def_style;
    lUInt32 _last_docflags;
    int _page_height;
    int _page_width;
    bool _parsing;
    bool _rendered;
    bool _just_rendered_from_cache;
    bool _toc_from_cache_valid;
    lUInt32 _warnings_seen_bitmap;
    ldomXRangeList _selections;
    lUInt32 _doc_rendering_hash;
    bool _open_from_cache;

    lString32 _docStylesheetFileName;

    LVContainerRef _container;

    LVHashTable<lUInt32, ListNumberingPropsRef> lists;

    LVEmbeddedFontList _fontList;

    /// load document cache file content
    bool loadCacheFileContent(CacheLoadingCallback* formatCallback, LVDocViewCallback* progressCallback = NULL);

    /// save changes to cache file
    bool saveChanges();
    /// saves changes to cache file, limited by time interval (can be called again to continue after TIMEOUT)
    virtual ContinuousOperationResult saveChanges(CRTimerUtil& maxTime, LVDocViewCallback* progressCallback = NULL);

    /// create XPointer from a non-normalized string made by toStringV1()
    ldomXPointer createXPointerV1(ldomNode* baseNode, const lString32& xPointerStr);
    /// create XPointer from a normalized string made by toStringV2()
    ldomXPointer createXPointerV2(ldomNode* baseNode, const lString32& xPointerStr);
protected:
    void applyDocumentStyleSheet();
public:
    lUInt32 getDocumentRenderingHash() const {
        return _doc_rendering_hash;
    }
    void forceReinitStyles() {
        dropStyles();
        _hdr.render_style_hash = 0;
        _rendered = false;
    }

    ListNumberingPropsRef getNodeNumberingProps(lUInt32 nodeDataIndex);
    void setNodeNumberingProps(lUInt32 nodeDataIndex, ListNumberingPropsRef v);
    void resetNodeNumberingProps();

    /// returns object image stream
    LVStreamRef getObjectImageStream(lString32 refName);
    /// returns object image source
    LVImageSourceRef getObjectImageSource(lString32 refName);

    bool isDefStyleSet() {
        return !_def_style.isNull();
    }

    /// return document's embedded font list
    LVEmbeddedFontList& getEmbeddedFontList() {
        return _fontList;
    }
    /// register embedded document fonts in font manager, if any exist in document
    void registerEmbeddedFonts();
    /// unregister embedded document fonts in font manager, if any exist in document
    void unregisterEmbeddedFonts();

    /// returns pointer to TOC root node
    LVTocItem* getToc() {
        return &m_toc;
    }
    /// build alternative TOC from document heading elements (H1 to H6) and cr-hints, or docFragments
    void buildAlternativeToc();
    bool isTocAlternativeToc() {
        return m_toc.hasAlternativeTocFlag();
    }
    /// build TOC from headings
    void buildTocFromHeadings();

    /// returns pointer to PageMapItems container
    LVPageMap* getPageMap() {
        return &m_pagemap;
    }

    bool isTocFromCacheValid() {
        return _toc_from_cache_valid;
    }

    /// save document formatting parameters after render
    void updateRenderContext();
    /// check document formatting parameters before render - whether we need to reformat; returns false if render is necessary
    bool checkRenderContext();

    /// try opening from cache file, find by source file name (w/o path) and crc32
    virtual bool openFromCache(CacheLoadingCallback* formatCallback, LVDocViewCallback* progressCallback = NULL);
    /// saves recent changes to mapped file
    virtual ContinuousOperationResult updateMap(CRTimerUtil& maxTime, LVDocViewCallback* progressCallback = NULL);
    /// swaps to cache file or saves changes, limited by time interval
    virtual ContinuousOperationResult swapToCache(CRTimerUtil& maxTime);
    /// saves recent changes to mapped file
    virtual bool updateMap(LVDocViewCallback* progressCallback = NULL) {
        CRTimerUtil infinite;
        return updateMap(infinite, progressCallback) != CR_ERROR; // NOLINT: Call to virtual function during destruction
    }

    LVContainerRef getContainer() {
        return _container;
    }
    void setContainer(LVContainerRef cont) {
        _container = cont;
    }

    void clearRendBlockCache() {
        _renderedBlockCache.clear();
    }
    void clear();
    lString32 getDocStylesheetFileName() {
        return _docStylesheetFileName;
    }
    void setDocStylesheetFileName(lString32 fileName) {
        _docStylesheetFileName = fileName;
    }

    ldomDocument();
    /// creates empty document which is ready to be copy target of doc partial contents
    ldomDocument(ldomDocument& doc);

    /// return selections collection
    ldomXRangeList& getSelections() {
        return _selections;
    }

    /// get full document height
    int getFullHeight();
    /// returns page height setting
    int getPageHeight() {
        return _page_height;
    }
    /// returns page width setting
    int getPageWidth() {
        return _page_width;
    }
    /// saves document contents as XML to stream with specified encoding
    bool saveToStream(LVStreamRef stream, const char* codepage, bool treeLayout = false);
    /// print a warning message (only once if warning_id provided, between 1 and 32)
    void printWarning(const char* msg, int warning_id = 0);
    /// get default font reference
    font_ref_t getDefaultFont() {
        return _def_font;
    }
    /// get default style reference
    css_style_ref_t getDefaultStyle() {
        return _def_style;
    }

    bool parseStyleSheet(lString32 codeBase, lString32 css);
    bool parseStyleSheet(lString32 cssFile);
    /// destructor
    virtual ~ldomDocument();
    bool isRendered() {
        return _rendered;
    }
    bool isBeingParsed() {
        return _parsing;
    }
    bool isOpenFromCache() const {
        return _open_from_cache;
    }
    /// renders (formats) document in memory: returns true if re-rendering needed, false if not
    virtual bool render(LVRendPageList* pages, LVDocViewCallback* callback, int width, int dy,
                        bool showCover, int y0, font_ref_t def_font, int def_interline_space,
                        CRPropRef props, int usable_left_overflow = 0, int usable_right_overflow = 0);
    /// set global rendering properties
    virtual bool setRenderProps(int width, int dy, bool showCover, int y0, font_ref_t def_font,
                                int def_interline_space, CRPropRef props);
    /// create xpointer from pointer string
    ldomXPointer createXPointer(const lString32& xPointerStr);
    /// create xpointer from pointer string
    ldomNode* nodeFromXPath(const lString32& xPointerStr) {
        return createXPointer(xPointerStr).getNode();
    }
    /// get element text by pointer string
    lString32 textFromXPath(const lString32& xPointerStr);

    /// create xpointer from relative pointer string
    ldomXPointer createXPointer(ldomNode* baseNode, const lString32& xPointerStr);

    /// create xpointer from doc point
    ldomXPointer createXPointer(lvPoint pt, int direction = PT_DIR_EXACT, bool strictBounds = false, ldomNode* from_node = NULL);
    /// get rendered block cache object
    CVRendBlockCache& getRendBlockCache() {
        return _renderedBlockCache;
    }

    bool findText(lString32 pattern, bool caseInsensitive, bool reverse, int minY, int maxY, LVArray<ldomWord>& words, int maxCount, int maxHeight, int maxHeightCheckStartY = -1);
};

#endif // __LDOMDOCUMENT_H_INCLUDED__
