/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2012 Vadim Lopatin <coolreader.org@gmail.com>      *
 *   Copyright (C) 2019,2020 Konstantin Potapov <pkbo@users.sourceforge.net>
 *   Copyright (C) 2018-2022 poire-z <poire-z@users.noreply.github.com>    *
 *   Copyright (C) 2018,2020,2023 Aleksey Chernov <valexlin@gmail.com>     *
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

#ifndef __LDOMNODE_H_INCLUDED__
#define __LDOMNODE_H_INCLUDED__

#include <lvstyles.h>
#include <lvtinydom_common.h>
#include <lvtinynodecollection.h>

class ldomDocument;
class ldomTextNode;
class tinyElement;
class LVDocViewCallback;
class RenderRectAccessor;

struct lxmlAttribute;
struct css_elem_def_props_t;

// To be used for 'direction' in ldomNode->elementFromPoint(lvPoint pt, int direction)
// and ldomDocument->createXPointer(lvPoint pt, int direction...) as a way to
// self-document what's expected (but the code does > and < comparisons, so
// don't change these values - some clients may also already use 0/1/-1).
// Use PT_DIR_EXACT to find the exact node at pt (with y AND x check),
// which is needed when selecting text or checking if tap is on a link,
// (necessary in table cells or floats, and in RTL text).
// Use PT_DIR_SCAN_* when interested only in finding the slice of a page
// at y (eg. to get the current page top), finding the nearest node in
// direction if pt.y happens to be in some node margin area.
// Use PT_DIR_SCAN_BACKWARD_LOGICAL_* when looking a whole page range
// xpointers, to not miss words on first or last line in bidi/RTL text.
#define PT_DIR_SCAN_BACKWARD_LOGICAL_LAST  -3
#define PT_DIR_SCAN_BACKWARD_LOGICAL_FIRST -2
#define PT_DIR_SCAN_BACKWARD               -1
#define PT_DIR_EXACT                       0
#define PT_DIR_SCAN_FORWARD                1
#define PT_DIR_SCAN_FORWARD_LOGICAL_FIRST  2
#define PT_DIR_SCAN_FORWARD_LOGICAL_LAST   3

/// max number which could be stored in ldomNodeHandle._docIndex
// #define MAX_DOCUMENT_INSTANCE_COUNT 256
#define MAX_DOCUMENT_INSTANCE_COUNT 16

// no vtable, very small size (16 bytes)
// optimized for 32 bit systems
struct ldomNode
{
    friend class tinyNodeCollection;
    friend class RenderRectAccessor;
    friend class NodeImageProxy;
    friend class ldomDocument;
private:
    static ldomDocument* _documentInstances[MAX_DOCUMENT_INSTANCE_COUNT];

    /// adds document to list, returns ID of allocated document, -1 if no space in instance array
    static int registerDocument(ldomDocument* doc);
    /// removes document from list
    static void unregisterDocument(ldomDocument* doc);

    // types for _handle._type
    enum
    {
        NT_TEXT = 0,   // mutable text node
        NT_ELEMENT = 1 // mutable element node
        ,
        NT_PTEXT = 2,   // immutable (persistent) text node
        NT_PELEMENT = 3 // immutable (persistent) element node
    };

    /// compact 32bit value for node
    struct ldomNodeHandle
    {
        // See comment above around #define TNC_PART_COUNT and TNC_PART_SHIFT changes
        //  in lvtinynodecollection.h
        // Original crengine field sizes:
        // unsigned _docIndex:8;
        // unsigned _dataIndex:24;
        unsigned _docIndex :4;  // index in ldomNode::_documentInstances[MAX_DOCUMENT_INSTANCE_COUNT];
        unsigned _dataIndex:28; // index of node in document's storage and type
    };

    /// 0: packed 32bit data field
    ldomNodeHandle _handle; // _docIndex, _dataIndex, _type

    /// 4: misc data 4 bytes (8 bytes on x64)
    union
    {                            // [8] 8 bytes (16 bytes on x64)
        ldomTextNode* _text_ptr; // NT_TEXT: mutable text node pointer
        tinyElement* _elem_ptr;  // NT_ELEMENT: mutable element pointer
        lUInt32 _pelem_addr;     // NT_PELEMENT: element storage address: chunk+offset
        lUInt32 _ptext_addr;     // NT_PTEXT: persistent text storage address: chunk+offset
        lUInt32 _nextFreeIndex;  // NULL for removed items
    } _data;

