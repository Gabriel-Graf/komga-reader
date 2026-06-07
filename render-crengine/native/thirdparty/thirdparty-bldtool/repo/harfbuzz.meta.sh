
# Metadata & build instructions for deploy script

PN="harfbuzz"
PV="12.1.0"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.xz"
SHA512="94cbc3fe8fad30f4f7871bdddc8b129c486ab55329f9b48c6336fdf15d05f09c3c96cac51f68a0218db113b4783c07ce5d6bb455ccc875b31fd2261e3e8dc559"

URL="https://github.com/harfbuzz/harfbuzz/releases/download/${PV}/${SRCFILE}"

SOURCESDIR="${PN}-${PV}"

PATCHES=

#src_configure() {
#	cd "${BUILDDIR}" || die "chdir failed!"
#	local add_args=
#	case "${CTARGET}" in
#	*-apple-macos*)
#		add_args="${add_args} --with-gdi=no --with-directwrite=no --with-coretext=yes"
#		export LIBS="-framework Cocoa"
#		;;
#	*-mingw32)
#		add_args="${add_args} --with-gdi=yes --with-directwrite=yes"
#		export LIBS="-ldwrite"
#		;;
#	esac
#	if [ -n "${CHOST}" ]
#	then
#		add_args="${add_args} --host=${CHOST} --target=${CHOST}"
#	fi
#	../${SOURCESDIR}/configure --prefix=${PREFIX} --enable-static --disable-shared \
#		--with-glib=no --with-gobject=no --with-cairo=no --with-chafa=no --with-icu=no \
#		--with-graphite2=no --with-freetype=yes --with-uniscribe=no \
#		${add_args}
#}

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	local local_add_args=
	case "${CTARGET}" in
	*-apple-macos*)
		local_add_args="-DHB_HAVE_CORETEXT=ON"
		;;
	*-mingw32)
		local_add_args="-DHB_HAVE_GDI=ON -DHB_HAVE_DIRECTWRITE=ON"
		;;
	esac
	cmake -G"${CMAKE_GENERATOR}" \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX="${PREFIX}" \
		-DBUILD_SHARED_LIBS=OFF \
		-DHB_HAVE_FREETYPE=ON \
		-DHB_HAVE_GRAPHITE2=OFF \
		-DHB_HAVE_GLIB=OFF \
		-DHB_HAVE_GOBJECT=OFF \
		-DHB_HAVE_ICU=OFF \
		-DHB_HAVE_UNISCRIBE=OFF \
		-DHB_BUILD_UTILS=OFF \
		-DHB_BUILD_SUBSET=OFF \
		${local_add_args} \
		${CMAKE_ADD_ARGS} \
		-DCMAKE_C_FLAGS="${CFLAGS}" \
		-DCMAKE_CXX_FLAGS="${CXXFLAGS}" \
		../${SOURCESDIR}
}

src_compile() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake || die "make failed!"
}

src_install() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake_install || die "make install failed!"
}
