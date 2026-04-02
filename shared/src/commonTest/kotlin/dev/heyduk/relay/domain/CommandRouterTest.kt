package dev.heyduk.relay.domain

import dev.heyduk.relay.domain.CommandRouter.CommandResult
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandRouterTest {

    @Test
    fun lsWithoutSessionIsGlobal() {
        assertEquals(CommandResult.Global("/ls"), CommandRouter.route("/ls", null))
    }

    @Test
    fun lsWithSessionIsStillGlobal() {
        assertEquals(CommandResult.Global("/ls"), CommandRouter.route("/ls", "abc"))
    }

    @Test
    fun helpWithoutSessionIsGlobal() {
        assertEquals(CommandResult.Global("/help"), CommandRouter.route("/help", null))
    }

    @Test
    fun lastWithSessionIsSessionTargeted() {
        assertEquals(
            CommandResult.SessionTargeted("/last @abc"),
            CommandRouter.route("/last", "abc")
        )
    }

    @Test
    fun openWithSessionIsSessionTargeted() {
        assertEquals(
            CommandResult.SessionTargeted("/open @abc"),
            CommandRouter.route("/open", "abc")
        )
    }

    @Test
    fun gotoWithSessionIsSessionTargeted() {
        assertEquals(
            CommandResult.SessionTargeted("/goto @abc"),
            CommandRouter.route("/goto", "abc")
        )
    }

    @Test
    fun renameWithArgsAndSessionIsSessionTargeted() {
        assertEquals(
            CommandResult.SessionTargeted("/rename newname @abc"),
            CommandRouter.route("/rename newname", "abc")
        )
    }

    @Test
    fun plainTextWithSessionIsMessage() {
        assertEquals(
            CommandResult.Message("abc", "deploy the thing"),
            CommandRouter.route("deploy the thing", "abc")
        )
    }

    @Test
    fun lastWithoutSessionIsNoSessionSelected() {
        assertEquals(CommandResult.NoSessionSelected, CommandRouter.route("/last", null))
    }

    @Test
    fun plainTextWithoutSessionIsNoSessionSelected() {
        assertEquals(CommandResult.NoSessionSelected, CommandRouter.route("hello", null))
    }
}
