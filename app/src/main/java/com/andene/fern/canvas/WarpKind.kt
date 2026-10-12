package com.andene.fern.canvas

/** The ways a selection or raster layer can be warped (roadmap items 84, 92, 122). */
enum class WarpKind {
    /** A 4 x 4 Bézier lattice, dragged by its control points ([meshWarp]). */
    MESH,

    /** Four corner pins with a perspective map between them ([perspectiveDistort]). */
    DISTORT,

    /** Pins stuck in where tapped, the rest following as rigidly as it can ([puppetWarp]). */
    PUPPET,
}