    /// sets document for node
    inline void setDocumentIndex(int index) {
        _handle._docIndex = index;
    }
    void setStyleIndexInternal(lUInt16 index);
    void setFontIndexInternal(lUInt16 index);

#define TNTYPE  (_handle._dataIndex & 0x0F)
#define TNINDEX (_handle._dataIndex & (~0x0E))
#define TNCHUNK (_addr >> &(~0x0F))
    void onCollectionDestroy();
    inline ldomNode* getTinyNode(lUInt32 index) const {
        return ((tinyNodeCollection*)getDocument())->getTinyNode(index);
    }

    void operator delete(void*) {
        // Do nothing. Just to disable delete.
    }

    /// changes parent of item
    void setParentNode(ldomNode* newParent);
    /// add child
    void addChild(lInt32 childNodeIndex);

    /// call to invalidate cache if persistent node content is modified
    void modified();

    /// returns copy of render data structure
    void getRenderData(lvdomElementFormatRec& dst);
    /// sets new value for render data structure
    void setRenderData(lvdomElementFormatRec& newData);

    void autoboxChildren(int startIndex, int endIndex, bool handleFloating = false);
    void removeChildren(int startIndex, int endIndex);
    bool cleanIfOnlyEmptyTextInline(bool handleFloating = false);
    /// returns true if element has inline content (non empty text, images, <BR>)
    bool hasNonEmptyInlineContent(bool ignoreFloats = false);
public:
    // Generic version of autoboxChildren() without any specific inline/block checking,
    // accepting any element id (from the enum el_*, like el_div, el_tabularBox) as
    // the wrapping element.
    ldomNode* boxWrapChildren(int startIndex, int endIndex, lUInt16 elementId);

    // Ensure this node has a ::before/::after pseudo element as
    // child, creating it if needed and possible
    void ensurePseudoElement(bool is_before);

    /// if stylesheet file name is set, and file is found, set stylesheet to its value
    bool applyNodeStylesheet();

    bool initNodeFont();
    void initNodeStyle();
    /// init render method for this node only (children should already have rend method set)
    void initNodeRendMethod();
    /// init render method for the whole subtree
    void initNodeRendMethodRecursive();
    /// init render method for the whole subtree
    void initNodeStyleRecursive(LVDocViewCallback* progressCallback);

    /// remove node, clear resources
    void destroy();

    /// returns true for invalid/deleted node ot NULL this pointer
    inline bool isNull() const {
        return _handle._dataIndex == 0 || getDocument() == NULL;
    }
    /// returns true if node is stored in persistent storage
    inline bool isPersistent() const {
        return (_handle._dataIndex & 2) != 0;
    }
    /// returns data index of node's registration in document data storage
    inline lInt32 getDataIndex() const {
        return TNINDEX;
    }
    /// returns pointer to document
    inline ldomDocument* getDocument() const {
        return _documentInstances[_handle._docIndex];
    }
    /// returns pointer to parent node, NULL if node has no parent
    ldomNode* getParentNode() const;
    /// returns node type, either LXML_TEXT_NODE or LXML_ELEMENT_NODE
    inline lUInt8 getNodeType() const {
        return (_handle._dataIndex & 1) ? LXML_ELEMENT_NODE : LXML_TEXT_NODE;
    }
    /// returns node level, 0 is root node
    lUInt8 getNodeLevel() const;
    /// returns dataIndex of node's parent, 0 if no parent
    int getParentIndex() const;
    /// returns index of node inside parent's child collection
    int getNodeIndex() const;
    /// returns index of child node by dataIndex
    int getChildIndex(lUInt32 dataIndex) const;
    /// returns true if node is document's root
    bool isRoot() const;
    /// returns true if node is text
    inline bool isText() const {
        return _handle._dataIndex && !(_handle._dataIndex & 1);
    }
    /// returns true if node is element
    inline bool isElement() const {
        return _handle._dataIndex && (_handle._dataIndex & 1);
    }
    /// returns true if node is and element that has children
    inline bool hasChildren() const {
        return getChildCount() != 0;
    }
    /// returns true if node is element has attributes
    inline bool hasAttributes() const {
        return getAttrCount() != 0;
    }

