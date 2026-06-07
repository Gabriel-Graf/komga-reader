/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2008-2012 Vadim Lopatin <coolreader.org@gmail.com>      *
 *   Copyright (C) 2018-2020 poire-z <poire-z@users.noreply.github.com>    *
 *   Copyright (C) 2020 Jellby <jellby@yahoo.com>                          *
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

#ifndef __LVTINYNODECOLLECTION_H_INCLUDED__
#define __LVTINYNODECOLLECTION_H_INCLUDED__

#include <lvstyles.h>
#include <lvtextfm.h>
#include <crprops.h>
#include <lvstsheet.h>
#include <lvtinydom_common.h>

struct ldomNode;
class LVDocViewCallback;
class CacheLoadingCallback;
class ldomBlobCache;
class ldomDataStorageManager;
class CacheFile;

/// final block cache
typedef LVRef<LFormattedText> LFormattedTextRef;
typedef LVCacheMap<ldomNode*, LFormattedTextRef> CVRendBlockCache;

/// return value for continuous operations
typedef enum
{
    CR_DONE,    ///< operation is finished successfully
    CR_TIMEOUT, ///< operation is incomplete - interrupted by timeout
    CR_ERROR    ///< error while executing operation
} ContinuousOperationResult;

/// type of image scaling
typedef enum
{
    IMG_NO_SCALE,        /// scaling is disabled
    IMG_INTEGER_SCALING, /// integer multipier/divisor scaling -- *2, *3 only
    IMG_FREE_SCALING     /// free scaling, non-integer factor
} img_scaling_mode_t;

/// image scaling option
struct img_scaling_option_t
{
    img_scaling_mode_t mode;
    int max_scale;
    int getHash() {
        return (int)mode * 33 + max_scale;
    }
    // creates default option value
    img_scaling_option_t();
};

/// set of images scaling options for different kind of images
struct img_scaling_options_t
{
    img_scaling_option_t zoom_in_inline;
    img_scaling_option_t zoom_in_block;
    img_scaling_option_t zoom_out_inline;
    img_scaling_option_t zoom_out_block;
    /// returns hash value
    int getHash() {
        return (((zoom_in_inline.getHash() * 33 + zoom_in_block.getHash()) * 33 + zoom_out_inline.getHash()) * 33 + zoom_out_block.getHash());
    }
    /// creates default options
    img_scaling_options_t();
    /// returns true if any changes occured
    bool update(CRPropRef props, int fontSize);
};

/// docFlag mask, enable internal stylesheet of document and style attribute of elements
#define DOC_FLAG_ENABLE_INTERNAL_STYLES 1
/// docFlag mask, enable paperbook-like footnotes
#define DOC_FLAG_ENABLE_FOOTNOTES 2
/// docFlag mask, enable preformatted text
#define DOC_FLAG_PREFORMATTED_TEXT 4
/// docFlag mask, enable document embedded fonts (EPUB)
#define DOC_FLAG_ENABLE_DOC_FONTS 8
/// docFlag mask, force page breaks on non-linear fragments (EPUB)
#define DOC_FLAG_NONLINEAR_PAGEBREAK 16
/// default docFlag set
#define DOC_FLAG_DEFAULTS (DOC_FLAG_ENABLE_INTERNAL_STYLES | DOC_FLAG_ENABLE_FOOTNOTES | DOC_FLAG_ENABLE_DOC_FONTS)

#define DEF_SPACE_WIDTH_SCALE_PERCENT        100
#define DEF_MIN_SPACE_CONDENSING_PERCENT     50
#define DEF_UNUSED_SPACE_THRESHOLD_PERCENT   5
#define DEF_MAX_ADDED_LETTER_SPACING_PERCENT 0

