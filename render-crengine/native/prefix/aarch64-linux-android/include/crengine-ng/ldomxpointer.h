/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2012 Vadim Lopatin <coolreader.org@gmail.com>      *
 *   Copyright (C) 2016 Yifei(Frank) ZHU <fredyifei@gmail.com>             *
 *   Copyright (C) 2018-2020 poire-z <poire-z@users.noreply.github.com>    *
 *   Copyright (C) 2020 Konstantin Potapov <pkbo@users.sourceforge.net>    *
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

#ifndef __LDOMXPOINTER_H_INCLUDED__
#define __LDOMXPOINTER_H_INCLUDED__

#include <lvtypes.h>
#include <lvstring.h>
#include <lvstring32collection.h>
#include <lxmldocbase.h>
#include <ldomnode.h>

class ldomDocument;

enum XPointerMode
{
    XPATH_USE_NAMES = 0,
    XPATH_USE_INDEXES
};

/**
 * @brief XPointer/XPath object with reference counting.
 *
 */
class ldomXPointer
{
protected:
    friend class ldomXPointerEx;
    struct XPointerData
    {
    protected:
        ldomDocument* _doc;
        lInt32 _dataIndex;
        int _offset;
        int _refCount;
    public:
        inline void addRef() {
            _refCount++;
        }
        inline int decRef() {
            return --_refCount;
        }
        // create empty
        XPointerData()
                : _doc(NULL)
                , _dataIndex(0)
                , _offset(0)
                , _refCount(1) { }
        // create instance
        XPointerData(ldomNode* node, int offset)
                : _doc(node ? node->getDocument() : NULL)
                , _dataIndex(node ? node->getDataIndex() : 0)
                , _offset(offset)
                , _refCount(1) { }
        // clone
        XPointerData(const XPointerData& v)
                : _doc(v._doc)
                , _dataIndex(v._dataIndex)
                , _offset(v._offset)
                , _refCount(1) { }
        inline ldomDocument* getDocument() const {
            return _doc;
        }
        inline bool operator==(const XPointerData& v) const {
            return _doc == v._doc && _dataIndex == v._dataIndex && _offset == v._offset;
        }
        inline bool operator!=(const XPointerData& v) const {
            return _doc != v._doc || _dataIndex != v._dataIndex || _offset != v._offset;
        }
        inline bool isNull() const {
            return _dataIndex == 0 || _doc == NULL;
        }
        inline ldomNode* getNode() const {
            return _dataIndex > 0 ? ((lxmlDocBase*)_doc)->getTinyNode(_dataIndex) : NULL;
        }
        inline int getOffset() const {
            return _offset;
        }
        inline void setNode(ldomNode* node) {
            if (node) {
                _doc = node->getDocument();
                _dataIndex = node->getDataIndex();
            } else {
                _doc = NULL;
                _dataIndex = 0;
            }
        }
        inline void setOffset(int offset) {
            _offset = offset;
        }
        inline void addOffset(int offset) {
            _offset += offset;
        }
        ~XPointerData() { }
    };
    XPointerData* _data;
    /// node pointer
    //ldomNode * _node;
    /// offset within node for pointer, -1 for xpath
    //int _offset;
    // cloning constructor
    ldomXPointer(const XPointerData* data)
            : _data(new XPointerData(*data)) {
    }
public:
    /// clear pointer (make null)
    void clear() {
        if (_data->decRef() == 0)
            delete _data;
        _data = new XPointerData();
    }
    /// return document
    inline ldomDocument* getDocument() const {
        return _data->getDocument();
    }
    /// returns node pointer
    inline ldomNode* getNode() const {
        return _data->getNode();
    }
    /// return parent final node, if found
    ldomNode* getFinalNode() const;
    /// return true is this node is a final node
    bool isFinalNode() const;
    /// returns offset within node
    inline int getOffset() const {
        return _data->getOffset();
    }
    /// set pointer node
    inline void setNode(ldomNode* node) {
        _data->setNode(node);
    }
    /// set pointer offset within node
    inline void setOffset(int offset) {
        _data->setOffset(offset);
    }
    /// default constructor makes NULL pointer
    ldomXPointer()
            : _data(new XPointerData()) {
    }
    /// remove reference
    ~ldomXPointer() {
        if (_data->decRef() == 0)
            delete _data;
    }
    /// copy constructor
    ldomXPointer(const ldomXPointer& v)
            : _data(v._data) {
        _data->addRef();
    }
    /// assignment operator
    ldomXPointer& operator=(const ldomXPointer& v) {
        if (_data == v._data)
            return *this;
        if (_data->decRef() == 0)
            delete _data;
        _data = v._data;
        _data->addRef();
        return *this;
    }
    /// constructor
    ldomXPointer(ldomNode* node, int offset)
            : _data(new XPointerData(node, offset)) {
    }
    /// get pointer for relative path
    ldomXPointer relative(lString32 relativePath);
    /// get pointer for relative path
    ldomXPointer relative(const lChar32* relativePath) {
        return relative(lString32(relativePath));
    }

