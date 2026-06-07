
# Metadata & build instructions for deploy script

PN="zlib"
PV="1.3.1"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.xz"
SHA512="1e8e70b362d64a233591906a1f50b59001db04ca14aaffad522198b04680be501736e7d536b4191e2f99767e7001ca486cd802362cca2be05d5d409b83ea732d"

# Upstream www.zlib.net only keeps the latest release in its root; 1.3.1 has
# been rotated out (404). The GitHub release asset is byte-identical (verified
# via the SHA512 below), so we pull from there for a stable, reproducible URL.
URL="https://github.com/madler/zlib/releases/download/v${PV}/${SRCFILE}"

SOURCESDIR="${PN}-${PV}"

PATCHES=

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	# Option BUILD_SHARED_LIBS ignored
	# Options ASM686, AMD64 broken (corresponding asm have been files removed)
	cmake -G"${CMAKE_GENERATOR}" \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX="${PREFIX}" \
		-DBUILD_SHARED_LIBS=OFF \
		${CMAKE_ADD_ARGS} \
		-DCMAKE_C_FLAGS="${CFLAGS}" \
		-DCMAKE_CXX_FLAGS="${CXXFLAGS}" \
		../${SOURCESDIR}
}

src_compile() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake ${MAKEOPTS} || die "make failed!"
}

src_install() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake_install || die "make install failed!"
}
