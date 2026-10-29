package com.hinnka.mycamera.expression

import org.junit.Assert.*
import org.junit.Test

class ExpressionWarpTest {
    private val face=ExpressionWarp.Face(ExpressionWarp.Point(50f,90f),ExpressionWarp.Point(150f,90f),
        ExpressionWarp.Point(100f,98f),ExpressionWarp.Point(100f,102f))
    @Test fun zeroStrengthIsIdentity() {
        for(y in 0..200) for(x in 0..200) {
            val d=ExpressionWarp.displacement(x.toFloat(),y.toFloat(),face,0f,false)
            assertEquals(0f,d.x,0f); assertEquals(0f,d.y,0f)
        }
    }
    @Test fun outsideMouthSupportIsUnchanged() {
        for(p in listOf(0f to 0f,100f to 30f,100f to 100f,200f to 190f)) {
            val d=ExpressionWarp.displacement(p.first,p.second,face,1f,false)
            assertEquals(0f,d.x,0f);assertEquals(0f,d.y,0f)
        }
    }
    @Test fun smileLiftsAndRelaxLowersExistingCorners() {
        val smile=ExpressionWarp.displacement(50f,90f,face,1f,false)
        val relax=ExpressionWarp.displacement(50f,90f,face,1f,true)
        assertTrue(smile.y<0f);assertTrue(relax.y>0f)
        assertTrue(kotlin.math.abs(smile.y)<=6.01f)
    }
    @Test fun relaxingUnliftedMouthDoesNotInventFrown() {
        val flat=face.copy(left=ExpressionWarp.Point(50f,100f),right=ExpressionWarp.Point(150f,100f))
        assertEquals(0f,ExpressionWarp.displacement(50f,100f,flat,1f,true).y,0f)
    }
}
