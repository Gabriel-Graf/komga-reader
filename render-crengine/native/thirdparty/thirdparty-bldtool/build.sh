#!/bin/bash

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

#set -x

top_srcdir=`pwd`

die()
{
    echo $*
    cd "${pwd1}"
    exit 1
}

args_count=$#
if [ "$#" -lt 2 ]
then
	echo "Usage: $0 <target> <package1> [<package2> .. <package_n>]"
	echo "For example:"
	echo "  $0 aarch64-linux-android libpng libjpeg-turbo libwebp freetype-stage0 harfbuzz freetype fribidi libunibreak utf8proc zstd"
	echo "  $0 arm-linux-androideabi libpng libjpeg-turbo libwebp freetype-stage0 harfbuzz freetype fribidi libunibreak utf8proc zstd"
	echo "  $0 x86_64-linux-android libpng libjpeg-turbo libwebp freetype-stage0 harfbuzz freetype fribidi libunibreak utf8proc zstd"
	echo "  $0 i686-linux-android libpng libjpeg-turbo libwebp freetype-stage0 harfbuzz freetype fribidi libunibreak utf8proc zstd"
	echo ""
	echo "  Clean mode:"
	echo "    $0 <target> --clean"
	exit 1
fi

clean_mode=no
verbose_mode=no
spec_target=$1
spec_target_ok=no
shift
case "${spec_target}" in
*-*-*)
	spec_target_ok=yes
	;;
esac
if [ "x${spec_target_ok}" = "xno" ]
then
	echo "Omitted or invalid target: ${spec_target}!"
	exit 1
fi

packages=
while [ -n "$1" ]
do
	if [ "x$1" == "x--clean" ]
	then
		clean_mode=yes
	elif [ "x$1" == "x--verbose" -o "x$1" == "x-V" ]
	then
		verbose_mode=yes
	else
		packages="${packages} ${1}"
	fi
	shift
done

if [ "x${packages}" = "x" ]
then
	packages="zlib libpng libjpeg-turbo libwebp freetype-stage0 harfbuzz freetype fribidi libunibreak zstd utf8proc"
fi

thirdparty_dir="${top_srcdir}/sources"
repo_dir="${top_srcdir}/repo"
repo_tmpdir="${top_srcdir}/tmp"

_get_tar_args()
{
	local args=
	case "${1}" in
	*.tar.gz)
		args="z"
		;;
	*.tar.bz2)
		args="j"
		;;
	*.tar.xz)
		args="J"
		;;
	esac
	echo "${args}"
}

_var_value_is_true()
{
	local ret=1
	case "${1}" in
	"yes"|"YES"|"y"|"Y")
		ret=0
		;;
	"true"|"TRUE"|"t"|"T")
		ret=0
		;;
	"on"|"ON")
		ret=0
		;;
	esac
	return $ret
}

# 'make' wrapper
domake()
{
	local __cmake_verbose_arg=
	local __make_verbose_arg=
	if [ "x${verbose_mode}" = "xyes" ]
	then
		__cmake_verbose_arg="--verbose"
		__make_verbose_arg="VERBOSE=1 V=1"
	fi
	if [ -f CMakeCache.txt ]
	then
		cmake --build . ${MAKEOPTS} ${__cmake_verbose_arg} $*
	else
		if [ "x${SHELL_REWRITE_NEED}" = "xyes" ]
		then
			make ${MAKEOPTS} SHELL=${SHELL} ${__make_verbose_arg} $*
		else
			make ${MAKEOPTS} ${__make_verbose_arg} $*
		fi
	fi
}

domake_install()
{
	local __cmake_verbose_arg=
	local __make_verbose_arg=
	if [ "x${verbose_mode}" = "xyes" ]
	then
		__cmake_verbose_arg="--verbose"
		__make_verbose_arg="VERBOSE=1 V=1"
	fi
	if [ -f CMakeCache.txt ]
	then
		cmake --install . ${__cmake_verbose_arg} $*
	else
		if [ "x${SHELL_REWRITE_NEED}" = "xyes" ]
		then
			make install SHELL=${SHELL} ${__make_verbose_arg} $*
		else
			make install ${__make_verbose_arg} $*
		fi
	fi
}

