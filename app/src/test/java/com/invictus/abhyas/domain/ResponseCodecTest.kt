package com.invictus.abhyas.domain

import com.invictus.abhyas.domain.engine.ResponseCodec
import com.invictus.abhyas.domain.model.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResponseCodecTest {
    @Test fun roundTrips() {
        listOf(
            Response.Choice(setOf("b", "a")),
            Response.Number(-2.5),
            Response.Bool(false),
            Response.Text("x y"),
        ).forEach { assertEquals(it, ResponseCodec.decode(ResponseCodec.encode(it))) }
    }

    @Test fun badInputIsNull() {
        assertNull(ResponseCodec.decode(null))
        assertNull(ResponseCodec.decode("{oops"))
        assertNull(ResponseCodec.decode("""{"k":"zzz"}"""))
    }
}