//#define TNC_PART_COUNT 1024
//#define TNC_PART_SHIFT 10
#define TNC_PART_COUNT       4096
#define TNC_PART_SHIFT       12
#define TNC_PART_INDEX_SHIFT (TNC_PART_SHIFT + 4)
#define TNC_PART_LEN         (1 << TNC_PART_SHIFT)
#define TNC_PART_MASK        (TNC_PART_LEN - 1)

// About these #define TNC_PART_* :
// A ldomNode unique reference is defined by:
//    struct ldomNodeHandle {     /// compact 32bit value for node
//        unsigned _docIndex:8;   // index in ldomNode::_documentInstances[MAX_DOCUMENT_INSTANCE_COUNT];
//        unsigned _dataIndex:24; // index of node in document's storage and type
//    };
// The 24 bits of _dataIndex are used that way:
//        return &(_elemList[index>>TNC_PART_INDEX_SHIFT][(index>>4)&TNC_PART_MASK]);
//        #define TNTYPE  (_handle._dataIndex&0x0F)
//        #define TNINDEX (_handle._dataIndex&(~0x0E))
//   24>15 10bits (1024 values) : index in the first-level _elemList[TNC_PART_COUNT]
//   14> 5 10bits (1024 values) : sub-index in second-level _elemList[first_index][]
//    4> 1  4bits (16 values) : type (bit 1: text | element, bit 2: mutable | permanent)
//                                   (bit 3 and 4 are not used, so we could grab 2 more bits from here if needed)
//
// We can update ldomNodeHandle to:
//    struct ldomNodeHandle {
//        unsigned _docIndex:4;   // decreasing MAX_DOCUMENT_INSTANCE_COUNT from 256 to 16
//        unsigned _dataIndex:28; // get 4 more bits that we can distribute to these indexes.
//    };
// The other #define below (and possibly the code too) assume the same TNC_PART_SHIFT for both indexes,
// so let's distribute 2 bits to each:
//   28>17 12bits (4096 values) : index in the first-level _elemList[TNC_PART_COUNT]
//   16> 5 12bits (4096 values) : sub-index in second-level _elemList[first_index][]
//    4> 1  4bits (16 values)
// With that, we have increased the max number of text nodes and the max number of
// element nodes from 1024x1024 (1M) to 4096x4096 (16M) which allows loading very large books.

/// storage of ldomNode
class tinyNodeCollection
{
    friend struct ldomNode;
    friend class tinyElement;
    friend class ldomDocument;
private:
    int _textCount;
    lUInt32 _textNextFree;
    ldomNode* _textList[TNC_PART_COUNT];
    int _elemCount;
    lUInt32 _elemNextFree;
    ldomNode* _elemList[TNC_PART_COUNT];
    LVIndexedRefCache<css_style_ref_t> _styles;
    LVIndexedRefCache<font_ref_t> _fonts;
    int _tinyElementCount;
    int _itemCount;
    int _docIndex;
protected:
    /// final block cache
    CVRendBlockCache _renderedBlockCache;
    CacheFile* _cacheFile;
    bool _cacheFileStale;
    bool _cacheFileLeaveAsDirty;
    bool _mapped;
    bool _maperror;
    int _mapSavingStage;

    img_scaling_options_t _imgScalingOptions;
    int _spaceWidthScalePercent;
    int _minSpaceCondensingPercent;
    int _unusedSpaceThresholdPercent;
    int _maxAddedLetterSpacingPercent;

    lUInt32 _nodeStyleHash;
    lUInt32 _nodeDisplayStyleHash;
    lUInt32 _nodeDisplayStyleHashInitial;
    bool _nodeStylesInvalidIfLoading;

    int calcFinalBlocks();
    void dropStyles();
    bool _hangingPunctuationEnabled;
    lUInt32 _renderBlockRenderingFlags;
    lUInt32 _DOMVersionRequested;
    int _interlineScaleFactor;