    /// returns element child count
    int getChildCount() const;
    /// returns element attribute count
    int getAttrCount() const;
    /// returns attribute value by attribute name id and namespace id
    const lString32& getAttributeValue(lUInt16 nsid, lUInt16 id) const;
    /// returns attribute value by attribute name
    inline const lString32& getAttributeValue(const lChar32* attrName) const {
        return getAttributeValue(NULL, attrName);
    }
    /// returns attribute value by attribute name
    inline const lString32& getAttributeValue(const lChar8* attrName) const {
        return getAttributeValue(NULL, attrName);
    }
    /// returns attribute value by attribute name and namespace
    const lString32& getAttributeValue(const lChar32* nsName, const lChar32* attrName) const;
    /// returns attribute value by attribute name and namespace
    const lString32& getAttributeValue(const lChar8* nsName, const lChar8* attrName) const;
    /// returns attribute by index
    const lxmlAttribute* getAttribute(lUInt32) const;
    /// returns true if element node has attribute with specified name id and namespace id
    bool hasAttribute(lUInt16 nsId, lUInt16 attrId) const;
    /// returns attribute name by index
    const lString32& getAttributeName(lUInt32) const;
    /// sets attribute value
    void setAttributeValue(lUInt16, lUInt16, const lChar32*);
    /// returns attribute value by attribute name id
    inline const lString32& getAttributeValue(lUInt16 id) const {
        return getAttributeValue(LXML_NS_ANY, id);
    }
    /// returns true if element node has attribute with specified name id
    inline bool hasAttribute(lUInt16 id) const {
        return hasAttribute(LXML_NS_ANY, id);
    }
    /// returns lowercased attribute value by attribute name id, for case insensitive keyword checking/parsing
    inline lString32 getAttributeValueLC(lUInt16 id) const {
        if (hasAttribute(id)) {
            lString32 value = getAttributeValue(id);
            return value.lowercase();
        }
        return lString32::empty_str;
    };

    /// returns attribute value by attribute name id, looking at children if needed
    const lString32& getFirstInnerAttributeValue(lUInt16 nsid, lUInt16 id) const;
    const lString32& getFirstInnerAttributeValue(lUInt16 id) const {
        return getFirstInnerAttributeValue(LXML_NS_ANY, id);
    }

    /// returns element type structure pointer if it was set in document for this element name
    const css_elem_def_props_t* getElementTypePtr();
    /// returns element name id
    lUInt16 getNodeId() const;
    /// returns element namespace id
    lUInt16 getNodeNsId() const;
    /// replace element name id with another value
    void setNodeId(lUInt16);
    /// returns element name
    const lString32& getNodeName() const;
    /// compares node name with value, returns true if matches
    bool isNodeName(const char* name) const;
    /// returns element namespace name
    const lString32& getNodeNsName() const;

    /// returns child node by index
    ldomNode* getChildNode(lUInt32 index) const;
    /// returns true child node is element
    bool isChildNodeElement(lUInt32 index) const;
    /// returns true child node is text
    bool isChildNodeText(lUInt32 index) const;
    /// returns child node by index, NULL if node with this index is not element or nodeId!=0 and element node id!=nodeId
    ldomNode* getChildElementNode(lUInt32 index, lUInt16 nodeId = 0) const;
    /// returns child node by index, NULL if node with this index is not element or nodeTag!=0 and element node name!=nodeTag
    ldomNode* getChildElementNode(lUInt32 index, const lChar32* nodeTag) const;

    /// returns text node text as wide string
    lString32 getText(lChar32 blockDelimiter = 0, int maxSize = 0) const;
    /// returns text node text as utf8 string
    lString8 getText8(lChar8 blockDelimiter = 0, int maxSize = 0) const;
    /// sets text node text as wide string
    void setText(lString32);
    /// sets text node text as utf8 string
    void setText8(lString8);

    /// returns node absolute rectangle (with inner=true, for erm_final, additionally
    //  shifted by the inner paddings (exluding padding bottom) to get the absolute rect
    //  of the inner LFormattedText.
    void getAbsRect(lvRect& rect, bool inner = false);
    /// sets node rendering structure pointer
    void clearRenderData();
    /// reset node rendering structure pointer for sub-tree
    void clearRenderDataRecursive();
    /// calls specified function recursively for all elements of DOM tree
    void recurseElements(void (*pFun)(ldomNode* node));
    /// calls specified function recursively for all elements of DOM tree matched by matchFun
    void recurseMatchingElements(void (*pFun)(ldomNode* node), bool (*matchFun)(ldomNode* node));
    /// calls specified function recursively for all elements of DOM tree, children before parent
    void recurseElementsDeepFirst(void (*pFun)(ldomNode* node));
    /// calls specified function recursively for all nodes of DOM tree
    void recurseNodes(void (*pFun)(ldomNode* node));

    /// returns first text child element
    ldomNode* getFirstTextChild(bool skipEmpty = false);
    /// returns last text child element
    ldomNode* getLastTextChild();

    /// find node by coordinates of point in formatted document
    ldomNode* elementFromPoint(lvPoint pt, int direction, bool strict_bounds_checking = false);
    /// find final node by coordinates of point in formatted document
    ldomNode* finalBlockFromPoint(lvPoint pt);

