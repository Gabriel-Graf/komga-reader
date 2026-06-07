/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2012 Vadim Lopatin <coolreader.org@gmail.com>      *
 *   Copyright (C) 2018,2020,2021 poire-z <poire-z@users.noreply.github.com>
 *   Copyright (C) 2020 Aleksey Chernov <valexlin@gmail.com>               *
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

#ifndef __LXMLDOCBASE_H_INCLUDED__
#define __LXMLDOCBASE_H_INCLUDED__

#include <lvtinynodecollection.h>
#include <lvserialbuf.h>
#include <lvstring32hashedcollection.h>
#include <dtddef.h>

// default: 512K
#define DEF_DOC_DATA_BUFFER_SIZE 0x80000

#define DOC_STRING_HASH_SIZE      256
#define RESERVED_DOC_SPACE        4096
#define MAX_TYPE_ID               1024 // max of element, ns, attr
#define MAX_ELEMENT_TYPE_ID       1024
#define MAX_NAMESPACE_TYPE_ID     64
#define MAX_ATTRIBUTE_TYPE_ID     1024
#define UNKNOWN_ELEMENT_TYPE_ID   (MAX_ELEMENT_TYPE_ID >> 1)
#define UNKNOWN_ATTRIBUTE_TYPE_ID (MAX_ATTRIBUTE_TYPE_ID >> 1)
#define UNKNOWN_NAMESPACE_TYPE_ID (MAX_NAMESPACE_TYPE_ID >> 1)

class LDOMNameIdMap;

/// Base class for XML DOM documents
/**
    Helps to decrease memory usage and increase performance for DOM implementations.
    Maintains Name<->Id maps for element names, namespaces and attributes.
    It allows to use short IDs instead of strings in DOM internals,
    and avoid duplication of string values.

    Manages data storage.
*/

class lxmlDocBase: public tinyNodeCollection
{
    friend struct ldomNode;
    friend class ldomXPointer;
protected:
    /// Default constructor
    lxmlDocBase(int dataBufSize = DEF_DOC_DATA_BUFFER_SIZE);
    /// Copy constructor - copies ID tables contents
    lxmlDocBase(lxmlDocBase& doc);
public:
    /// Destructor
    virtual ~lxmlDocBase();

    /// serialize to byte array (pointer will be incremented by number of bytes written)
    void serializeMaps(SerialBuf& buf);
    /// deserialize from byte array (pointer will be incremented by number of bytes read)
    bool deserializeMaps(SerialBuf& buf);

    //======================================================================
    // Name <-> Id maps functions

    /// Get namespace name by id
    /**
        \param id is numeric value of namespace
        \return string value of namespace
    */
    const lString32& getNsName(lUInt16 id);

    /// Get namespace id by name
    /**
        \param name is string value of namespace
        \return id of namespace
    */
    lUInt16 getNsNameIndex(const lChar32* name);

    /// Get namespace id by name
    /**
        \param name is string value of namespace (ASCII only)
        \return id of namespace
    */
    lUInt16 getNsNameIndex(const lChar8* name);

    /// Get attribute name by id
    /**
        \param id is numeric value of attribute
        \return string value of attribute
    */
    const lString32& getAttrName(lUInt16 id);

    /// Get attribute id by name
    /**
        \param name is string value of attribute
        \return id of attribute
    */
    lUInt16 getAttrNameIndex(const lChar32* name);

    /// Get attribute id by name
    /**
        \param name is string value of attribute (8bit ASCII only)
        \return id of attribute
    */
    lUInt16 getAttrNameIndex(const lChar8* name);

    /// helper: returns attribute value
    inline const lString32& getAttrValue(lUInt32 index) const {
        return _attrValueTable[index];
    }

    /// helper: returns attribute value index
    inline lUInt32 getAttrValueIndex(const lChar32* value) {
        return (lUInt32)_attrValueTable.add(value);
    }

    /// helper: returns attribute value index, 0xffffffff if not found
    inline lUInt32 findAttrValueIndex(const lChar32* value) {
        return (lUInt32)_attrValueTable.find(value);
    }

    /// Get element name by id
    /**
        \param id is numeric value of element name
        \return string value of element name
    */
    const lString32& getElementName(lUInt16 id);

    /// Get element id by name
    /**
        \param name is string value of element name
        \return id of element
    */
    lUInt16 getElementNameIndex(const lChar32* name);

