package com.hinnka.mycamera.photosphere

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Inserts a standard XMP APP1 packet immediately after SOI, streaming the JPEG unchanged. */
internal object SphereXmp {
    fun write(source: File, target: File, width: Int, height: Int, checkCancelled: () -> Unit) {
        require(width == height * 2)
        val xml = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description rdf:about="" xmlns:GPano="http://ns.google.com/photos/1.0/panorama/" GPano:ProjectionType="equirectangular" GPano:UsePanoramaViewer="True" GPano:FullPanoWidthPixels="$width" GPano:FullPanoHeightPixels="$height" GPano:CroppedAreaImageWidthPixels="$width" GPano:CroppedAreaImageHeightPixels="$height" GPano:CroppedAreaLeftPixels="0" GPano:CroppedAreaTopPixels="0"/></rdf:RDF></x:xmpmeta>"""
        val packet = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII) + xml.toByteArray(Charsets.UTF_8)
        val length = packet.size + 2
        require(length <= 65535)
        check(target.createNewFile()) { "Refusing to overwrite sphere" }
        source.inputStream().buffered().use { input ->
            if (input.read() != 0xff || input.read() != 0xd8) throw IOException("Invalid source JPEG")
            FileOutputStream(target).use { output ->
                output.write(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe1.toByte(), (length shr 8).toByte(), length.toByte()))
                output.write(packet)
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.flush(); output.fd.sync()
            }
        }
    }
}
