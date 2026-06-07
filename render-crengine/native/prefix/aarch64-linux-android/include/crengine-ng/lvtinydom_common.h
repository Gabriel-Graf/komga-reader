/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007,2009-2011 Vadim Lopatin <coolreader.org@gmail.com> *
 *   Copyright (C) 2018,2020 poire-z <poire-z@users.noreply.github.com>    *
 *   Copyright (C) 2020 Konstantin Potapov <pkbo@users.sourceforge.net>    *
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

#ifndef __LV_TINYDOM_COMMON_H_INCLUDED__
#define __LV_TINYDOM_COMMON_H_INCLUDED__

#include <lvtypes.h>

#define BLOB_NAME_PREFIX       U"@blob#"
#define MOBI_IMAGE_NAME_PREFIX U"mobi_image_"

#define LXML_NO_DATA      0 ///< to mark data storage record as empty
#define LXML_ELEMENT_NODE 1 ///< element node
#define LXML_TEXT_NODE    2 ///< text node
//#define LXML_DOCUMENT_NODE 3 ///< document node (not implemented)
//#define LXML_COMMENT_NODE  4 ///< comment node (not implemented)

#define LXML_NS_NONE         0          ///< no namespace specified
#define LXML_NS_ANY          0xFFFF     ///< any namespace can be specified
#define LXML_ATTR_VALUE_NONE 0xFFFFFFFF ///< attribute not found

#define NODE_DISPLAY_STYLE_HASH_UNINITIALIZED 0xFFFFFFFF

// Changes:
// 20100101 to 20180502: historical version
//
// 20180503: fixed <BR> that were previously converted to <P> because
// of a fix for "bad LIB.RU books" being applied in any case. This
// could prevent the style of the container to be applied on HTML
// sub-parts after a <BR>, or the style of <P> (with text-indent) to
// be applied after a <BR>.
//
// 20180524: changed default rendering of:
//   <li> (and css 'display:list-item') from css_d_list_item_legacy to css_d_list_item_block
//   <cite> from css_d_block to css_d_inline (inline in HTML, block in FB2, ensured by fb2.css)
//   <style> from css_d_inline to css_d_none (invisible in HTML)
// Changed also the default display: value for base elements (and so
// for unknown elements) from css_d_inherit to css_d_inline, and disable
// inheritance for the display: property, as per specs.
// See https://developer.mozilla.org/en-US/docs/Web/CSS/display
// (Initial value: inline; Inherited: no)
//
// 20180528: clean epub.css from class name based declarations
//   added support for style property -cr-ignore-if-dom-version-greater-or-equal: 20180528;
//   to ignore the whole declaration with newer gDOMVersionRequested.
//   Use it to keep class name based declarations that involve display:
//   so to not break previous DOM
// Also: fb2def.h updates
//   Changed some elements from XS_TAG1 to XS_TAG1T (<hr>, <ul>, <ol>,
//   <dl>, <output>, <section>, <svg>), so any text node direct child is
//   now displayed instead of just being dropped (all browsers do display
//   such child text nodes).
//   Also no more hide the <form> element content, as it may contain
//   textual information.
//   Also change <code> from 'white-space: pre' to 'normal', like browsers do
//   Added missing block elements from HTML specs so they are correctly
//   displayed as 'block' instead of the new default of 'inline'.
//
// (20190703: added support for CSS float: and clear: which may
// insert <floatBox> elements in the DOM tree. Bus as this is
// toggable, and legacy rendering is available, no need to limit
// their support to some DOM_VERSION. So no bump needed.)
//
// (20200110: added support for CSS display: inline-block and inline-table,
// which may insert <inlineBox> elements in the DOM tree. Bus as this is
// toggable, and legacy rendering is available, no need to limit
// their support to some DOM_VERSION. So no bump needed.)
//
// 20200223: normalized XPointers/XPATHs, by using createXPointerV2()
// and toStringV2(), that should ensure XPointers survive changes
// in style->display and the insertion or removal of autoBoxing,
// floatBox and inlineBox.
// (Older gDOMVersionRequested will keep using createXPointerV1()
// and toStringV1() to have non-normalized XPointers still working.)
// (20200223: added toggable auto completion of incomplete tables by
// wrapping some elements in a new <tabularBox>.)
//
// 20200824: added more HTML5 elements, and HTML parser changes
// to be (only a little bit) more HTML5 conformant

// Change this in case of incompatible changes in XML parsing or DOM
// building that could result in XPATHs being different than previously
// (this could make saved bookmarks and highlights, made with a previous
// version, not found in the DOM built with a newer version.
// Users of this library can request the old behaviour by setting
// gDOMVersionRequested to an older version to request the old (possibly
// buggy) behaviour.
#define DOM_VERSION_CURRENT 20200824

#define DOM_VERSION_WITH_NORMALIZED_XPOINTERS 20200223

struct ldomNodeStyleInfo
{
    lUInt16 _fontIndex;
    lUInt16 _styleIndex;
};

// Allows for requesting older DOM building code (including bugs NOT fixed)
extern const int gDOMVersionCurrent;

#endif // __LV_TINYDOM_COMMON_H_INCLUDED__
