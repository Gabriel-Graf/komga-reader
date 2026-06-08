#!/bin/sh

###########################################################################
#   LxReader, third party libs builder                                    #
#   Copyright (C) 2024-2026 Aleksey Chernov <valexlin@gmail.com>          #
#                                                                         #
#   This program is free software: you can redistribute it and/or modify  #
#   it under the terms of the GNU General Public License as published by  #
#   the Free Software Foundation, either version 3 of the License, or     #
#   (at your option) any later version.                                   #
#                                                                         #
#   This program is distributed in the hope that it will be useful,       #
#   but WITHOUT ANY WARRANTY; without even the implied warranty of        #
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         #
#   GNU General Public License for more details.                          #
#                                                                         #
#   You should have received a copy of the GNU General Public License     #
#   along with this program.  If not, see <https://www.gnu.org/licenses/>.#
###########################################################################

# To build android shared library, make sure that the "-fPIC" option is added to make.conf in CFLAGS.

die()
{
	echo $*
	exit 1
}

arch_list="aarch64-linux-android arm-linux-androideabi x86_64-linux-android i686-linux-android"
pkg_list="libpng libjpeg-turbo libwebp freetype-stage0 harfbuzz freetype fribidi libunibreak utf8proc zstd"

# cleanup
for arch in ${arch_list}
do
	echo "Calling ./build.sh $arch ${pkg_list} --clean"
	./build.sh $arch ${pkg_list} --clean || die "cleanup failed"
done

# build
for arch in ${arch_list}
do
	echo "Calling ./build.sh $arch ${pkg_list}"
	./build.sh $arch ${pkg_list} || die "build failed"
done
