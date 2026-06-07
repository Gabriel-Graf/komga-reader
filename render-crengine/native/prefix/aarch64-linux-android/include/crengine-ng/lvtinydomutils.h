/***************************************************************************
 *   crengine-ng                                                           *
 *   Copyright (C) 2007,2008,2010 Vadim Lopatin <coolreader.org@gmail.com> *
 *   Copyright (C) 2020 poire-z <poire-z@users.noreply.github.com>         *
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

#ifndef __LVTINYDOMUTILS_H_INCLUDED__
#define __LVTINYDOMUTILS_H_INCLUDED__

#include <lvstream.h>
#include <dtddef.h>

class ldomDocument;

//utils
/// extract authors from FB2 document, delimiter is lString32 by default
lString32 extractDocAuthors(ldomDocument* doc, lString32 delimiter = lString32::empty_str, bool shortMiddleName = true);
lString32 extractDocTitle(ldomDocument* doc);
lString32 extractDocLanguage(ldomDocument* doc);
/// returns "(Series Name #number)" if pSeriesNumber is NULL, separate name and number otherwise
lString32 extractDocSeries(ldomDocument* doc, int* pSeriesNumber = NULL);
lString32 extractDocKeywords(ldomDocument* doc, lString32 delimiter = lString32::empty_str);
lString32 extractDocDescription(ldomDocument* doc);

/// parse XML document from stream, returns NULL if failed
ldomDocument* LVParseXMLStream(LVStreamRef stream,
                               const elem_def_t* elem_table = NULL,
                               const attr_def_t* attr_table = NULL,
                               const ns_def_t* ns_table = NULL);

/// parse XML document from stream, returns NULL if failed
ldomDocument* LVParseHTMLStream(LVStreamRef stream,
                                const elem_def_t* elem_table = NULL,
                                const attr_def_t* attr_table = NULL,
                                const ns_def_t* ns_table = NULL);

#endif // __LVTINYDOMUTILS_H_INCLUDED__