    /// returns true for NULL pointer
    bool isNull() const {
        return !_data || _data->isNull();
    }
    /// returns true if object is pointer
    bool isPointer() const {
        return !_data->isNull() && getOffset() >= 0;
    }
    /// returns true if object is path (no offset specified)
    bool isPath() const {
        return !_data->isNull() && getOffset() == -1;
    }
    /// returns true if pointer is NULL
    bool operator!() const {
        return _data->isNull();
    }
    /// returns true if pointers are equal
    bool operator==(const ldomXPointer& v) const {
        return *_data == *v._data;
    }
    /// returns true if pointers are not equal
    bool operator!=(const ldomXPointer& v) const {
        return *_data != *v._data;
    }
    /// returns caret rectangle for pointer inside formatted document
    bool getRect(lvRect& rect, bool extended = false, bool adjusted = false) const;
    /// returns glyph rectangle for pointer inside formatted document considering paddings and borders
    /// (with adjusted=true, adjust for left and right side bearing of the glyph, for cleaner highlighting)
    bool getRectEx(lvRect& rect, bool adjusted = false) const {
        return getRect(rect, true, adjusted);
    }
    /// returns coordinates of pointer inside formatted document
    lvPoint toPoint(bool extended = false) const;
    /// converts to string
    lString32 toString(XPointerMode mode = XPATH_USE_NAMES) const;
    lString32 toStringV1() const;          // Using names, old, with boxing elements (non-normalized)
    lString32 toStringV2() const;          // Using names, new, without boxing elements, so: normalized
    lString32 toStringV2AsIndexes() const; // Without element names, normalized (not used)

    /// returns XPath node text
    lString32 getText(lChar32 blockDelimiter = 0) const {
        ldomNode* node = getNode();
        if (!node)
            return lString32::empty_str;
        return node->getText(blockDelimiter);
    }
    /// returns href attribute of <A> element, null string if not found
    lString32 getHRef() const;
    /// returns href attribute of <A> element, plus xpointer of <A> element itself
    lString32 getHRef(ldomXPointer& a_xpointer) const;
    /// create a copy of pointer data
    ldomXPointer* clone() const {
        return new ldomXPointer(_data);
    }
    /// returns true if current node is element
    inline bool isElement() const {
        return !isNull() && getNode()->isElement();
    }
    /// returns true if current node is element
    inline bool isText() const {
        return !isNull() && getNode()->isText();
    }
    /// returns HTML (serialized from the DOM, may be different from the source HTML)
    lString8 getHtml(lString32Collection& cssFiles, int wflags = 0) const;
    lString8 getHtml(int wflags = 0) const {
        lString32Collection cssFiles;
        return getHtml(cssFiles, wflags);
    }
};

#endif // __LDOMXPOINTER_H_INCLUDED__