    ldomDataStorageManager* _textStorage;  // persistent text node data storage
    ldomDataStorageManager* _elemStorage;  // persistent element data storage
    ldomDataStorageManager* _rectStorage;  // element render rect storage
    ldomDataStorageManager* _styleStorage; // element style storage (font & style indexes ldomNodeStyleInfo)

    CRPropRef _docProps;
    lUInt32 _docFlags; // document flags

    int _styleIndex;

    LVStyleSheet _stylesheet;

    LVHashTable<lUInt16, lUInt16> _fontMap; // style index to font index

    /// checks buffer sizes, compacts most unused chunks
    ldomBlobCache* _blobCache;

    /// uniquie id of file format parsing option (usually 0, but 1 for preformatted text files)
    int getPersistenceFlags();

    bool saveStylesData();
    bool loadStylesData();
    bool updateLoadedStyles(bool enabled);
    lUInt32 calcStyleHash(bool already_rendered);
    bool saveNodeData();
    bool saveNodeData(lUInt16 type, ldomNode** list, int nodecount);
    bool loadNodeData();
    bool loadNodeData(lUInt16 type, ldomNode** list, int nodecount);

    bool hasRenderData();

    bool openCacheFile();

    void setNodeStyleIndex(lUInt32 dataIndex, lUInt16 index);
    void setNodeFontIndex(lUInt32 dataIndex, lUInt16 index);
    lUInt16 getNodeStyleIndex(lUInt32 dataIndex);
    lUInt16 getNodeFontIndex(lUInt32 dataIndex);
    css_style_ref_t getNodeStyle(lUInt32 dataIndex);
    font_ref_t getNodeFont(lUInt32 dataIndex);
    void setNodeStyle(lUInt32 dataIndex, css_style_ref_t& v);
    void setNodeFont(lUInt32 dataIndex, font_ref_t& v);
    void clearNodeStyle(lUInt32 dataIndex);
    virtual void resetNodeNumberingProps() { }

    /// creates empty collection
    tinyNodeCollection();
    tinyNodeCollection(tinyNodeCollection& v);
public:
    int getSpaceWidthScalePercent() {
        return _spaceWidthScalePercent;
    }

    bool setSpaceWidthScalePercent(int spaceWidthScalePercent) {
        if (spaceWidthScalePercent == _spaceWidthScalePercent)
            return false;
        _spaceWidthScalePercent = spaceWidthScalePercent;
        return true;
    }

    bool setMinSpaceCondensingPercent(int minSpaceCondensingPercent) {
        if (minSpaceCondensingPercent == _minSpaceCondensingPercent)
            return false;
        _minSpaceCondensingPercent = minSpaceCondensingPercent;
        return true;
    }

    bool setUnusedSpaceThresholdPercent(int unusedSpaceThresholdPercent) {
        if (unusedSpaceThresholdPercent == _unusedSpaceThresholdPercent)
            return false;
        _unusedSpaceThresholdPercent = unusedSpaceThresholdPercent;
        return true;
    }

    bool setMaxAddedLetterSpacingPercent(int maxAddedLetterSpacingPercent) {
        if (maxAddedLetterSpacingPercent == _maxAddedLetterSpacingPercent)
            return false;
        _maxAddedLetterSpacingPercent = maxAddedLetterSpacingPercent;
        // This does not need to trigger a re-rendering, just
        // a re-formatting of the final blocks
        _renderedBlockCache.clear();
        return true;
    }

    /// add named BLOB data to document
    bool addBlob(lString32 name, const lUInt8* data, int size);
    /// get BLOB by name
    LVStreamRef getBlob(lString32 name);

    /// called on document loading end
    bool validateDocument();

