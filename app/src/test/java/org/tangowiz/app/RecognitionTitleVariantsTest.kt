package org.tangowiz.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionTitleVariantsTest {
    @Test fun displaysOneTitleForEquivalentCandidateLabels() {
        val titles = listOf("El tango, del barrio", "Tango del barrio", "Tango de barrio", "Tango de el barrio")
        val candidate = Candidate(
            "recording", "Orquesta", titles.first(),
            metadataAlternatives = mapOf("title" to titles)
        )

        assertEquals(titles.first(), candidate.display("title"))
        assertEquals(titles.first(), candidate.watchTitle())
        assertEquals(titles, candidate.labels("title"))
    }

    @Test fun scoreGapUsesTheStrongestDifferentTitle() {
        val best = Candidate("a", "First orchestra", "El tango, del barrio", score = .91)
        val sameSong = Candidate("b", "Second orchestra", "Tango de barrio", score = .90)
        val rival = Candidate("c", "Third orchestra", "Otro tango", score = .80)

        val runner = Candidate.distinctRunner(best, listOf(best, sameSong, rival))
        assertEquals(rival, runner)
    }

    @Test fun scoreGapTreatsDecoratedVersionsAsTheSameWork() {
        val best = Candidate("original", "Los Herederos del Compás", "Bella Ciao Milonga (original version)", score = .91)
        val otherVersion = Candidate("percussion", "Los Herederos del Compás", "Bella Ciao [Percussion Version]", score = .90)
        val plainTitle = Candidate("plain", "Los Herederos del Compás", "Bella Ciao Milonga", score = .89)
        val rival = Candidate("rival", "Los Herederos del Compás", "Otra milonga", score = .80)

        assertEquals(rival, Candidate.distinctRunner(best, listOf(best, otherVersion, plainTitle, rival)))
        assertEquals(false, best.sameRecording(otherVersion))
    }

    @Test fun missingOrAmbiguousTitlesStillCompete() {
        val best = Candidate("a", "First orchestra", "El tango del barrio", score = .91)
        val blank = Candidate("b", "Second orchestra", "", score = .90)
        val ambiguous = Candidate("c", "Third orchestra", "Tango de barrio", score = .89,
            metadataAlternatives = mapOf("title" to listOf("Otro tango")))

        assertEquals(blank, Candidate.distinctRunner(best, listOf(best, blank)))
        assertEquals(ambiguous, Candidate.distinctRunner(best, listOf(best, ambiguous)))
    }
}
