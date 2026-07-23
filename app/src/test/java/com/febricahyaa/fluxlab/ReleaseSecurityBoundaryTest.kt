package com.febricahyaa.fluxlab

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSecurityBoundaryTest {
    @Test
    fun releaseWorkflowUsesProductionAndSanitizedSecretHandling() {
        val workflow = repositoryFile(".github/workflows/release.yml").readText()

        assertTrue(workflow.contains("environment: production"))
        assertTrue(workflow.contains("contents: read"))
        assertTrue(workflow.contains("id-token: write"))
        assertTrue(workflow.contains("attestations: write"))
        assertFalse(workflow.contains("contents: write"))
        assertFalse(workflow.contains("set -x"))
        assertFalse(Regex("echo\\s+.*FLUXLAB_RELEASE_(STORE_PASSWORD|KEY_PASSWORD|KEYSTORE_B64)").containsMatchIn(workflow))
        assertTrue(workflow.contains("RUNNER_TEMP/fluxlab-release.keystore"))
        assertTrue(workflow.contains("chmod 600"))
        assertTrue(workflow.contains("if: always()"))
        assertTrue(workflow.contains("apksigner\" verify --verbose --print-certs --Werr"))
        assertTrue(workflow.contains("actions/attest-build-provenance@v2"))
        assertTrue(workflow.contains("apkSha256"))
        assertTrue(workflow.contains("certificateSha256Fingerprint"))
    }

    @Test
    fun signingFilesAreIgnoredWithoutHidingPublicPemCertificates() {
        val gitignore = repositoryFile(".gitignore").readText()

        listOf("*.jks", "*.keystore", "*.p12", "*.pfx", "*.pk8", "*.key", "*.idsig", "keystore.properties", "signing.properties", "local.properties").forEach {
            assertTrue("missing ignore rule: $it", gitignore.lines().contains(it))
        }
        assertFalse(gitignore.lines().contains("*.pem"))
    }

    private fun repositoryFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("Working directory unavailable")).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: error("Repository root not found for $relativePath")
        }
    }
}
