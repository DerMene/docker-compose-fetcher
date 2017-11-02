import groovy.json.JsonSlurper

import java.security.MessageDigest

class Fetcher {

    static String digest(URL url) {
        def inputStream = url.openStream()

        MessageDigest.getInstance("SHA-256").digest(inputStream.bytes).encodeHex().toString()
    }

    public static void main(String... args) {

        JsonSlurper slurper = new JsonSlurper()
        def releases = slurper.parse(new URL("https://api.github.com/repos/docker/compose/releases")) as List
        def latest = releases[0].name

        println("docker_compose_latest: $latest")
        println("docker_compose_checksums:")
        for (String os in ['Linux', 'Darwin', 'Windows']) {

            Object hashes = getHashes(releases, os)

            println("  \"$os\":")
            println("    \"latest\": \"sha256:${hashes[latest]}\"")
            hashes.each { version, sha ->
                println("    \"$version\": \"$sha\"")
            }
        }
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
            // println("calculate sha256 of: $url")
            def sha = digest(new URL(url))
            [(version): "sha256:$sha"]
        }
        hashes
    }
}
