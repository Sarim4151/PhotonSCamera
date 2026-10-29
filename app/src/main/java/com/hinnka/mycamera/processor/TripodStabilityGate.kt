package com.hinnka.mycamera.processor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.delay

/** Gyro qualification is device motion only; it cannot detect subject motion or sky tracking. */
object TripodStabilityGate {
    suspend fun qualify(context: Context): Boolean {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return false
        val lock = Any()
        var samples = 0
        var sumSquares = 0.0
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                synchronized(lock) {
                    sumSquares += event.values.take(3).sumOf { it.toDouble() * it }
                    samples++
                }
            }
        }
        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)) return false
        try {
            delay(1000)
            return synchronized(lock) { samples >= 10 && kotlin.math.sqrt(sumSquares / samples) < .035 }
        } finally { manager.unregisterListener(listener) }
    }
}
