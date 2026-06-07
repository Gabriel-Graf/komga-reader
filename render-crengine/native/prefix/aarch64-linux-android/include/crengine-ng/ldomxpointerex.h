/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007-2009,2011,2014 Vadim Lopatin <coolreader.org@gmail.com>
 *   Copyright (C) 2018,2019 poire-z <poire-z@users.noreply.github.com>    *
 *   Copyright (C) 2018,2020,2021 Aleksey Chernov <valexlin@gmail.com>     *
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

#ifndef __LDOMXPOINTEREX_H_INCLUDED__
#define __LDOMXPOINTEREX_H_INCLUDED__

#include <ldomxpointer.h>

#define MAX_DOM_LEVEL 64
/// Xpointer optimized to iterate through DOM tree
class ldomXPointerEx: public ldomXPointer
{
protected:
    int _indexes[MAX_DOM_LEVEL];
    int _level;
    void initIndex();
public:
    /// returns bottom level index
    int getIndex() {
        return _indexes[_level - 1];
    }
    /// returns node level
    int getLevel() {
        return _level;
    }
    /// default constructor
    ldomXPointerEx()
            : ldomXPointer() {
        initIndex();
    }
    /// constructor by node pointer and offset
    ldomXPointerEx(ldomNode* node, int offset)
            : ldomXPointer(node, offset) {
        initIndex();
    }
    /// copy constructor
    ldomXPointerEx(const ldomXPointer& v)
            : ldomXPointer(v._data) {
        initIndex();
    }
    /// copy constructor
    ldomXPointerEx(const ldomXPointerEx& v)
            : ldomXPointer(v._data) {
        _level = v._level;
        for (int i = 0; i < _level; i++)
            _indexes[i] = v._indexes[i];
    }
    /// assignment operator
    ldomXPointerEx& operator=(const ldomXPointer& v) {
        if (_data == v._data)
            return *this;
        if (_data->decRef() == 0)
            delete _data;
        _data = new XPointerData(*v._data);
        initIndex();
        return *this;
    }
    /// assignment operator
    ldomXPointerEx& operator=(const ldomXPointerEx& v) {
        if (_data == v._data)
            return *this;
        if (_data->decRef() == 0)
            delete _data;
        _data = new XPointerData(*v._data);
        _level = v._level;
        for (int i = 0; i < _level; i++)
            _indexes[i] = v._indexes[i];
        return *this;
    }
    /// returns true if ranges are equal
    bool operator==(const ldomXPointerEx& v) const {
        return _data->getDocument() == v._data->getDocument() && _data->getNode() == v._data->getNode() && _data->getOffset() == v._data->getOffset();
    }
    /// searches path for element with specific id, returns level at which element is founs, 0 if not found
    int findElementInPath(lUInt16 id);
    /// compare two pointers, returns -1, 0, +1
    int compare(const ldomXPointerEx& v) const;
    /// move to next sibling
    bool nextSibling();
    /// move to previous sibling
    bool prevSibling();
    /// move to next sibling element
    bool nextSiblingElement();
    /// move to previous sibling element
    bool prevSiblingElement();
    /// move to parent
    bool parent();
    /// move to first child of current node
    bool firstChild();
    /// move to last child of current node
    bool lastChild();
    /// move to first element child of current node
    bool firstElementChild();
    /// move to last element child of current node
    bool lastElementChild();
    /// move to child #
    bool child(int index);
    /// move to sibling #
    bool sibling(int index);
    /// ensure that current node is element (move to parent, if not - from text node to element)
    bool ensureElement();
    /// moves pointer to parent element with FINAL render method, returns true if success
    bool ensureFinal();
    /// returns true if current node is visible element with render method == erm_final
    bool isVisibleFinal();
    /// move to next final visible node (~paragraph)
    bool nextVisibleFinal();
    /// move to previous final visible node (~paragraph)
    bool prevVisibleFinal();
    /// returns true if current node is visible element or text
    bool isVisible();
    // returns true if text node char at offset is part of a word
    bool isVisibleWordChar();
    /// move to next text node
    bool nextText(bool thisBlockOnly = false);
    /// move to previous text node
    bool prevText(bool thisBlockOnly = false);
    /// move to next visible text node
    bool nextVisibleText(bool thisBlockOnly = false);
    /// move to previous visible text node
    bool prevVisibleText(bool thisBlockOnly = false);

    /// move to prev visible char
    bool prevVisibleChar(bool thisBlockOnly = false);
    /// move to next visible char
    bool nextVisibleChar(bool thisBlockOnly = false);

    /// move to previous visible word beginning
    bool prevVisibleWordStart(bool thisBlockOnly = false);
    /// move to previous visible word end
    bool prevVisibleWordEnd(bool thisBlockOnly = false);
    /// move to next visible word beginning
    bool nextVisibleWordStart(bool thisBlockOnly = false);
    /// move to end of current word
    bool thisVisibleWordEnd(bool thisBlockOnly = false);
    /// move to next visible word end
    bool nextVisibleWordEnd(bool thisBlockOnly = false);

    /// move to previous visible word beginning (in sentence)
    bool prevVisibleWordStartInSentence(bool thisBlockOnly);
    /// move to previous visible word end (in sentence)
    bool prevVisibleWordEndInSentence(bool thisBlockOnly);
    /// move to next visible word beginning (in sentence)
    bool nextVisibleWordStartInSentence(bool thisBlockOnly);
    /// move to end of current word (in sentence)
    bool thisVisibleWordEndInSentence();
    /// move to next visible word end (in sentence)
    bool nextVisibleWordEndInSentence(bool thisBlockOnly);

    /// move to beginning of current visible text sentence
    bool thisSentenceStart();
    /// move to end of current visible text sentence
    bool thisSentenceEnd();
    /// move to beginning of next visible text sentence
    bool nextSentenceStart();
    /// move to beginning of next visible text sentence
    bool prevSentenceStart();
    /// move to end of next visible text sentence
    bool nextSentenceEnd();
    /// move to end of prev visible text sentence
    bool prevSentenceEnd();
    /// returns true if points to beginning of sentence
    bool isSentenceStart();
    /// returns true if points to end of sentence
    bool isSentenceEnd();

    /// returns true if points to last visible text inside block element
    bool isLastVisibleTextInBlock();
    /// returns true if points to first visible text inside block element
    bool isFirstVisibleTextInBlock();

    /// returns block owner node of current node (or current node if it's block)
    ldomNode* getThisBlockNode();

    /// returns true if current position is visible word beginning
    bool isVisibleWordStart();
    /// returns true if current position is visible word end
    bool isVisibleWordEnd();
    /// forward iteration by elements of DOM three
    bool nextElement();
    /// backward iteration by elements of DOM three
    bool prevElement();
    /// calls specified function recursively for all elements of DOM tree
    void recurseElements(void (*pFun)(ldomXPointerEx& node));
    /// calls specified function recursively for all nodes of DOM tree
    void recurseNodes(void (*pFun)(ldomXPointerEx& node));

    /// move to next sibling or parent's next sibling
    bool nextOuterElement();
    /// move to (end of) last and deepest child node descendant of current node
    bool lastInnerNode(bool toTextEnd = false);
    /// move to (end of) last and deepest child text node descendant of current node
    bool lastInnerTextNode(bool toTextEnd = false);
};

#endif // __LDOMXPOINTEREX_H_INCLUDED__
