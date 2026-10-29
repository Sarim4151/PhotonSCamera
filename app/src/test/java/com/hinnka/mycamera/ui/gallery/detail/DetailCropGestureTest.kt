package com.hinnka.mycamera.ui.gallery.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailCropGestureTest {
    @Test fun dragMovesPhotoAndCropSliderPositionTogether() {
        // A 100 px drag across a 400 px viewport moves one quarter of a 768 px crop.
        assertEquals(.440594f, moveDetailCrop(.5f, 100f, 400, 4000, 768, 1f), .00001f)
    }

    @Test fun pinchMagnificationSlowsSourceMovementAndClampsEdges() {
        assertEquals(.470297f, moveDetailCrop(.5f, 100f, 400, 4000, 768, 2f), .00001f)
        assertEquals(0f, moveDetailCrop(.02f, 500f, 400, 4000, 768, 1f))
        assertEquals(.5f, moveDetailCrop(.5f, 100f, 400, 768, 768, 1f))
    }
}
