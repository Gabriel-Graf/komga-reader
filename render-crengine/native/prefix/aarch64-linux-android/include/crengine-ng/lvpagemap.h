/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2020 poire-z <poire-z@users.noreply.github.com>         *
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

#ifndef __LVPAGEMAP_H_INCLUDED__
#define __LVPAGEMAP_H_INCLUDED__

#include <ldomxpointer.h>

/// PageMapItem
class LVPageMapItem
{
    friend class LVDocView;
    friend class LVPageMap;
private:
    ldomDocument* _doc;
    lInt32 _index;
    lInt32 _page;
    lInt32 _doc_y;
    lString32 _label;
    lString32 _path;
    ldomXPointer _position;
    LVPageMapItem(ldomXPointer pos, lString32 path, const lString32& label)
            : _index(0)
            , _page(0)
            , _doc_y(-1)
            , _label(label)
            , _path(path)
            , _position(pos) { }
    void setPage(int n) {
        _page = n;
    }
    void setDocY(int y) {
        _doc_y = y;
    }
public:
    /// serialize to byte array (pointer will be incremented by number of bytes written)
    bool serialize(SerialBuf& buf);
    /// deserialize from byte array (pointer will be incremented by number of bytes read)
    bool deserialize(ldomDocument* doc, SerialBuf& buf);
    /// get rendered page number
    int getPage() {
        return _page;
    }
    /// returns node index
    int getIndex() const {
        return _index;
    }
    /// returns page label
    lString32 getLabel() const {
        return _label;
    }
    /// returns position pointer
    ldomXPointer getXPointer();
    /// returns position path
    lString32 getPath();
    /// returns Y position
    int getDocY(bool refresh = false);
    LVPageMapItem(ldomDocument* doc)
            : _doc(doc)
            , _index(0)
            , _page(0)
            , _doc_y(-1) { }
};

/// PageMapItems container
class LVPageMap
{
    friend class LVDocView;
private:
    ldomDocument* _doc;
    bool _page_info_valid;
    lString32 _source;
    LVPtrVector<LVPageMapItem> _children;
    void addPage(LVPageMapItem* item) {
        item->_doc = _doc;
        item->_index = _children.length();
        _children.add(item);
    }
public:
    /// serialize to byte array (pointer will be incremented by number of bytes written)
    bool serialize(SerialBuf& buf);
    /// deserialize from byte array (pointer will be incremented by number of bytes read)
    bool deserialize(ldomDocument* doc, SerialBuf& buf);
    /// returns child node count
    int getChildCount() const {
        return _children.length();
    }
    /// returns child node by index
    LVPageMapItem* getChild(int index) const {
        return _children[index];
    }
    /// add page item
    LVPageMapItem* addPage(const lString32& label, ldomXPointer ptr, lString32 path) {
        LVPageMapItem* item = new LVPageMapItem(ptr, path, label);
        addPage(item);
        return item;
    }
    void clear() {
        _children.clear();
    }
    bool hasValidPageInfo() {
        return _page_info_valid;
    }
    void invalidatePageInfo() {
        _page_info_valid = false;
    }
    // Page source (info about the book paper version the page labels reference)
    void setSource(lString32 source) {
        _source = source;
    }
    lString32 getSource() const {
        return _source;
    }
    // root node constructor
    LVPageMap(ldomDocument* doc)
            : _doc(doc)
            , _page_info_valid(false) { }
    ~LVPageMap() {
        clear();
    }
};

#endif // __LVPAGEMAP_H_INCLUDED__
