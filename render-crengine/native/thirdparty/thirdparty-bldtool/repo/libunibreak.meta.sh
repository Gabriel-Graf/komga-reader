
# Metadata & build instructions for deploy script

PN="libunibreak"
PV="6.1"
# package revision: when patchset is changed (but not version), increase it
# when version changed, reset to "1".
REV="1"
SRCFILE="${PN}-${PV}.tar.gz"
SHA512="8ffde29a9b90ddcbfabb61d7302ffe3b17473cd6d30fe1a4403d857e6191291d7e7a6f23bde58654155ed95f4a0f31e082cdf424a82da46722a811291ef38c2f"

URL="https://github.com/adah1972/libunibreak/releases/download/libunibreak_${PV/./_}/${SRCFILE}"

SOURCESDIR="${PN}-${PV}"

PATCHES=

src_configure() {
	cd "${BUILDDIR}" || die "chdir failed!"
	if [ -n "${CHOST}" ]
	then
		add_args="${add_args} --host=${CHOST}"
	fi
	../${SOURCESDIR}/configure --prefix=${PREFIX} --enable-static --disable-shared \
		${add_args}
}

src_compile() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake || die "make failed!"
}

src_install() {
	cd "${BUILDDIR}" || die "chdir failed!"
	domake_install || die "make install failed!"
}