strip_files()
{
	local dir=$1
	local strip_exe=
	local strip_args=
	local have_strip=
	case "${spec_target}" in
		*-linux-android*)
			strip_exe="${ANDROID_TOOLCHAIN_PATH_BIN}/${spec_target}-strip"
			strip_args="--strip-debug"
			;;
		*)
			strip_exe="strip"
			strip_args="--strip-debug"
			;;
	esac
	which ${strip_exe} > /dev/null 2>&1 && have_strip=yes
	if [ "x${have_strip}" != "xyes" ]
	then
		case "${spec_target}" in
			*-linux-android*)
				strip_exe="${ANDROID_TOOLCHAIN_PATH_BIN}/llvm-objcopy"
				strip_args="--strip-debug"
				;;
		esac
	fi
	which ${strip_exe} > /dev/null 2>&1 && have_strip=yes
	if [ "x${have_strip}" != "xyes" ]
	then
		echo "I'm sorry, 'strip' tool not found, skipping..."
		return 0
	fi
	local files_to_strip=
	# Find executables
	local bin_files_all=`find ${dir}/bin/ 2>/dev/null`
	local file_output=
	for f in ${bin_files_all}
	do
		file_output=`file $f 2>/dev/null`
		if echo $file_output | grep "ELF" 2>&1 >/dev/null
		then
			files_to_strip="${files_to_strip} $f"
		fi
		if echo $file_output | grep "Mach-O" 2>&1 >/dev/null
		then
			files_to_strip="${files_to_strip} $f"
		fi
	done
	# Find libraries
	local lib_files_all=`find ${dir}/lib/ ${dir}/lib64/ 2>/dev/null`
	for f in ${lib_files_all}
	do
		file_output=`file $f 2>/dev/null`
		if echo $file_output | grep "ar archive" 2>&1 >/dev/null
		then
			files_to_strip="${files_to_strip} $f"
		fi
		if echo $file_output | grep "ELF" 2>&1 >/dev/null
		then
			files_to_strip="${files_to_strip} $f"
		fi
		if echo $file_output | grep "Mach-O" 2>&1 >/dev/null
		then
			files_to_strip="${files_to_strip} $f"
		fi
	done
	if [ "x${files_to_strip}" != "x" ]
	then
		echo "Strip debug symbols:"
		for f in ${files_to_strip}
		do
			echo "  $f"
			$strip_exe $strip_args "$f"
		done
	fi
}

