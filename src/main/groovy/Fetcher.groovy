import groovy.json.JsonSlurper
import groovy.util.logging.Log

import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class Fetcher {


    void fetch(String path) {
        StringBuffer stringBuffer = new StringBuffer()

        JsonSlurper slurper = new JsonSlurper()
        def releases = slurper.parse(new URL("https://api.github.com/repos/docker/compose/releases")) as List
        def latest = releases[0].name

        stringBuffer.append("docker_compose_latest: $latest\n")
        stringBuffer.append("docker_compose_checksums:\n")
        for (String os in ['Linux', 'Darwin', 'Windows']) {

            Object hashes = getHashes(releases, os)

            stringBuffer.append("  \"$os\":\n")
            stringBuffer.append("    \"latest\": \"sha256:${hashes[latest]}\"\n")
            hashes.each { version, sha ->
                stringBuffer.append("    \"$version\": \"sha256:$sha\"\n")
            }
        }

        Paths.get(path).setText(stringBuffer.toString())
    }

    private static Object getHashes(List releases, String os) {
        def downloads = releases.collectEntries { release ->
            def url = release.assets
                    .findAll { it.name.contains(os) }
                    .find { it.content_type in ['application/octet-stream', 'application/x-ms-dos-executable'] }
                    ?.browser_download_url
            [(release.name): url]
        }.findAll {k, v -> v}


        def hashes = downloads.collectEntries { version, String url ->
            def sha = digest(new URL(url))
            [(version): "$sha"]
        }
        hashes
    }

    private static String digest(URL url) {
        def inputStream = url.openStream()

        MessageDigest.getInstance("SHA-256").digest(inputStream.bytes).encodeHex().toString()
    }
}
