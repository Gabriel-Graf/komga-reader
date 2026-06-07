/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007,2009,2010 Vadim Lopatin <coolreader.org@gmail.com> *
 *   Copyright (C) 2018-2020 poire-z <poire-z@users.noreply.github.com>    *
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

#ifndef __LVTOCITEM_H_INCLUDED__
#define __LVTOCITEM_H_INCLUDED__

#include <ldomxpointer.h>

/// TOC item
class LVTocItem
{
    friend class LVDocView;
private:
    LVTocItem* _parent;
    ldomDocument* _doc;
    lInt32 _level;
    lInt32 _index;
    lInt32 _page;
    lInt32 _percent;
    lString32 _name;
    lString32 _path;
    ldomXPointer _position;
    LVPtrVector<LVTocItem> _children;
    //====================================================
    //LVTocItem( ldomXPointer pos, const lString32 & name ) : _parent(NULL), _level(0), _index(0), _page(0), _percent(0), _name(name), _path(pos.toString()), _position(pos) { }
    LVTocItem(ldomXPointer pos, lString32 path, const lString32& name)
            : _parent(NULL)
            , _level(0)
            , _index(0)
            , _page(0)
            , _percent(0)
            , _name(name)
            , _path(path)
            , _position(pos) { }
    void addChild(LVTocItem* item);
    //====================================================
    void setPage(int n) {
        _page = n;
    }
    void setPercent(int n) {
        _percent = n;
    }
public:
    /// serialize to byte array (pointer will be incremented by number of bytes written)
    bool serialize(SerialBuf& buf);
    /// deserialize from byte array (pointer will be incremented by number of bytes read)
    bool deserialize(ldomDocument* doc, SerialBuf& buf);
    /// get page number
    int getPage() {
        return _page;
    }
    /// get position percent * 100
    int getPercent() {
        return _percent;
    }
    /// returns parent node pointer
    LVTocItem* getParent() const {
        return _parent;
    }
    /// returns node level (0==root node, 1==top level)
    int getLevel() const {
        return _level;
    }
    /// returns node index
    int getIndex() const {
        return _index;
    }
    /// returns section title
    lString32 getName() const {
        return _name;
    }
    /// returns position pointer
    ldomXPointer getXPointer();
    /// set position pointer (for cases where we need to create a LVTocItem as a container, but
    /// we'll know the xpointer only later, mostly always the same xpointer as its first child)
    void setXPointer(ldomXPointer xp) {
        _position = xp;
    }
    /// returns position path
    lString32 getPath();
    /// returns Y position
    int getY();
    /// returns page number
    //int getPageNum( LVRendPageList & pages );
    /// returns child node count
    int getChildCount() const {
        return _children.length();
    }
    /// returns child node by index
    LVTocItem* getChild(int index) const {
        return _children[index];
    }
    /// add child TOC node
    LVTocItem* addChild(const lString32& name, ldomXPointer ptr, lString32 path) {
        LVTocItem* item = new LVTocItem(ptr, path, name);
        addChild(item);
        return item;
    }
    void clear() {
        _children.clear();
    }
    // root node constructor
    LVTocItem(ldomDocument* doc)
            : _parent(NULL)
            , _doc(doc)
            , _level(0)
            , _index(0)
            , _page(0) { }
    ~LVTocItem() {
        clear();
    }

    /// For use on the root toc item only (_page, otherwise unused, can be used to store this flag)
    void setAlternativeTocFlag() {
        if (_level == 0)
            _page = 1;
    }
    bool hasAlternativeTocFlag() {
        return _level == 0 && _page == 1;
    }

    /// When page numbers have been calculated, LVDocView::updatePageNumbers()
    /// sets the root toc item _percent to -1. So let's use it to know that fact.
    bool hasValidPageNumbers() {
        return _level == 0 && _percent == -1;
    }
    void invalidatePageNumbers() {
        if (_level == 0)
            _percent = 0;
    }
};

#endif // __LVTOCITEM_H_INCLUDED__
