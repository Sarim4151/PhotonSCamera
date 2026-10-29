package com.hinnka.mycamera.video

/** A reservation covers preparation, recording, finalization and publication. */
internal class RecordingSessionGate {
    class Ticket internal constructor() { internal var finishing = false }
    private var active: Ticket? = null

    @Synchronized fun reserve(): Ticket? {
        if (active != null) return null
        return Ticket().also { active = it }
    }

    @Synchronized fun beginFinish(ticket: Ticket): Boolean {
        if (active !== ticket || ticket.finishing) return false
        ticket.finishing = true
        return true
    }

    @Synchronized fun complete(ticket: Ticket) {
        if (active === ticket) active = null
    }
}
