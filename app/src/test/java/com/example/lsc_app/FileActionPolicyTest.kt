package com.example.lsc_app

import org.junit.Assert.*
import org.junit.Test

class FileActionPolicyTest {
    @Test fun safeNamesCannotEscapeDestinationOrSpoofPaths() {
        assertEquals("report.pdf", FileActionPolicy.safeFilename("../../report.pdf"))
        assertEquals("photo.jpg", FileActionPolicy.safeFilename("C:\\private\\photo.jpg"))
        assertEquals("download", FileActionPolicy.safeFilename(".."))
        assertEquals("ab_cd.txt", FileActionPolicy.safeFilename("ab\u202Ecd.txt"))
        assertEquals("bad_name_.txt", FileActionPolicy.safeFilename("bad:name?.txt"))
        assertTrue(FileActionPolicy.safeFilename("a".repeat(300)).length <= 120)
    }

    @Test fun malformedMimeRestrictionsDoNotBecomeWildcardAccess() {
        assertTrue(FileActionPolicy.acceptTypes(arrayOf("not-a-mime")).isEmpty())
        assertTrue(FileActionPolicy.acceptTypes(arrayOf("text/html\r\nX-Foo: bad")).isEmpty())
        assertEquals(listOf("*/*"), FileActionPolicy.acceptTypes(emptyArray()))
        assertEquals(listOf("image/jpeg"), FileActionPolicy.acceptTypes(arrayOf("IMAGE/JPEG")))
    }

    @Test fun uploadPolicyHonorsMimeAndSelectionBounds() {
        assertEquals(listOf("image/*"), FileActionPolicy.acceptTypes(arrayOf("image/*")))
        assertEquals(listOf("*/*"), FileActionPolicy.acceptTypes(arrayOf("")))
        assertEquals(listOf("application/pdf", "image/png"),
            FileActionPolicy.acceptTypes(arrayOf("application/pdf,image/png")))
        assertTrue(FileActionPolicy.mimeAllowed("image/jpeg", listOf("image/*")))
        assertFalse(FileActionPolicy.mimeAllowed("application/pdf", listOf("image/*")))
        assertFalse(FileActionPolicy.mimeAllowed(null, listOf("image/*")))
        assertTrue(FileActionPolicy.selectionAllowed(1, false))
        assertFalse(FileActionPolicy.selectionAllowed(2, false))
        assertTrue(FileActionPolicy.selectionAllowed(10, true))
        assertFalse(FileActionPolicy.selectionAllowed(11, true))
        assertFalse(FileActionPolicy.selectionAllowed(0, true))
    }

    @Test fun downloadsRefuseRedirectsHtmlAndOversizeResponses() {
        assertTrue(FileActionPolicy.downloadAllowed(200, "application/pdf", 100))
        assertTrue(FileActionPolicy.downloadAllowed(200, null, -1))
        listOf(301,302,303,307,308,401,403,404,206).forEach {
            assertFalse(FileActionPolicy.downloadAllowed(it, "application/pdf", 100))
        }
        assertFalse(FileActionPolicy.downloadAllowed(200, "text/html; charset=UTF-8", 100))
        assertFalse(FileActionPolicy.downloadAllowed(200, "application/xhtml+xml", 100))
        assertFalse(FileActionPolicy.downloadAllowed(200, "application/pdf", 104857601))
    }

    @Test fun boundedCopyStopsWhenOwnerCancels() {
        val input = "abc".byteInputStream()
        assertThrows(java.io.InterruptedIOException::class.java) {
            FileActionPolicy.copyBounded(input, java.io.ByteArrayOutputStream(), 100) { true }
        }
        assertEquals(3, input.available())
    }

    @Test fun boundedCopyRefusesUnknownLengthOverflowAndHtmlSniff() {
        val out = java.io.ByteArrayOutputStream()
        assertEquals(3L, FileActionPolicy.copyBounded("abc".byteInputStream(), out, 3))
        assertEquals("abc", out.toString())
        assertThrows(java.io.IOException::class.java) {
            FileActionPolicy.copyBounded("abcd".byteInputStream(), java.io.ByteArrayOutputStream(), 3)
        }
        assertTrue(FileActionPolicy.looksLikeHtml(" \n<!DOCTYPE html><html>Login".toByteArray()))
        assertTrue(FileActionPolicy.looksLikeHtml("<html><body>Login".toByteArray()))
        assertFalse(FileActionPolicy.looksLikeHtml("%PDF-1.7".toByteArray()))
    }

    @Test fun onlyExactHttpsBackendCanPerformFileActions() {
        assertTrue(FileActionPolicy.isTrusted("https://lsc.up.railway.app/files/a?x=1"))
        assertTrue(FileActionPolicy.isTrusted("https://LSC.up.railway.app:443/a"))
        listOf("http://lsc.up.railway.app/a", "https://lsc.up.railway.app:444/a",
            "https://user@lsc.up.railway.app/a", "https://lsc.up.railway.app.evil/a",
            "https://evil/a", "file:///a", "https://lsc.up.railway.app\\@evil/a",
            "https://lsc.up.railway.app/a#fragment", "not a url").forEach {
            assertFalse(it, FileActionPolicy.isTrusted(it))
        }
    }
}