    /// Get element id by name
    /**
        \param name is string value of element name (8bit ASCII only)
        \return id of element, allocates new ID if not found
    */
    lUInt16 getElementNameIndex(const lChar8* name);

    /// Get element id by name
    /**
        \param name is string value of element name (8bit ASCII only)
        \return id of element, 0 if not found
    */
    lUInt16 findElementNameIndex(const lChar8* name);

    /// Get element type properties structure by id
    /**
        \param id is element id
        \return pointer to elem_def_t structure containing type properties
        \sa elem_def_t
    */
    const css_elem_def_props_t* getElementTypePtr(lUInt16 id);

    // set node types from table
    void setNodeTypes(const elem_def_t* node_scheme);
    // set attribute types from table
    void setAttributeTypes(const attr_def_t* attr_scheme);
    // set namespace types from table
    void setNameSpaceTypes(const ns_def_t* ns_scheme);
    // set node/attribute/namespace types by copying them from an other document
    void setAllTypesFrom(lxmlDocBase* d);

    // debug dump
    void dumpUnknownEntities(const char* fname);
    lString32Collection getUnknownEntities();

    /// garbage collector
    virtual void gc() {
        fontMan->gc();
    }

    inline LVStyleSheet* getStyleSheet() {
        return &_stylesheet;
    }
    /// sets style sheet, clears old content of css if arg replace is true
    void setStyleSheet(const char* css, bool replace);

    /// apply document's stylesheet to element node
    inline void applyStyle(ldomNode* element, css_style_rec_t* pstyle) {
        _stylesheet.apply(element, pstyle);
    }

    void onAttributeSet(lUInt16 attrId, lUInt32 valueId, ldomNode* node);

    /// get element by id attribute value code
    ldomNode* getNodeById(lUInt32 attrValueId);

    /// get element by id attribute value
    inline ldomNode* getElementById(const lChar32* id) {
        lUInt32 attrValueId = getAttrValueIndex(id);
        ldomNode* node = getNodeById(attrValueId);
        return node;
    }
    /// returns root element
    ldomNode* getRootNode();

    /// returns code base path relative to document container
    lString32 getCodeBase();

    /// sets code base path relative to document container
    void setCodeBase(const lString32& codeBase);

#ifdef _DEBUG
    ///debug method, for DOM tree consistency check, returns false if failed
    bool checkConsistency(bool requirePersistent);
#endif

    /// create formatted text object with options set
    LFormattedText* createFormattedText();

    void setHightlightOptions(text_highlight_options_t& options) {
        _highlightOptions = options;
    }
protected:
    struct DocFileHeader
    {
        lUInt32 render_dx;
        lUInt32 render_dy;
        lUInt32 render_docflags;
        lUInt32 render_style_hash;
        lUInt32 stylesheet_hash;
        lUInt32 node_displaystyle_hash;
        bool serialize(SerialBuf& buf);
        bool deserialize(SerialBuf& buf);
        DocFileHeader()
                : render_dx(0)
                , render_dy(0)
                , render_docflags(0)
                , render_style_hash(0)
                , stylesheet_hash(0)
                , node_displaystyle_hash(NODE_DISPLAY_STYLE_HASH_UNINITIALIZED) {
        }
    };
    DocFileHeader _hdr;
    text_highlight_options_t _highlightOptions;

    LDOMNameIdMap* _elementNameTable; // Element Name<->Id map
    LDOMNameIdMap* _attrNameTable;    // Attribute Name<->Id map
    LDOMNameIdMap* _nsNameTable;      // Namespace Name<->Id map
    lUInt16 _nextUnknownElementId;    // Next Id for unknown element
    lUInt16 _nextUnknownAttrId;       // Next Id for unknown attribute
    lUInt16 _nextUnknownNsId;         // Next Id for unknown namespace
    lString32HashedCollection _attrValueTable;
    LVHashTable<lUInt32, lInt32> _idNodeMap;               // id to data index map
    LVHashTable<lString32, LVImageSourceRef> _urlImageMap; // url to image source map
    lUInt16 _idAttrId;                                     // Id for "id" attribute name
    lUInt16 _nameAttrId;                                   // Id for "name" attribute name

    SerialBuf _pagesData;
};

#endif // __LXMLDOCBASE_H_INCLUDED__