deploy_package()
{
	local pkgname="${1}"
	local metafile="${repo_dir}/${pkgname}.meta.sh"

	source "${metafile}" || die "no such package!"

	local patchesdir="${repo_dir}/patches/${PN}"
	local pkg_datadir="${repo_tmpdir}/${pkgname}"

	local force_update=n
	local have_ninja=n
	local pwd1=`pwd`
	local path_saved="${PATH}"

	if [ ! -d "${pkg_datadir}" ]
	then
		mkdir "${pkg_datadir}" || die "Failed to create package tmpdir!"
	fi
	cd "${pkg_datadir}" || die "chdir failed!"

	# Check consistency
	if [ -f .downloaded -o -f .verified  ]
	then
		if [ ! -f "${SRCFILE}" ]
		then
			echo "Source file \"${SRCFILE}\" not found!"
			echo "Perhaps the ${pkgname} package has been updated..."
			rm -f .downloaded .verified
			force_update=y
		fi
	fi
	if [ -f .prepared ]
	then
		local pkg_rev=`cat .prepared 2>/dev/null`
		test "x${pkg_rev}" = "x" && pkg_rev=0
		if [ "x${pkg_rev}" != "x${REV}" ]
		then
			echo "The existing source package is older."
			echo -n "Cleaning it... "
			rm -rf "${thirdparty_dir}/${SOURCESDIR}" >/dev/null 2>&1 || die "rm (dir) failed!"
			force_update=y
			echo "done"
		fi
	fi

	if [ ! -d "${thirdparty_dir}/${SOURCESDIR}" ]
	then
		force_update=y
	fi

	if [ ! -f .downloaded ]
	then
		if [ -f "${SRCFILE}" ]
		then
			rm -f "${SRCFILE}"
		fi
		curl -f -L -O ${URL} || die "Failed to fetch sources!"
		if [ ! -f "${SRCFILE}" ]
		then
			die "Something wrong... source file not found!"
		fi
		echo "1" > .downloaded
		echo "Downloaded OK."
	fi

	if [ ! -f .verified ]
	then
		# make sha512 sum file
		echo "${SHA512} *${SRCFILE}" > "${SRCFILE}.sha512"
		shasum -c "${SRCFILE}.sha512" || if [ "x" = "x" ]; then rm -f .downloaded; rm -f "${SRCFILE}.sha512"; die "Failed to verify checksum!"; fi
		echo "1" > .verified
		rm -f "${SRCFILE}.sha512"
		echo "Checksum OK."
	fi

	local tar_args=`_get_tar_args "${SRCFILE}"`
	if [ ! -f .unpacked -o "x${force_update}" = "xy" ]
	then
		cd "${thirdparty_dir}" || die "chdir to thirdparty_dir failed!"
		tar -x${tar_args}f "${pkg_datadir}/${SRCFILE}"
		local tar_ret=$?
		if [ "x$IGNORE_TAR_ERRORS" != "xy" -a "x$IGNORE_TAR_ERRORS" != "xyes" ]
		then
			test $tar_ret != 0 && die "Failed to unpack sources!"
		fi
		cd "${pkg_datadir}" || die "chdir failed!"
		echo "1" > .unpacked
		echo "Unpacked OK."
	fi

	if [ ! -f .prepared -o "x${force_update}" = "xy"  ]
	then
		cd "${thirdparty_dir}/${SOURCESDIR}" || die "chdir to srcdir failed!"
		for p in ${PATCHES}
		do
			patch -p1 -i "${patchesdir}/${p}" || die "Failed to patch!"
		done
		cd "${pkg_datadir}" || die "chdir failed!"
		echo "${REV}" > .prepared
		echo "Prepared OK."
	fi

	export BUILDDIR="${thirdparty_dir}/${pkgname}-build-${spec_target}"
	if [ "x${force_update}" = "xy" ]
	then
		rm -rf "${BUILDDIR}" || die "rm failed"
		rm -rf "${spec_target}"
	fi
	mkdir -p "${BUILDDIR}" || die "mkdir failed"
	mkdir -p "${spec_target}" || die "mkdir failed!"

	# Specific platform build options
	local add_cflags=
	local add_ldflags=

	CMAKE_GENERATOR=
	CMAKE_ADD_ARGS=
	if [ -z "${ANDROID_NDK_ROOT}" ]
	then
		if [ -z "${ANDROID_HOME}" ]
		then
			echo "ANDROID_HOME is not defined!"
			die "abort"
		fi
		if [ ! -d "${ANDROID_HOME}" ]
		then
			echo "Path in ANDROID_HOME is not exist!"
			echo "ANDROID_HOME=${ANDROID_HOME}"
			die "abort"
		fi
		ANDROID_NDK_ROOT="${ANDROID_HOME}/ndk/21.4.7075529"
	else
		if [ ! -d "${ANDROID_NDK_ROOT}" ]
		then
			echo "Path in ${ANDROID_NDK_ROOT} is not exist!"
			echo "ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT}"
			die "abort"
		fi
	fi
	if [ -z "${ANDROID_CMAKE}" ]
	then
		ANDROID_CMAKE="${ANDROID_HOME}/cmake/3.22.1"
	fi
	ANDROID_TOOLCHAIN_PATH_BIN="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${ANDROID_NDK_HOST_TAG}/bin"
	ANDROID_TOOLS_PATH_BIN="${ANDROID_NDK_ROOT}/prebuilt/${ANDROID_NDK_HOST_TAG}/bin:${ANDROID_CMAKE}/bin"
	if [ -n "${ANDROID_TOOLCHAIN_PATH_BIN}" ]
	then
		export PATH="${ANDROID_TOOLCHAIN_PATH_BIN}:${PATH}"
	fi
	if [ -n "${ANDROID_TOOLS_PATH_BIN}" ]
	then
		export PATH="${ANDROID_TOOLS_PATH_BIN}:${PATH}"
	fi
	local _cmake_toolchain_file=${ANDROID_NDK_ROOT}/build/cmake/android.toolchain.cmake
	case "${spec_target}" in
	aarch64-*)
		export CC=aarch64-linux-android21-clang
		export CXX=aarch64-linux-android21-clang++
		CMAKE_ADD_ARGS="-DCMAKE_TOOLCHAIN_FILE=${_cmake_toolchain_file} -DANDROID_ABI=arm64-v8a -DANDROID_STL=c++_static"
		;;
	arm*)
		export CC=armv7a-linux-androideabi21-clang
		export CXX=armv7a-linux-androideabi21-clang++
		CMAKE_ADD_ARGS="-DCMAKE_TOOLCHAIN_FILE=${_cmake_toolchain_file} -DANDROID_ABI=armeabi-v7a -DANDROID_ARM_MODE=thumb -DANDROID_ARM_NEON=ON -DANDROID_STL=c++_static"
		;;
	i686-*)
		export CC=i686-linux-android21-clang
		export CXX=i686-linux-android21-clang++
		CMAKE_ADD_ARGS="-DCMAKE_TOOLCHAIN_FILE=${_cmake_toolchain_file} -DANDROID_ABI=x86 -DANDROID_STL=c++_static"
		;;
	x86_64-*)
		export CC=x86_64-linux-android21-clang
		export CXX=x86_64-linux-android21-clang++
		CMAKE_ADD_ARGS="-DCMAKE_TOOLCHAIN_FILE=${_cmake_toolchain_file} -DANDROID_ABI=x86_64 -DANDROID_STL=c++_static"
		;;
	esac
	if _var_value_is_true "${ANDROID_PAGE_SIZE_16K}"
	then
		CMAKE_ADD_ARGS="${CMAKE_ADD_ARGS} -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
		add_ldflags="${add_ldflags} -Wl,-z,max-page-size=16384"
	fi
	case "${ANDROID_NDK_HOST_TAG}" in
	windows-*)
		case "$SHELL" in
		*bash|*sh)
			# set DOS style path for Git Bash SHELL for Android NDK make
			export SHELL=$(cygpath -d $SHELL)
			export SHELL_REWRITE_NEED="yes"
			;;
		esac
	esac

	CHOST=${spec_target}

	which ninja > /dev/null 2>&1 && have_ninja=yes
	if [ "x${have_ninja}" = "xyes" ]
	then
		CMAKE_GENERATOR="Ninja"
	fi

	if [ "x${CMAKE_IGNORE_PREFIX_PATH}" != "x" ]
	then
		CMAKE_ADD_ARGS="${CMAKE_ADD_ARGS} -DCMAKE_IGNORE_PREFIX_PATH=${CMAKE_IGNORE_PREFIX_PATH}"
	fi

	if [ -z "${CMAKE_GENERATOR}" ]
	then
		CMAKE_GENERATOR="Unix Makefiles"
	fi

	export PREFIX="${top_srcdir}/../../prefix/${spec_target}"
	export CFLAGS="-g0 -O2 -pipe ${make_conf_CFLAGS} ${add_cflags} -I${PREFIX}/include"
	export CXXFLAGS="-g0 -O2 -pipe ${make_conf_CXXFLAGS} ${add_cflags} -I${PREFIX}/include"
	export LDFLAGS="-L${PREFIX}/lib/ ${add_ldflags}"
	export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig/"
	export PATH="${PATH}:${PREFIX}/bin"
	export CHOST
	export CTARGET="${spec_target}"
	export ANDROID_NDK_ROOT
	CMAKE_ADD_ARGS="-DCMAKE_FIND_ROOT_PATH=${PREFIX} ${CMAKE_ADD_ARGS}"
	export CMAKE_ADD_ARGS
	export CMAKE_GENERATOR

	if [ ! -f "${spec_target}/.configured" ]
	then
		cd "${thirdparty_dir}/${SOURCESDIR}" || die "chdir to srcdir failed!"
		src_configure || die "configure failed!"
		cd "${pkg_datadir}" || die "chdir failed!"
		echo "1" > "${spec_target}/.configured"
		echo "configured OK."
	fi

	if [ ! -f "${spec_target}/.compiled" ]
	then
		cd "${thirdparty_dir}/${SOURCESDIR}" || die "chdir to srcdir failed!"
		src_compile || die "compile failed!"
		cd "${pkg_datadir}" || die "chdir failed!"
		echo "1" > "${spec_target}/.compiled"
		echo "compiled OK."
	fi

	if [ ! -f "${spec_target}/.installed" ]
	then
		cd "${thirdparty_dir}/${SOURCESDIR}" || die "chdir to srcdir failed!"
		src_install || die "install failed!"
		cd "${pkg_datadir}" || die "chdir failed!"
		echo "1" > "${spec_target}/.installed"
		echo "installed OK."
	fi

	# clean vars
	unset -v BUILDDIR
	unset -v CTARGET
	unset -v CHOST
	unset -v PKG_CONFIG_PATH
	unset -v LDFLAGS
	unset -v CXXFLAGS
	unset -v CFLAGS
	export PATH="${path_saved}"
	unset -v PREFIX

	unset -v PN
	unset -v PV
	unset -v REV
	unset -v SRCFILE
	unset -v IGNORE_TAR_ERRORS
	unset -v SHA512
	unset -v URL
	unset -v SOURCEDIR
	unset -v PATCHES

	cd "${pwd1}"
}