    /// swaps to cache file or saves changes, limited by time interval (can be called again to continue after TIMEOUT)
    virtual ContinuousOperationResult swapToCache(CRTimerUtil& maxTime) = 0;
    /// try opening from cache file, find by source file name (w/o path) and crc32
    virtual bool openFromCache(CacheLoadingCallback* formatCallback, LVDocViewCallback* progressCallback = NULL) = 0;
    /// saves recent changes to mapped file, with timeout (can be called again to continue after TIMEOUT)
    virtual ContinuousOperationResult updateMap(CRTimerUtil& maxTime, LVDocViewCallback* progressCallback = NULL) = 0;
    /// saves recent changes to mapped file
    virtual bool updateMap(LVDocViewCallback* progressCallback = NULL) {
        CRTimerUtil infinite;
        return updateMap(infinite, progressCallback) != CR_ERROR;
    }

    bool swapToCacheIfNecessary();

    bool createCacheFile();

    bool getHangingPunctiationEnabled() const {
        return _hangingPunctuationEnabled;
    }
    bool setHangingPunctiationEnabled(bool value);

    lUInt32 getRenderBlockRenderingFlags() const {
        return _renderBlockRenderingFlags;
    }
    bool setRenderBlockRenderingFlags(lUInt32 flags);

    lUInt32 getDOMVersionRequested() const {
        return _DOMVersionRequested;
    }
    bool setDOMVersionRequested(lUInt32 version);

    int getInterlineScaleFactor() const {
        return _interlineScaleFactor;
    }
    bool setInterlineScaleFactor(int value);

    inline bool getDocFlag(lUInt32 mask) {
        return (_docFlags & mask) != 0;
    }

    void setDocFlag(lUInt32 mask, bool value);

    inline lUInt32 getDocFlags() {
        return _docFlags;
    }

    inline int getDocIndex() {
        return _docIndex;
    }

    inline int getFontContextDocIndex() {
        return (_docFlags & DOC_FLAG_ENABLE_DOC_FONTS) && (_docFlags & DOC_FLAG_ENABLE_INTERNAL_STYLES) ? _docIndex : -1;
    }

    void setDocFlags(lUInt32 value);

    /// returns doc properties collection
    inline CRPropRef getProps() {
        return _docProps;
    }
    /// returns doc properties collection
    void setProps(CRPropRef props) {
        _docProps = props;
    }

    /// set cache file stale flag
    void setCacheFileStale(bool stale) {
        _cacheFileStale = stale;
    }

    /// is built (and cached) DOM possibly invalid (can happen when some nodes have changed display style)
    bool isBuiltDomStale() {
        return _nodeDisplayStyleHashInitial != NODE_DISPLAY_STYLE_HASH_UNINITIALIZED &&
               _nodeDisplayStyleHash != _nodeDisplayStyleHashInitial;
    }
    void setNodeStylesInvalidIfLoading() {
        _nodeStylesInvalidIfLoading = true;
    }

    /// if a cache file is in use
    bool hasCacheFile() {
        return _cacheFile != NULL;
    }
    /// set cache file as dirty, so it's not re-used on next load
    void invalidateCacheFile() {
        _cacheFileLeaveAsDirty = true;
    }
    /// get cache file full path
    lString32 getCacheFilePath();

    /// minimize memory consumption
    void compact();
    /// dumps memory usage statistics to debug log
    void dumpStatistics();
    /// get memory usage statistics
    lString32 getStatistics();

    /// get ldomNode instance pointer
    ldomNode* getTinyNode(lUInt32 index) const;
    /// allocate new ldomNode
    ldomNode* allocTinyNode(int type);
    /// allocate new tinyElement
    ldomNode* allocTinyElement(ldomNode* parent, lUInt16 nsid, lUInt16 id);
    /// recycle ldomNode on node removing
    void recycleTinyNode(lUInt32 index);

    /// put all object into persistent storage
    virtual void persist(CRTimerUtil& maxTime);

    /// destroys collection
    virtual ~tinyNodeCollection();
};

#if 0
/// pass false to not compress data in cache files
void setCacheCompressionType(CacheCompressionType type);
#endif

#endif // __LVTINYNODECOLLECTION_H_INCLUDED__