    // rich interface stubs for supporting Element operations
    /// returns rendering method
    lvdom_element_render_method getRendMethod();
    /// sets rendering method
    void setRendMethod(lvdom_element_render_method);
    /// returns element style record
    css_style_ref_t getStyle() const;
    /// returns element font
    font_ref_t getFont();
    /// sets element font
    void setFont(font_ref_t);
    /// sets element style record
    void setStyle(css_style_ref_t&);
    /// returns first child node
    ldomNode* getFirstChild() const;
    /// returns last child node
    ldomNode* getLastChild() const;
    /// returns previous sibling node
    ldomNode* getPrevSibling() const;
    /// returns next sibling node
    ldomNode* getNextSibling() const;
    /// removes and deletes last child element
    void removeLastChild();
    /// move range of children startChildIndex to endChildIndex inclusively to specified element
    void moveItemsTo(ldomNode*, int, int);
    /// find child element by tag id
    ldomNode* findChildElement(lUInt16 nsid, lUInt16 id, int index);
    /// find child element by id path
    ldomNode* findChildElement(lUInt16 idPath[]);
    /// inserts child element
    ldomNode* insertChildElement(lUInt32 index, lUInt16 nsid, lUInt16 id);
    /// inserts child element
    ldomNode* insertChildElement(lUInt16 id);
    /// inserts child text
    ldomNode* insertChildText(lUInt32 index, const lString32& value);
    /// inserts child text
    ldomNode* insertChildText(const lString32& value);
    /// inserts child text
    ldomNode* insertChildText(const lString8& value, bool before_last_child = false);
    /// remove child
    ldomNode* removeChild(lUInt32 index);

    /// returns XPath segment for this element relative to parent element (e.g. "p[10]")
    lString32 getXPathSegment() const;

    /// creates stream to read base64 encoded data from element
    LVStreamRef createBase64Stream();
    /// returns object image source
    LVImageSourceRef getObjectImageSource();
    /// returns object image ref name
    lString32 getObjectImageRefName(bool percentDecode = true);
    /// returns object image stream
    LVStreamRef getObjectImageStream();
    /// returns the sum of this node and its parents' top and bottom margins, borders and paddings
    /// (provide account_height_below_strut_baseline=true for images, as they align on the baseline,
    /// so their container would be larger because of the strut)
    int getSurroundingAddedHeight(bool account_height_below_strut_baseline = false);
    /// formats final block
    int renderFinalBlock(LFormattedTextRef& frmtext, RenderRectAccessor* fmt, int width,
                         BlockFloatFootprint* float_footprint = NULL);
    /// formats final block again after change, returns true if size of block is changed
    bool refreshFinalBlock();
    /// replace node with r/o persistent implementation
    ldomNode* persist();
    /// replace node with r/w implementation
    ldomNode* modify();

    /// for display:list-item node, get marker
    bool getNodeListMarker(int& counterValue, lString32& marker, int& markerWidth);
    /// is node a floating floatBox
    bool isFloatingBox() const;
    /// is node an inlineBox that has not been re-inlined by having
    /// its child no more inline-block/inline-table
    bool isBoxingInlineBox() const;
    /// is node an inlineBox that wraps a bogus embedded block (not inline-block/inline-table)
    /// can be called with inline_box_checks_done=true when isBoxingInlineBox() has already
    /// been called to avoid rechecking what is known
    bool isEmbeddedBlockBoxingInlineBox(bool inline_box_checks_done = false) const;

    /// is node any of our internal boxing element (or, optionally, our pseudoElem)
    bool isBoxingNode(bool orPseudoElem = false, lUInt16 exceptBoxingNodeId = 0) const;

    /// return real (as in the original HTML) parent/siblings by skipping any internal
    /// boxing element up or down (returns NULL when no more sibling)
    ldomNode* getUnboxedParent(lUInt16 exceptBoxingNodeId = 0) const;
    ldomNode* getUnboxedFirstChild(bool skip_text_nodes = false, lUInt16 exceptBoxingNodeId = 0) const;
    ldomNode* getUnboxedLastChild(bool skip_text_nodes = false, lUInt16 exceptBoxingNodeId = 0) const;
    ldomNode* getUnboxedPrevSibling(bool skip_text_nodes = false, lUInt16 exceptBoxingNodeId = 0) const;
    ldomNode* getUnboxedNextSibling(bool skip_text_nodes = false, lUInt16 exceptBoxingNodeId = 0) const;
};

#endif // __LDOMNODE_H_INCLUDED__