clean_package()
{
	local pkgname="${1}"
	local metafile="${repo_dir}/${pkgname}.meta.sh"

	source "${metafile}" || die "no such package!"

	local patchesdir="${repo_dir}/patches/${PN}"
	local pkg_datadir="${repo_tmpdir}/${pkgname}"

	local pwd1=`pwd`

	if [ -d "${pkg_datadir}" ]
	then
		cd "${pkg_datadir}" || die "chdir failed!"
		rm -f .prepared .unpacked
		rm -rf "${spec_target}"
		cd "${pwd1}"
	fi

	rm -rf "${thirdparty_dir}/${pkgname}-build-${spec_target}"
	rm -rf "${thirdparty_dir}/${SOURCESDIR}"

	unset -v PN
	unset -v PV
	unset -v REV
	unset -v SRCFILE
	unset -v SHA512
	unset -v URL
	unset -v SOURCEDIR
	unset -v PATCHES
}

# check current directory
if [ ! -f ./build.sh ]
then
	die "Error! You must call this script only in top_srcdir!"
fi

if [ ! -d "${thirdparty_dir}" ]
then
	mkdir "${thirdparty_dir}" || die "Failed to create thirdparty_dir!"
fi

if [ ! -d "${repo_tmpdir}" ]
then
	mkdir "${repo_tmpdir}" || die "Failed to create repo tmpdir!"
fi

if [ -f "./make.conf" ]
then
	source ./make.conf
	export make_conf_CFLAGS=${CFLAGS}
	export make_conf_CXXFLAGS=${CXXFLAGS}
fi

if [ -z "${ANDROID_NDK_HOST_TAG}" ]
then
	ANDROID_NDK_HOST_TAG=linux-x86_64
fi

export -f die
export -f domake
export -f domake_install

for pkg in ${packages}
do
	if [ "x${clean_mode}" = "xyes" ]
	then
		clean_package ${pkg}
	else
		deploy_package ${pkg}
	fi
done

if [ "x${clean_mode}" != "xyes" -a "x${packages}" != "x" ]
then
	strip_files "${top_srcdir}/../../prefix/${spec_target}" || die "strip failed!"
fi
