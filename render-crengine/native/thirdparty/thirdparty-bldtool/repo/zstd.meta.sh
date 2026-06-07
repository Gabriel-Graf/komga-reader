
# Metadata & build instructions for deploy script

PN="zstd"
PV="1.5.7"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="2"
SRCFILE="${PN}-${PV}.tar.gz"
SHA512="b4de208f179b68d4c6454139ca60d66ed3ef3893a560d6159a056640f83d3ee67cdf6ffb88971cdba35449dba4b597eaa8b4ae908127ef7fd58c89f40bf9a705"

URL="https://github.com/facebook/zstd/releases/download/v${PV}/${SRCFILE}"
IGNORE_TAR_ERRORS=y

SOURCESDIR="${PN}-${PV}"

PATCHES=

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	cmake -G"${CMAKE_GENERATOR}" \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX="${PREFIX}" \
		-DZSTD_BUILD_SHARED=OFF \
		-DZSTD_BUILD_STATIC=ON \
		-DZSTD_LEGACY_SUPPORT=OFF \
		-DZSTD_MULTITHREAD_SUPPORT=OFF \
		-DZSTD_BUILD_PROGRAMS=OFF \
		-DZSTD_BUILD_CONTRIB=OFF \
		${CMAKE_ADD_ARGS} \
		-DCMAKE_C_FLAGS="${CFLAGS}" \
		-DCMAKE_CXX_FLAGS="${CXXFLAGS}" \
		../${SOURCESDIR}/build/cmake
}

src_compile() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake || die "make failed!"
}

src_install() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake_install || die "make install failed!"
}
