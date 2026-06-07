
# Metadata & build instructions for deploy script

PN="freetype"
PV="2.14.1"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.xz"
SHA512="28284da99be52e90c7883fd668131cd44227ce68b98a57767fc33b2fe73e4baa5425cba4144bf3094192946d2abec03cec7afefe6300c0cda6787fae91966bad"
URL="https://download.savannah.gnu.org/releases/${PN}/${SRCFILE}"

SOURCESDIR="${PN}-${PV}"

PATCHES=

#src_configure() {
#	mkdir -p docs/markdown
#	cd "${BUILDDIR}" || die "chdir failed!"
#	local add_args=
#	case "${CTARGET}" in
#	*-apple-macos*)
#		add_args="${add_args} --with-fsspec --with-fsref --with-quickdraw-toolbox --with-quickdraw-carbon --with-ats"
#		;;
#	esac
#	if [ -n "${CHOST}" ]
#	then
#		add_args="${add_args} --host=${CHOST} --target=${CHOST}"
#	fi
#	../${SOURCESDIR}/configure --prefix=${PREFIX} --enable-static --disable-shared \
#		--with-zlib=yes --with-bzip2=no --with-png=yes --with-harfbuzz=no --with-brotli=no \
#		${add_args}
#}

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	cmake -G"${CMAKE_GENERATOR}" \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX="${PREFIX}" \
		-DBUILD_SHARED_LIBS=OFF \
		-DFT_REQUIRE_ZLIB=ON \
		-DFT_DISABLE_BZIP2=ON \
		-DFT_REQUIRE_PNG=ON \
		-DFT_DISABLE_HARFBUZZ=ON \
		-DFT_DISABLE_BROTLI=ON \
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
