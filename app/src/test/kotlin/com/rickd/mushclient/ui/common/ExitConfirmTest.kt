package com.rickd.mushclient.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitConfirmTest {
    @Test fun noQuestionWhenNothingIsConnected() = assertNull(exitConfirmMessage(0))
    @Test fun singularForOneConnection() = assertEquals("1 connection is open and will be closed.", exitConfirmMessage(1))
    @Test fun pluralForSeveral() = assertEquals("3 connections are open and will be closed.", exitConfirmMessage(3))
}
